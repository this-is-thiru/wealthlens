package com.thiru.wealthlens.shared.config;

import com.thiru.wealthlens.portfolio.migration.TradeOutcomeFinancialYearMigration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * On startup, rewrites any {@code trade_outcomes} row still carrying the {@code FY2025-26} year form (D-239).
 *
 * <p>Runs on every start and changes nothing once no such row remains, so it is safe to leave in place; it can be
 * deleted with its migration once every database has started on it.
 */
@Component
@RequiredArgsConstructor
public class TradeOutcomeFinancialYearMigrationRunner implements CommandLineRunner {

    private final TradeOutcomeFinancialYearMigration migration;

    @Override
    public void run(String... args) {
        migration.rewriteFinancialYears();
    }
}
