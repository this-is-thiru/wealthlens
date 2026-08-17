package com.thiru.wealthlens.auth.filter;

import com.thiru.wealthlens.auth.dto.AuthHelper;
import com.thiru.wealthlens.auth.service.AuthService;
import com.thiru.wealthlens.auth.service.UserValidator;
import io.jsonwebtoken.Claims;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Log4j2
public class AuthFilter extends OncePerRequestFilter {

    private final AuthService authService;

    AuthFilter(final AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain filterChain) throws ServletException, IOException {
        try {
            final String authHeader = request.getHeader("Authorization");
            // Proceed further if auth is not bearer
            if (!isBearerAuth(authHeader)) {
                filterChain.doFilter(request, response);
                return;
            }

            final String token = authHeader.substring(7);
            Claims claims = authService.extractAllClaims(token);

            if (claims != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                String username = claims.getSubject();
                List<GrantedAuthority> userAuthorities = AuthHelper.getAuthorities(claims);
                if (UserValidator.isRestrictedUser(username, userAuthorities)) {
                    throw new BadCredentialsException("User: " + username + " does not have access");
                }

                if (authService.validateToken(claims)) {
                    final UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(username, null, userAuthorities);
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } else if (claims == null) {
                log.error("Token expired or invalid");
                throw new BadCredentialsException("Token expired or invalid");
            }
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            log.error("Error in authentication filter {}", e.getMessage());
            filterChain.doFilter(request, response);
        }
    }

    private static boolean isBearerAuth(String authHeader) {
        return authHeader != null && authHeader.startsWith("Bearer ");
    }
}
