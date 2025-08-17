package com.sourav.Gateway.service;

import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;

@Service
public class JwtValidator {
    private static final Logger log = LoggerFactory.getLogger(JwtValidator.class);
    
    private SecretKey secretKey;
    private final UltimateSecurityAuthClient authClient;
    private final Cache<String, Boolean> localCache;
    private final RedisTemplate<String, Boolean> redisTemplate;
    private final MeterRegistry meterRegistry;

    public JwtValidator(
        @Value("${jwt.secret}") String secret,
        UltimateSecurityAuthClient authClient,
        CacheManager cacheManager,
        RedisTemplate<String, Boolean> redisTemplate,
        MeterRegistry meterRegistry) {
        
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.authClient = authClient;
        this.localCache = (Cache<String, Boolean>) cacheManager.getCache("jwt-cache");
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    public Mono<Boolean> validateToken(String jwt) {
        // 1. Check local cache
        Boolean cached = localCache.getIfPresent(jwt);
        if (cached != null) {
            meterRegistry.counter("jwt.validation", "type", "local_cache").increment();
            return Mono.just(cached);
        }

        // 2. Check Redis
        return checkRedisCache(jwt)
            .flatMap(redisValid -> {
                if (redisValid != null) {
                    meterRegistry.counter("jwt.validation", "type", "redis_cache").increment();
                    localCache.put(jwt, redisValid);
                    return Mono.just(redisValid);
                }
                // 3. Local JWT validation
                if (!validateLocally(jwt)) {
                    return Mono.just(false);
                }
                // 4. Remote validation
                return validateRemotely(jwt);
            });
    }

    private Mono<Boolean> checkRedisCache(String jwt) {
        return Mono.fromCallable(() -> redisTemplate.opsForValue().get("jwt:" + jwt))
            .onErrorResume(e -> {
                log.warn("Redis cache check failed", e);
                return Mono.empty();
            });
    }

    private boolean validateLocally(String jwt) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(jwt);
            return true;
        } catch (Exception e) {
            log.debug("Local JWT validation failed", e);
            return false;
        }
    }

    private Mono<Boolean> validateRemotely(String jwt) {
        return authClient.validateToken(jwt)
            .doOnNext(valid -> {
                if (valid) {
                    cacheToken(jwt);
                }
            })
            .onErrorResume(e -> {
                log.error("Remote validation failed", e);
                return Mono.just(validateLocally(jwt)); // Fallback
            });
    }

    private void cacheToken(String jwt) {
        localCache.put(jwt, true);
        redisTemplate.opsForValue().set("jwt:" + jwt, true, 1, TimeUnit.MINUTES);
    }

    @Scheduled(fixedRate = 3600000)
    public void refreshSecretKey() {
        authClient.fetchNewSecretKey()
            .subscribe(newKey -> {
                this.secretKey = Keys.hmacShaKeyFor(newKey.getBytes());
                log.info("JWT secret key rotated");
            });
    }
}