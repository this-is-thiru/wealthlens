package com.thiru.wealthlens.auth.config;

import com.nimbusds.jose.JOSEException;
import com.thiru.wealthlens.shared.privacy.HeldAccounts;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Access tokens through Spring's own JWT encoder and decoder (W-358b, D-853 C3a), HS256 on the existing {@code JWT_SECURITY_KEY}, so
 * there is no new key to create or deploy. Each token names its key by the key's RFC 7638 thumbprint, so a second key can be added
 * later and told apart without any configuration today.
 */
@Configuration
@RequiredArgsConstructor
public class AccessTokenConfig {

    /** The claim holding the caller's roles and sign-in factors, as Spring Security names them ({@code ROLE_USER}, {@code FACTOR_PASSWORD}). */
    public static final String AUTHORITIES_CLAIM = "authorities";

    private final SecurityConfig securityConfig;
    private final HeldAccounts heldAccounts;
    private final Clock clock;

    @Bean
    public JwtEncoder accessTokenEncoder() {
        return NimbusJwtEncoder.withSecretKey(signingKey()).algorithm(MacAlgorithm.HS256).jwkPostProcessor(key -> {
            try {
                key.keyIDFromThumbprint();
            } catch (JOSEException e) {
                throw new IllegalStateException("The key id of JWT_SECURITY_KEY could not be computed", e);
            }
        }).build();
    }

    /** Checks the signature, the expiry on the application's clock and, on every request, that the account is not held (W-237d C3a). */
    @Bean
    public JwtDecoder accessTokenDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey()).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator expiry = new JwtTimestampValidator();
        expiry.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(expiry, notHeld()));
        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter accessTokenAuthentication() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(AUTHORITIES_CLAIM);
        authorities.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** A held account's token stops working at once, not when it expires. */
    private OAuth2TokenValidator<Jwt> notHeld() {
        OAuth2Error held = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "The account is held for erasure", null);
        return jwt -> heldAccounts.contains(jwt.getSubject()) ? OAuth2TokenValidatorResult.failure(held) : OAuth2TokenValidatorResult.success();
    }

    private SecretKey signingKey() {
        return new SecretKeySpec(Base64.getDecoder().decode(securityConfig.keySecret()), "HmacSHA256");
    }
}
