package com.thiru.wealthlens.shared.config;

import java.time.Clock;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class InvestmentTrackerConfig {

	/**
	 * Writes Argon2id, labelled, and reads every BCrypt hash stored before it — unlabelled (all of them until W-358d3) or {@code $2y$}
	 * from {@code htpasswd} — asking for each to be rewritten at its next sign-in (W-358d3, D-885 C1a). Built from these two encoders
	 * only, not Spring's factory map, so a plain-text {@code {noop}} or a digest such as {@code {MD5}} written into the database can
	 * never sign anyone in (C2a). Argon2 runs on Spring's own defaults (16 MiB, two passes, one lane) and on BouncyCastle.
	 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
		DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder("argon2",
				Map.of("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(), "bcrypt", bcrypt));
		encoder.setDefaultPasswordEncoderForMatches(bcrypt);
		return encoder;
	}

	/**
	 * Today, for code whose result turns on the date: interest on a return runs to the day it is computed (W-112, D-446), so a
	 * test must be able to fix the day. The zone is the system's, as {@code LocalDate.now()} used before.
	 */
	@Bean
	public Clock clock() {
		return Clock.systemDefaultZone();
	}
}
