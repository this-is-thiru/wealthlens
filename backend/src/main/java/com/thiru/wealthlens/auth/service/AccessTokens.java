package com.thiru.wealthlens.auth.service;

import com.thiru.wealthlens.auth.config.AccessTokenConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues the short-lived access token (D-853 C3a: 15 minutes); a refresh token renews it, so a stolen one is worth little. */
@Service
@RequiredArgsConstructor
public class AccessTokens {

    public static final Duration LIFETIME = Duration.ofMinutes(15);

    private final JwtEncoder encoder;
    private final Clock clock;

    public String issue(String email, Collection<String> authorities) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(email).issuedAt(now).expiresAt(now.plus(LIFETIME))
                .claim(AccessTokenConfig.AUTHORITIES_CLAIM, List.copyOf(authorities)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
