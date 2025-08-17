package com.sourav.Security.controller;

import com.sourav.Security.exceptions.ValidationException;
import com.sourav.Security.model.AuthResponse;
import com.sourav.Security.model.Users;
import com.sourav.Security.service.UserService;

import javax.security.sasl.AuthenticationException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    @Autowired
    private UserService service;


    @PostMapping("/register")
    public Users register(@RequestBody Users user) throws ValidationException {
        return service.register(user);

    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody Users user) throws AuthenticationException {

        return service.verify(user);
    }
}
