package com.thiru.wealthlens.auth.config;

import com.thiru.wealthlens.shared.privacy.ProcessingLogFilter;
import com.thiru.wealthlens.shared.web.ApiZones;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class AuthConfig {

    /**
     * Open to a caller without a login: the public zone (D-855), the notice as a page (W-247) and where a refusal is rendered (an error
     * dispatch carries no login, so a 403 from a rule below would otherwise come back as 401; W-359a). RouteAccessIntegrationTest keeps
     * its own copy, so a change here is a change there.
     */
    private static final String[] PUBLIC_PATHS = {ApiZones.VERSION + "/public/**", "/privacy", "/error"};

    private final ProcessingLogFilter processingLogFilter;
    private final PasswordEncoder passwordEncoder;
    private final UserDetailsService userDetailsService;
    private final UserDetailsPasswordService userDetailsPasswordService;
    private final JwtDecoder accessTokenDecoder;
    private final JwtAuthenticationConverter accessTokenAuthentication;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // A signed-in caller; a /me route takes its user from the token (D-855 C4a), so no path names another user
                        .requestMatchers(ApiZones.VERSION + "/me/**").authenticated()
                        // The owner's: rate cards, rebuilds, the privacy duties, and one user's data named in the path (D-855 C3a)
                        .requestMatchers(ApiZones.VERSION + "/admin/**", "/actuator/**").hasRole("SUPER_USER")
                        // Deny by default (W-358a): a route outside the zones answers nobody, so a new controller without a
                        // zone annotation never opens by accident. RouteAccessIntegrationTest checks every route against these rules.
                        .anyRequest().denyAll())
                .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Spring's resource server reads the access token (W-358b, AccessTokenConfig)
                .oauth2ResourceServer(server -> server.bearerTokenResolver(tokenOutsidePublicPaths())
                        .jwt(jwt -> jwt.decoder(accessTokenDecoder).jwtAuthenticationConverter(accessTokenAuthentication)))
                // Ahead of the token check, so a refused request is recorded with what the person was told (W-237e C1a).
                .addFilterBefore(processingLogFilter, BearerTokenAuthenticationFilter.class).build();
    }

    /** A public path ignores the Authorization header, so a stale access token sent along never refuses the sign-in or the refresh. */
    private static BearerTokenResolver tokenOutsidePublicPaths() {
        DefaultBearerTokenResolver header = new DefaultBearerTokenResolver();
        RequestMatcher publicPaths = new OrRequestMatcher(Arrays.stream(PUBLIC_PATHS).<RequestMatcher>map(PathPatternRequestMatcher::pathPattern).toList());
        return request -> publicPaths.matches(request) ? null : header.resolve(request);
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        // Rewrites an older stored hash as Argon2id when its owner signs in (W-358d3)
        authProvider.setUserDetailsPasswordService(userDetailsPasswordService);
        return new ProviderManager(authProvider);
    }

}
