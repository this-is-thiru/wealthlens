package com.thiru.wealthlens;

import java.time.ZoneOffset;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableMethodSecurity
@EnableTransactionManagement
@ConfigurationPropertiesScan("com.thiru.wealthlens")
@SpringBootApplication
public class WealthLensApplication {

	/** The JVM runs in UTC whatever the host's zone (D-958): a time taken without the business clock cannot drift with the host. */
	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
		SpringApplication.run(WealthLensApplication.class, args);
	}
}
