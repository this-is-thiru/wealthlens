package com.thiru.wealthlens.auth.controller;

import com.thiru.wealthlens.auth.dto.RegistrationRequest;
import com.thiru.wealthlens.auth.service.AuthService;
import com.thiru.wealthlens.shared.web.CallerEmail;
import com.thiru.wealthlens.shared.web.MeApi;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** A signed-in caller's password (split from {@code AuthController} by D-855 C6a, so each class has one zone). */
@AllArgsConstructor
@MeApi
@RestController
public class PasswordController {

    private final AuthService authService;

    @PutMapping("/password")
    public String changePassword(@CallerEmail String email, @RequestBody RegistrationRequest createUserRequest) {
        return authService.changePassword(email, createUserRequest);
    }
}
