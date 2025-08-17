package com.sourav.Security.controller;

import jakarta.servlet.http.HttpServletRequest;

import javax.security.sasl.AuthenticationException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sourav.Security.exceptions.ValidationException;
import com.sourav.Security.model.AuthResponse;
import com.sourav.Security.model.RefreshTokenRequest;
import com.sourav.Security.model.Users;
import com.sourav.Security.service.UserService;



@RestController
@RequestMapping("/auth")
public class HelloController {

	    @Autowired
	    private UserService userService;

	    @PostMapping("/register")
	    public ResponseEntity<?> register( @RequestBody Users user) throws ValidationException {
	        Users registeredUser = userService.register(user);
	        return ResponseEntity.ok(registeredUser);
	    }

	    @PostMapping("/login")
	    public ResponseEntity<AuthResponse> login( @RequestBody Users user) throws AuthenticationException {
	        AuthResponse response = userService.verify(user);
	        return ResponseEntity.ok(response);
	    }

	    @PostMapping("/refresh-token")
	    public ResponseEntity<AuthResponse> refreshToken( @RequestBody RefreshTokenRequest request) throws AuthenticationException {
	        AuthResponse response = userService.refreshToken(request.getRefreshToken());
	        return ResponseEntity.ok(response);
	    }

	    @PostMapping("/logout")
	    public ResponseEntity<?> logout(
	            @RequestHeader("Authorization") String authHeader,
	            @RequestBody RefreshTokenRequest request) {
	        String accessToken = authHeader.substring(7);
	        userService.logout(accessToken, request.getRefreshToken());
	        return ResponseEntity.ok().build();
	    }
	}
