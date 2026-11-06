package com.thiru.wealthlens.shared.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class InvestmentTrackerConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
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
