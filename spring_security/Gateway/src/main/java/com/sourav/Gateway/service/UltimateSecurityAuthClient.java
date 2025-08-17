package com.sourav.Gateway.service;

import java.util.concurrent.TimeUnit;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Data;
import reactor.core.publisher.Mono;

@Service
public class UltimateSecurityAuthClient {
    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final MeterRegistry meterRegistry;

    public UltimateSecurityAuthClient(
        @LoadBalanced WebClient.Builder webClientBuilder,
        CircuitBreakerRegistry cbRegistry,
        RetryRegistry retryRegistry,
        MeterRegistry meterRegistry) {
        
        this.webClient = webClientBuilder.baseUrl("lb://ultimate-security").build();
        this.circuitBreaker = cbRegistry.circuitBreaker("authService");
        this.retry = retryRegistry.retry("authService");
        this.meterRegistry = meterRegistry;
    }

    public Mono<Boolean> validateToken(String token) {
    	return Mono.defer(() -> {
    	    long start = System.currentTimeMillis();
    	    return webClient.post()
    	        .uri("/auth/validate")
    	        .header("Authorization", "Bearer " + token)
    	        .retrieve()
    	        .bodyToMono(AuthResponse.class)
    	        .map(AuthResponse::isValid)
    	        .doOnTerminate(() -> 
    	            meterRegistry.timer("auth.service.latency")
    	                .record(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS)
    	        );
    	});
    }


    public Mono<String> fetchNewSecretKey() {
        return webClient.get()
            .uri("/auth/jwt-secret")
            .retrieve()
            .bodyToMono(String.class);
    }

    @Data
    public static class AuthResponse {
        private boolean valid;
    }
}
