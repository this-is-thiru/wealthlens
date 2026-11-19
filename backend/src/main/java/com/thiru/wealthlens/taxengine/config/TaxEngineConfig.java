package com.thiru.wealthlens.taxengine.config;

import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Compiles the shipped rule packs as the application starts (D-210), so a timeline that no longer matches its
 * lock stops the boot rather than failing a request — packs are compiled at startup only, never on a request
 * path ({@code 02-TECH-SPEC.md} T4; {@code DR 07} X2).
 *
 * <p>The bean is internal to {@code taxengine}: no named interface exposes it until a module first calls the
 * engine (D-048).
 */
@Configuration
public class TaxEngineConfig {

    /**
     * The classpath directory of the shipped packs. Its {@code shipped-years.json} declares the years, rather than
     * a scan finding them: a year that silently fails to appear is worse than one that fails to load. A test ties
     * the manifest to the locks shipped (D-268).
     */
    static final String ROOT = "rulepacks";

    @Bean
    public ShippedRulePacks shippedRulePacks() {
        return ShippedRulePacks.compile(ROOT, ClasspathRulePackProvider.shippedYears(ROOT));
    }
}
