package com.thiru.wealthlens.shared.migration;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

/**
 * How each time inside embedded data was held by the {@code m1.6.1} code that set it (W-337g2 C4a, D-954), so the copier
 * writes it in UTC. A UTC-held time was stored through the writing JVM's zone, so it is read back in that written zone; a
 * local-held time was the JVM's local time, stored as its true instant, so it is read in UTC. Keyed by the declaring class's
 * name and the field, so the copier names no other module's type. A time with no entry is refused, never guessed.
 */
final class EmbeddedTimes {

    private static final Set<String> HELD_IN_UTC = Set.of(
            // StalenessSweep.java:90 at m1.6.1: new DraftStaleness(context.nowUtc(), …)
            "com.thiru.wealthlens.taxplanning.workspace.DraftStaleness.since",
            // PortfolioService.java:445, 453 at m1.6.1: assetRequest.orderExecutionDateTime(), TLocalDateTime.atUtc(…);
            // LotLedger.java:201: transactions.order_execution_time, UTC (D-961)
            "com.thiru.wealthlens.portfolio.dto.OrderTimeQuantity.orderExecutionTime",
            // AssetRequestParser.java:210: the upload's cell, held as entered and read back as held (W-408)
            "com.thiru.wealthlens.portfolio.dto.AssetRequest.orderExecutionDateTime",
            // ChargeSummaryReport.java:71 at m1.6.1: merge stamps TLocalDateTime.now(), then ZonedDateTime.now(UTC) (TLocalDateTime.java:16-18)
            "com.thiru.wealthlens.brokercharges.entity.model.ChargeSummaryReport.lastUpdatedTime");

    private static final Set<String> HELD_LOCALLY = Set.of(
            // a corporate-action copy's audit times: the copied action's auditing, LocalDateTime.now() (D-954)
            "com.thiru.wealthlens.shared.entity.helper.AuditColumns.createdAt",
            "com.thiru.wealthlens.shared.entity.helper.AuditColumns.updatedAt",
            // ProfitAndLossService.java:131, 420 at m1.6.1: LocalDateTime.now()
            "com.thiru.wealthlens.portfolio.entity.model.RealisedProfits.lastUpdatedTime");

    private EmbeddedTimes() {
    }

    /** The zone the stored instant is read in to give back the UTC time; empty when no entry classifies the field. */
    static Optional<ZoneId> readZone(Class<?> declaringClass, String field, ZoneId writtenZone) {
        String name = declaringClass.getName() + "." + field;
        if (HELD_IN_UTC.contains(name)) {
            return Optional.of(writtenZone);
        }
        return HELD_LOCALLY.contains(name) ? Optional.of(ZoneOffset.UTC) : Optional.empty();
    }
}
