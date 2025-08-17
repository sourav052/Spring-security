package com.sourav.Security.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Service
public class JWTService {


	 	private final String secretKey;
	    private final long accessTokenValidity;
	    private final long refreshTokenValidity;
	    private final Map<String, String> tokenBlacklist = new ConcurrentHashMap();

	    public JWTService() throws NoSuchAlgorithmException {
	        KeyGenerator keyGen = KeyGenerator.getInstance("HmacSHA256");
	        SecretKey sk = keyGen.generateKey();
	        this.secretKey = Base64.getEncoder().encodeToString(sk.getEncoded());
	        this.accessTokenValidity = 30 * 60 * 1000; // 30 minutes
	        this.refreshTokenValidity = 7 * 24 * 60 * 60 * 1000; // 7 days
	    }
	    public String generateAccessToken(String username, List<String> roles) {
	        return buildToken(username, roles, accessTokenValidity);
	    }

	    public String generateRefreshToken(String username) {
	        return buildToken(username, Collections.emptyList(), refreshTokenValidity);
	    }

	    private String buildToken(String username, List<String> roles, long validity) {
	        Map<String, Object> claims = new HashMap<>();
	        claims.put("roles", roles);
	        claims.put("type", validity == accessTokenValidity ? "ACCESS" : "REFRESH");

	        return Jwts.builder()
	                .claims(claims)
	                .subject(username)
	                .issuedAt(new Date(System.currentTimeMillis()))
	                .expiration(new Date(System.currentTimeMillis() + validity))
	                .signWith(getKey())
	                .compact();
	    }
	    public void invalidateToken(String token) {
	        if (token != null && !token.isBlank()) {
	            Claims claims = extractAllClaims(token);
	            Date expiration = claims.getExpiration();
	            long ttl = expiration.getTime() - System.currentTimeMillis();
	            if (ttl > 0) {
	                tokenBlacklist.put(token, "invalidated");
	            }
	        }
	    }
	    public boolean isTokenInvalidated(String token) {
	        return tokenBlacklist.containsKey(token);
	    }

	    private SecretKey getKey() {
	        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
	        return Keys.hmacShaKeyFor(keyBytes);
	    }

	    public String extractUserName(String token) {
	        return extractClaim(token, Claims::getSubject);
	    }

	    public List<String> extractRoles(String token) {
	        return extractClaim(token, claims -> claims.get("roles", List.class));
	    }

	    public String extractTokenType(String token) {
	        return extractClaim(token, claims -> claims.get("type", String.class));
	    }

	    private <T> T extractClaim(String token, Function<Claims, T> claimResolver) {
	        final Claims claims = extractAllClaims(token);
	        return claimResolver.apply(claims);
	    }

	    private Claims extractAllClaims(String token) {
	        try {
	            return Jwts.parser()
	                    .verifyWith(getKey())
	                    .build()
	                    .parseSignedClaims(token)
	                    .getPayload();
	        } catch (Exception e) {
	            throw new JwtException("Invalid JWT token: " + e.getMessage());
	        }
	    }

	    public boolean validateToken(String token, UserDetails userDetails) {
	        try {
	            final String userName = extractUserName(token);
	            return (userName.equals(userDetails.getUsername()) && 
	                   !isTokenExpired(token) && 
	                   !isTokenInvalidated(token));
	        } catch (Exception e) {
	            return false;
	        }
	    }

	    private boolean isTokenExpired(String token) {
	        return extractExpiration(token).before(new Date());
	    }

	    private Date extractExpiration(String token) {
	        return extractClaim(token, Claims::getExpiration);
	    }

	    public void cleanExpiredTokens() {
	        tokenBlacklist.entrySet().removeIf(entry -> {
	            try {
	                return isTokenExpired(entry.getKey());
	            } catch (Exception e) {
	                return true;
	            }
	        });
	    }

}
