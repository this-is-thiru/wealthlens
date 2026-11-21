package com.thiru.wealthlens.auth.config;

import com.thiru.wealthlens.auth.service.UserValidator;
import com.thiru.wealthlens.shared.privacy.ProcessingLogFilter;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class AuthConfig {

    /** Open to a caller without a login; RouteAccessIntegrationTest keeps its own copy, so a change here is a change there. */
    private static final String[] PUBLIC_PATHS = {"/auth/login", "/auth/register", "/helper/**", "/finances/**", "/template/**",
            "/privacy/rights",
            // The notice as a page (W-247); an exact path, so nothing under /privacy/** opens with it.
            "/privacy",
            // Where a refusal or failure is rendered: an error dispatch carries no login, so a 403 from a rule
            // below would otherwise come back as 401 (W-359a, ActuatorIntegrationTest).
            "/error",
            // The refresh token is the credential on these two (W-358b), not the access token
            "/auth/token/refresh", "/auth/logout"};

    private final ProcessingLogFilter processingLogFilter;
    private final PasswordEncoder passwordEncoder;
    private final UserDetailsService userDetailsService;
    private final JwtDecoder accessTokenDecoder;
    private final JwtAuthenticationConverter accessTokenAuthentication;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers("/auth/**", "/portfolio/**", "/reports/**", "/transactions/**", "/corporate-action/**",
                                "/temporary-transactions/**", "/broker-charges/**", "/user-broker-charges/**", "/analytics/**", "/tax-planning/**",
                                // The charges engine: a rate card sets what every user is charged, /charges/amc/impose bills
                                // real money, and /user-charges exposes one person's trading history.
                                "/charge-schedules/**", "/charge-catalogue", "/charges/**", "/user-charges/**", "/charge-accounts/**",
                                // Taxpayer data: PAN, family, income (W-096, D-440).
                                "/tax/**",
                                // A user's grievances and the owner's queue (W-237b); only the notice is public.
                                "/privacy/**").access(signedInOnOwnPath())
                        // Health and readiness (W-359a): an admin's, like the other operator routes
                        .requestMatchers("/actuator/**").hasRole("SUPER_USER")
                        // Deny by default (W-358a): a path nobody names above still needs a login, so a new route never
                        // opens by accident, as POST /properties/user/{email}/sales did while this line was permitAll.
                        // RouteAccessIntegrationTest checks every route the application serves against these rules.
                        .anyRequest().access(signedInOnOwnPath()))
                .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(Customizer.withDefaults())
                // Spring's resource server reads the access token (W-358b, AccessTokenConfig)
                .oauth2ResourceServer(server -> server.bearerTokenResolver(tokenOutsidePublicPaths())
                        .jwt(jwt -> jwt.decoder(accessTokenDecoder).jwtAuthenticationConverter(accessTokenAuthentication)))
                // Ahead of the token check, so a refused request is recorded with what the person was told (W-237e C1a).
                .addFilterBefore(processingLogFilter, BearerTokenAuthenticationFilter.class).build();
    }

    /**
     * A login, and on a {@code /…/user/{email}/…} path that email's (or a super user's): another user's path answers 403. Until
     * W-358c's {@code /me} routes take the user from the token instead of the path.
     */
    private static AuthorizationManager<RequestAuthorizationContext> signedInOnOwnPath() {
        AuthorizationManager<RequestAuthorizationContext> signedIn = AuthenticatedAuthorizationManager.authenticated();
        return (authentication, context) -> {
            AuthorizationResult login = signedIn.authorize(authentication, context);
            if (login == null || !login.isGranted()) {
                return login;
            }
            Authentication caller = authentication.get();
            return new AuthorizationDecision(!UserValidator.isRestrictedUser(caller.getName(), caller.getAuthorities(), context.getRequest()));
        };
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
        return new ProviderManager(authProvider);
    }

}
