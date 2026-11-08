package com.thiru.wealthlens.taxplanning.compute;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reads the HR-support table at start, so a table that is not well-formed fails the start, not a request (W-238f). */
@Configuration
public class HrSupportConfig {

    @Bean
    public HrSupportTable hrSupportTable() {
        return HrSupportTable.fromClasspath();
    }
}
