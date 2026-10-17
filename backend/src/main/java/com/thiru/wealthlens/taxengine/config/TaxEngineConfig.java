package com.thiru.wealthlens.taxengine.config;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import java.util.Set;
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
     * Declared rather than scanned: a year that silently fails to appear is worse than one that fails to load. A
     * test ties this set to the locks shipped.
     */
    static final Set<TaxYearId> SHIPPED_YEARS = Set.of(TaxYearId.of(2025), TaxYearId.of(2026));

    @Bean
    public ShippedRulePacks shippedRulePacks() {
        return ShippedRulePacks.compile("rulepacks", SHIPPED_YEARS);
    }
}
