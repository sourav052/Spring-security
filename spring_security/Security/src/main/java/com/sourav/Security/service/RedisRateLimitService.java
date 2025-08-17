//package com.sourav.Security.service;
//
//import io.github.resilience4j.circuitbreaker.CircuitBreaker;
//import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
//import io.github.resilience4j.ratelimiter.RateLimiter;
//import io.github.resilience4j.ratelimiter.RateLimiterConfig;
//import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
//import inet.ipaddr.IPAddressString;
//import io.micrometer.core.instrument.MeterRegistry;
//import jakarta.servlet.http.HttpServletRequest;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.data.redis.core.script.DefaultRedisScript;
//import org.springframework.data.redis.core.script.RedisScript;
//import org.springframework.stereotype.Service;
//
//import java.time.Duration;
//import java.util.Arrays;
//import java.util.Collections;
//import java.util.List;
//
//@Service
//public class RedisRateLimitService {
//    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitService.class);
//    private static final String RATE_LIMIT_SCRIPT =
//        "local current = redis.call('incr', KEYS[1])\n" +
//        "if current == 1 then\n" +
//        "    redis.call('expire', KEYS[1], ARGV[1])\n" +
//        "end\n" +
//        "return current <= tonumber(ARGV[2])";
//
//    private final RedisTemplate<String, Object> redisTemplate;
//    private final List<String> trustedProxies;
//    private final RateLimiterRegistry registry;
//    private final RedisScript<Boolean> rateLimitScript;
//    private final CircuitBreaker redisCircuitBreaker;
//    private final MeterRegistry meterRegistry;
//
//    @Value("${app.rate-limiting.ip.global-limit}") 
//    private volatile int ipGlobalLimit;
//    
//    @Value("${app.rate-limiting.ip.auth-limit}") 
//    private volatile int ipAuthLimit;
//    
//    @Value("${app.rate-limiting.user.default-limit}") 
//    private volatile int userDefaultLimit;
//    
//    @Value("${app.rate-limiting.user.premium-limit}") 
//    private volatile int userPremiumLimit;
//
//    @Autowired
//    public RedisRateLimitService(RedisTemplate<String, Object> redisTemplate,
//                               @Value("${app.rate-limiting.trusted-proxies}") List<String> trustedProxies,
//                               RateLimiterRegistry registry,
//                               MeterRegistry meterRegistry) {
//        this.redisTemplate = redisTemplate;
//        this.trustedProxies = trustedProxies;
//        this.registry = registry;
//        this.meterRegistry = meterRegistry;
//        this.rateLimitScript = new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, Boolean.class);
//        this.redisCircuitBreaker = CircuitBreaker.of("redis-cb", 
//            CircuitBreakerConfig.custom()
//                .failureRateThreshold(50)
//                .waitDurationInOpenState(Duration.ofSeconds(30))
//                .slidingWindowSize(10)
//                .build());
//        
//        validateConfigs();
//    }
//
//    public boolean tryConsume(String key, RateLimitType type) {
//        try {
//            return redisCircuitBreaker.executeSupplier(() -> {
//                Boolean result = redisTemplate.execute(
//                    rateLimitScript,
//                    Collections.singletonList("rate_limit:" + key),
//                    String.valueOf(getTimeWindow(type)),
//                    String.valueOf(getLimit(type))
//                );
//                
//                if (result == null) {
//                    meterRegistry.counter("rate_limit.redis_fallback").increment();
//                    throw new RuntimeException("Null response from Redis");
//                }
//                
//                if (!result) {
//                    log.debug("Rate limit exceeded for {} with type {}", key, type);
//                    meterRegistry.counter("rate_limit.exceeded", "type", type.name()).increment();
//                }
//                
//                return result;
//            });
//        } catch (Exception e) {
//            log.warn("Redis operation failed, falling back to local rate limiter", e);
//            meterRegistry.counter("rate_limit.local_fallback").increment();
//            return getLocalRateLimiter(key, type).acquirePermission();
//        }
//    }
//
//    private RateLimiter getLocalRateLimiter(String key, RateLimitType type) {
//        return registry.rateLimiter(key + "-local", 
//            () -> RateLimiterConfig.custom()
//                .limitForPeriod(getLimit(type))
//                .limitRefreshPeriod(Duration.ofSeconds(getTimeWindow(type)))
//                .timeoutDuration(Duration.ZERO)
//                .build());
//    }
//
//    private int getLimit(RateLimitType type) {
//        return switch (type) {
//            case IP_GLOBAL -> ipGlobalLimit;
//            case IP_AUTH -> ipAuthLimit;
//            case USER_DEFAULT -> userDefaultLimit;
//            case USER_PREMIUM -> userPremiumLimit;
//        };
//    }
//
//    private long getTimeWindow(RateLimitType type) {
//        return 60; // 1 minute for all types
//    }
//
//    public String extractClientIp(HttpServletRequest request) {
//        String ip = getFirstNonProxyIp(request.getHeader("X-Forwarded-For"));
//        if (ip == null) ip = request.getRemoteAddr();
//        return ip;
//    }
//
//    private String getFirstNonProxyIp(String forwardedFor) {
//        if (forwardedFor == null) return null;
//        
//        return Arrays.stream(forwardedFor.split(","))
//            .map(String::trim)
//            .filter(ip -> !isTrustedProxy(ip))
//            .findFirst()
//            .orElse(null);
//    }
//
//    private boolean isTrustedProxy(String ip) {
//        try {
//            IPAddressString ipAddr = new IPAddressString(ip);
//            return trustedProxies.stream()
//                .anyMatch(cidr -> ipAddr.contains(new IPAddressString(cidr)));
//        } catch (Exception e) {
//            log.warn("Invalid IP address format: {}", ip, e);
//            return false;
//        }
//    }
//
//    private void validateConfigs() {
//        if (ipGlobalLimit <= 0 || ipAuthLimit <= 0 || 
//            userDefaultLimit <= 0 || userPremiumLimit <= 0) {
//            throw new IllegalStateException("Rate limit values must be positive");
//        }
//    }
//
//    public enum RateLimitType {
//        IP_GLOBAL, IP_AUTH, USER_DEFAULT, USER_PREMIUM
//    }
//}