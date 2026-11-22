package com.thiru.wealthlens.auth.controller;

import com.thiru.wealthlens.auth.dto.LoginRequest;
import com.thiru.wealthlens.auth.dto.LoginResponse;
import com.thiru.wealthlens.auth.dto.RefreshRequest;
import com.thiru.wealthlens.auth.dto.RefreshResponse;
import com.thiru.wealthlens.auth.dto.RegistrationRequest;
import com.thiru.wealthlens.auth.service.AuthService;
import com.thiru.wealthlens.auth.service.SignInAttempts;
import com.thiru.wealthlens.shared.privacy.ProcessingLogFilter;
import com.thiru.wealthlens.shared.web.PublicApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
    private final SignInAttempts signInAttempts;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        ProcessingLogFilter.nameLoginAttempt(loginRequest.getEmail());
        String client = request.getRemoteAddr();
        signInAttempts.requireAllowed(loginRequest.getEmail(), client);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(loginRequest.getEmail(),
                loginRequest.getPassword());
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(auth);
        } catch (AuthenticationException refused) {
            signInAttempts.recordFailure(loginRequest.getEmail(), client);
            throw refused;
        }
        if (authentication.isAuthenticated()) {
            signInAttempts.clear(loginRequest.getEmail(), client);
            return authService.generateToken(loginRequest.getEmail(), authentication);
        } else {
            throw new UsernameNotFoundException("Invalid user request");
        }
    }

    /** Public: the refresh token is the credential, and an expired access token may come with it (W-358b). */
    @PostMapping("/token/refresh")
    public RefreshResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    /** Ends the sign-in the refresh token belongs to; the same answer for a token already ended or unknown. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }

    @PostMapping("/register")
    public String addNewUser(@Valid @RequestBody RegistrationRequest request) {
        return authService.addUser(request);
    }

}
