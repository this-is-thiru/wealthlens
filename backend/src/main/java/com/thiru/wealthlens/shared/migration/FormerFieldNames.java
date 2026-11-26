package com.thiru.wealthlens.shared.migration;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

/**
 * The {@code @Field} names {@code m1.6.1} stored a field under where the class no longer carries them and they are not the
 * field's snake_case name (W-337g2b3 C1a), so {@link JsonWalk} can match the stored key. Keyed by the declaring class's name
 * and the field, so the copier names no other module's type. Removed with the copier (W-337j).
 */
final class FormerFieldNames {

    private static final Map<String, String> STORED_UNDER = Map.of(
            // FinancialReport.java:19 at m1.6.1
            "com.thiru.wealthlens.portfolio.entity.model.FinancialReport.monthlyReport", "capital_gains",
            // MonthlyReport.java:15, 18 at m1.6.1
            "com.thiru.wealthlens.portfolio.entity.model.MonthlyReport.firstFortnightReport", "first_half_report",
            "com.thiru.wealthlens.portfolio.entity.model.MonthlyReport.secondFortnightReport", "second_half_report");

    private FormerFieldNames() {
    }

    static Optional<String> of(Field field) {
        return Optional.ofNullable(STORED_UNDER.get(field.getDeclaringClass().getName() + "." + field.getName()));
    }
}
