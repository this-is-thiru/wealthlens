package com.thiru.wealthlens.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The application's password encoder (W-358d3, D-885 C1a, C2a): writes Argon2id, still reads every BCrypt hash stored before, asks for
 * every older hash to be rewritten, and lets no other stored form sign anyone in.
 */
class PasswordEncodingTest {

    private final PasswordEncoder encoder = new InvestmentTrackerConfig().passwordEncoder();
    private final String todays = new BCryptPasswordEncoder().encode("secret");

    @Test
    void encode_whenAPasswordIsStored_writesLabelledArgon2id() {
        // When / Then — Spring's defaults for Argon2id: 16 MiB, two passes, one lane
        assertThat(encoder.encode("secret")).startsWith("{argon2}$argon2id$v=19$m=16384,t=2,p=1$");
    }

    @Test
    void matches_whenTheHashIsBcryptStoredBefore_matches() {
        // When / Then — unlabelled as today, htpasswd's $2y$, and labelled
        assertThat(encoder.matches("secret", todays)).isTrue();
        assertThat(encoder.matches("secret", todays.replace("$2a$", "$2y$"))).isTrue();
        assertThat(encoder.matches("secret", "{bcrypt}" + todays)).isTrue();
        assertThat(encoder.matches("wrong", todays)).isFalse();
    }

    @Test
    void matches_whenTheStoredFormIsPlainOrADigest_neverMatches() {
        // When / Then — a value written into the database by any means must not sign anyone in
        assertThat(encoder.matches("secret", "{noop}secret")).isFalse();
        assertThat(encoder.matches("secret", "{MD5}5ebe2294ecd0e0f08eab7690d2a6ee69")).isFalse();
    }

    @Test
    void upgradeEncoding_whenTheHashIsOlderThanArgon2_asksForARewrite() {
        // When / Then
        assertThat(encoder.upgradeEncoding(todays)).isTrue();
        assertThat(encoder.upgradeEncoding("{bcrypt}" + todays)).isTrue();
        assertThat(encoder.upgradeEncoding(encoder.encode("secret"))).isFalse();
    }
}
