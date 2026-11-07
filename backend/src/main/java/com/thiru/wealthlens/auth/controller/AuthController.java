package com.thiru.wealthlens.auth.controller;

import com.thiru.wealthlens.auth.dto.LoginRequest;
import com.thiru.wealthlens.auth.dto.LoginResponse;
import com.thiru.wealthlens.auth.dto.RegistrationRequest;
import com.thiru.wealthlens.auth.dto.RoleUpgradeRequest;
import com.thiru.wealthlens.auth.service.AuthService;
import com.thiru.wealthlens.shared.privacy.ProcessingLogFilter;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest loginRequest) {
        ProcessingLogFilter.nameLoginAttempt(loginRequest.getEmail());

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(loginRequest.getEmail(),
                loginRequest.getPassword());
        Authentication authentication = authenticationManager.authenticate(auth);
        if (authentication.isAuthenticated()) {
            return authService.generateToken(loginRequest.getEmail(), authentication);
        } else {
            throw new UsernameNotFoundException("Invalid user request");
        }
    }

    @PutMapping("/user/{email}/change/password")
    public String changePassword(@PathVariable String email, @RequestBody RegistrationRequest createUserRequest) {
        return authService.changePassword(email, createUserRequest);
    }

    @PostMapping("/register")
    public String addNewUser(@RequestBody RegistrationRequest request) {
        return authService.addUser(request);
    }

    @PostMapping("/test")
    public RoleUpgradeRequest testUserRole(@RequestBody RoleUpgradeRequest request) {
        return request;
    }

    @GetMapping("/login")
    public String login1() {
        return "login";
    }
}
