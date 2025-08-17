package com.sourav.Gateway.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import reactor.core.publisher.Mono;

@Component
	public class JwtValidationFilter implements GatewayFilter {
	    private static final Logger log = LoggerFactory.getLogger(JwtValidationFilter.class);
	    
	    private final JwtValidator jwtValidator;
	    private final ObjectMapper objectMapper;

	    public JwtValidationFilter(JwtValidator jwtValidator, ObjectMapper objectMapper) {
	        this.jwtValidator = jwtValidator;
	        this.objectMapper = objectMapper;
	    }

	    @Override
	    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
	        String token = extractToken(exchange.getRequest());
	        
	        if (token == null) {
	            return unauthorized(exchange, "Missing authorization token");
	        }

	        return jwtValidator.validateToken(token)
	            .flatMap(valid -> {
	                if (!valid) {
	                    return unauthorized(exchange, "Invalid token");
	                }
	                return chain.filter(addUserContext(exchange, token));
	            })
	            .onErrorResume(e -> {
	                log.error("JWT validation error", e);
	                return fallback(exchange, "Authentication service error");
	            });
	    }

	    private ServerWebExchange addUserContext(ServerWebExchange exchange, String token) {
	        Claims claims = parseClaims(token);
	        return exchange.mutate()
	            .request(r -> r.headers(headers -> {
	                headers.set("X-Authenticated-User", claims.getSubject());
	                headers.set("X-User-Roles", String.join(",", getRoles(claims)));
	            }))
	            .build();
	    }

	    private Claims parseClaims(String token) {
	        return Jwts.parserBuilder()
	            .verifyWith(jwtValidator.getSecretKey())
	            .build()
	            .parseSignedClaims(token)
	            .getPayload();
	    }

	    private List<String> getRoles(Claims claims) {
	        return claims.get("roles", List.class);
	    }
	}
