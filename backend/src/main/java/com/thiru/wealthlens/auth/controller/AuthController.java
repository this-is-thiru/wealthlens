package com.thiru.wealthlens.auth.controller;

import com.thiru.wealthlens.auth.dto.LoginRequest;
import com.thiru.wealthlens.auth.dto.LoginResponse;
import com.thiru.wealthlens.auth.dto.RefreshRequest;
import com.thiru.wealthlens.auth.dto.RefreshResponse;
import com.thiru.wealthlens.auth.dto.RegistrationRequest;
import com.thiru.wealthlens.auth.service.AuthService;
import com.thiru.wealthlens.shared.privacy.ProcessingLogFilter;
import com.thiru.wealthlens.shared.web.PublicApi;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@PublicApi
@RestController
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

    /** Public: the refresh token is the credential, and an expired access token may come with it (W-358b). */
    @PostMapping("/token/refresh")
    public RefreshResponse refresh(@RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    /** Ends the sign-in the refresh token belongs to; the same answer for a token already ended or unknown. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }

    @PostMapping("/register")
    public String addNewUser(@RequestBody RegistrationRequest request) {
        return authService.addUser(request);
    }

    @GetMapping("/login")
    public String login1() {
        return "login";
    }
}
