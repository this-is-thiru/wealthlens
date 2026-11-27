package com.thiru.wealthlens.shared.migration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * How one source is read (W-417, D-999): the zone its rows were written in, the zone a time is read in, and a tally of what an
 * earlier build left in a form {@code m1.6.1} read past — a retired key left out, or a date stored at midnight in the written
 * zone rather than UTC — so each is named on the source's report line, never dropped without a word. A view made by
 * {@link #timesIn} reads times in another zone and counts into the same tally.
 */
final class FormerForms {

    private final ZoneId writtenZone;
    private final ZoneId timeZone;
    private final Map<String, Integer> counts;

    private FormerForms(ZoneId writtenZone, ZoneId timeZone, Map<String, Integer> counts) {
        this.writtenZone = writtenZone;
        this.timeZone = timeZone;
        this.counts = counts;
    }

    static FormerForms of(ZoneId writtenZone) {
        return new FormerForms(writtenZone, writtenZone, new TreeMap<>());
    }

    /** The same source and tally, its times read in the given zone. */
    FormerForms timesIn(ZoneId zone) {
        return new FormerForms(writtenZone, zone, counts);
    }

    ZoneId writtenZone() {
        return writtenZone;
    }

    ZoneId timeZone() {
        return timeZone;
    }

    void leftOut(String key) {
        counts.merge(key + " left out", 1, Integer::sum);
    }

    /**
     * A stored date as {@code m1.6.1} read it, at UTC ({@code DateToLocalDateConverter} at {@code m1.6.1}). It stored one at
     * midnight UTC since #33 (6cece68c) and at midnight in the JVM's zone before it; any other time is refused.
     */
    LocalDate date(Date stored) {
        LocalDateTime atUtc = LocalDateTime.ofInstant(stored.toInstant(), ZoneOffset.UTC);
        if (atUtc.toLocalTime().equals(LocalTime.MIDNIGHT)) {
            return atUtc.toLocalDate();
        }
        if (!LocalDateTime.ofInstant(stored.toInstant(), writtenZone).toLocalTime().equals(LocalTime.MIDNIGHT)) {
            throw new IllegalStateException("A stored date is at midnight neither in UTC nor in " + writtenZone + ": "
                    + stored.toInstant());
        }
        counts.merge("date at midnight " + writtenZone, 1, Integer::sum);
        return atUtc.toLocalDate();
    }

    /** The tally as the report line names it; empty when the source held no former form. */
    String describe() {
        if (counts.isEmpty()) {
            return "";
        }
        return "read as m1.6.1 read them: " + counts.entrySet().stream().map(each -> each.getValue() + " × " + each.getKey())
                .collect(Collectors.joining(", "));
    }
}
