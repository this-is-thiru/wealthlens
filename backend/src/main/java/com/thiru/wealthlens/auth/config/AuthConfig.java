package com.thiru.wealthlens.auth.config;

import com.thiru.wealthlens.auth.filter.AuthFilter;
import com.thiru.wealthlens.shared.privacy.ProcessingLogFilter;
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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class AuthConfig {

    private final AuthFilter authFilter;
    private final ProcessingLogFilter processingLogFilter;
    private final PasswordEncoder passwordEncoder;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login", "/auth/register", "/helper/**", "/finances/**", "/template/**", "/tax-planning/public/**", "/privacy/rights").permitAll()
                        .requestMatchers("/auth/**", "/portfolio/**", "/reports/**", "/transactions/**", "/corporate-action/**",
                                "/temporary-transactions/**", "/broker-charges/**", "/user-broker-charges/**", "/test/**", "/analytics/**", "/tax-planning/**",
                                // The charges engine. Listed explicitly because the fallback below
                                // is permitAll: a path nobody adds here is public. A rate card sets
                                // what every user is charged, /charges/amc/impose bills real money,
                                // and /user-charges exposes one person's trading history.
                                "/charge-schedules/**", "/charge-catalogue", "/charges/**", "/user-charges/**", "/charge-accounts/**",
                                // Taxpayer data: PAN, family, income. Public by the same fallback unless named (W-096, D-440).
                                "/tax/**",
                                // A user's grievances and the owner's queue (W-237b); only the notice is public.
                                "/privacy/**").authenticated()
                        .anyRequest().permitAll())
                .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(Customizer.withDefaults())
                .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class)
                // Ahead of the token check, so a refused request is recorded with what the person was told (W-237e C1a).
                .addFilterBefore(processingLogFilter, AuthFilter.class).build();
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(authProvider);
    }

}
