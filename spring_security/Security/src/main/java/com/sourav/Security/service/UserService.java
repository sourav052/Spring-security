
package com.sourav.Security.service;


import javax.security.sasl.AuthenticationException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.sourav.Security.exceptions.ValidationException;
import com.sourav.Security.model.AuthResponse;
import com.sourav.Security.model.UserPrincipal;
import com.sourav.Security.model.Users;
import com.sourav.Security.repo.UserRepo;

@Service
public class UserService {

    @Autowired
    private JWTService jwtService;

    @Autowired
    private AuthenticationManager authManager;

    @Autowired
    private UserRepo repo;
    
    @Autowired
    @Lazy
    ApplicationContext context;

    @Autowired
    @Lazy
    private PasswordEncoder passwordEncoder;

    public Users register(Users user) throws ValidationException {
        validateUserInput(user);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return repo.save(user);
    }

    public AuthResponse verify(Users user) throws AuthenticationException {
        Authentication authentication = authManager.authenticate(
            new UsernamePasswordAuthenticationToken(user.getUsername(), user.getPassword()));
        
        if (authentication.isAuthenticated()) {
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            String accessToken = jwtService.generateAccessToken(user.getUsername(), principal.getRoles());
            String refreshToken = jwtService.generateRefreshToken(user.getUsername());
            return new AuthResponse(accessToken, refreshToken);
        }
        throw new AuthenticationException("Invalid credentials");
    }

    public AuthResponse refreshToken(String refreshToken) throws AuthenticationException {
        if (jwtService.isTokenInvalidated(refreshToken)) {
            throw new AuthenticationException("Refresh token was invalidated");
        }

        String username = jwtService.extractUserName(refreshToken);
        UserDetails userDetails = context.getBean(MyUserDetailsService.class).loadUserByUsername(username);
        
        if (jwtService.validateToken(refreshToken, userDetails)) {
            UserPrincipal principal = (UserPrincipal) userDetails;
            String newAccessToken = jwtService.generateAccessToken(username, principal.getRoles());
            String newRefreshToken = jwtService.generateRefreshToken(username);
            jwtService.invalidateToken(refreshToken); // Invalidate old refresh token
            return new AuthResponse(newAccessToken, newRefreshToken);
        }
        throw new AuthenticationException("Invalid refresh token");
    }

    public void logout(String accessToken, String refreshToken) {
        jwtService.invalidateToken(accessToken);
        jwtService.invalidateToken(refreshToken);
    }

    private void validateUserInput(Users user) throws ValidationException {
        if (user.getUsername() == null || user.getUsername().length() < 4) {
            throw new ValidationException("Username must be at least 4 characters");
        }
        if (user.getPassword() == null || user.getPassword().length() < 8) {
            throw new ValidationException("Password must be at least 8 characters");
        }
    }
}