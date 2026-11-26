package com.thiru.wealthlens.shared.migration;

import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;

/**
 * How each time inside embedded data was held by the {@code m1.6.1} code that set it (W-337g2 C4a, D-954), so the copier
 * writes it in UTC. A UTC-held time was stored through the writing JVM's zone, so it is read back in that written zone.
 * Keyed by the declaring class's name and the field, so the copier names no other module's type. A time with no entry is
 * refused, never guessed.
 */
final class EmbeddedTimes {

    private static final Set<String> HELD_IN_UTC = Set.of(
            // StalenessSweep.java:90 at m1.6.1: new DraftStaleness(context.nowUtc(), …)
            "com.thiru.wealthlens.taxplanning.workspace.DraftStaleness.since");

    private EmbeddedTimes() {
    }

    /** The zone the stored instant is read in to give back the UTC time; empty when no entry classifies the field. */
    static Optional<ZoneId> readZone(Class<?> declaringClass, String field, ZoneId writtenZone) {
        return HELD_IN_UTC.contains(declaringClass.getName() + "." + field) ? Optional.of(writtenZone) : Optional.empty();
    }
}
