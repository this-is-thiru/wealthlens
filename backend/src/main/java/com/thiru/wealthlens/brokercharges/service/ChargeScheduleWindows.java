package com.thiru.wealthlens.brokercharges.service;

import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Refuses two rate cards that would both price the same trade on the same day.
 *
 * <p>{@code findCandidates} matches {@code start_date <= d <= end_date}, inclusive at both ends. Two
 * cards of one scope whose windows overlap are therefore both candidates for every day in the
 * overlap, and which one prices the trade is decided by the resolver's tie-break rather than by
 * anything a human intended. A rate is wrong by whatever the two cards disagree about, silently.
 *
 * <p>Neither existing check catches it. {@code ChargeScheduleValidator} validates one card in
 * isolation and cannot see its neighbours; {@code ChargeScheduleService.publish} closes the
 * incumbent's window before opening the successor's, so it cannot produce an overlap. Seeding
 * several files can, which is where this is applied.
 *
 * <p>An open-ended card counts as running to the end of time, so two open-ended cards for one scope
 * always overlap. That is the same fault reached by a different route, and the same message serves.
 */
final class ChargeScheduleWindows {

    private ChargeScheduleWindows() {
    }

    static void requireNoOverlap(List<ChargeScheduleEntity> schedules) {
        Map<String, List<ChargeScheduleEntity>> byScope = schedules.stream()
                .collect(Collectors.groupingBy(ChargeScheduleWindows::scope));

        List<String> clashes = new ArrayList<>();
        byScope.values().forEach(scoped -> {
            List<ChargeScheduleEntity> inOrder = scoped.stream()
                    .sorted(Comparator.comparing(ChargeScheduleEntity::getStartDate,
                            Comparator.nullsFirst(Comparator.naturalOrder())))
                    .toList();

            for (int i = 0; i + 1 < inOrder.size(); i++) {
                ChargeScheduleEntity earlier = inOrder.get(i);
                ChargeScheduleEntity later = inOrder.get(i + 1);
                if (overlaps(earlier, later)) {
                    clashes.add(earlier.getScheduleCode() + " and " + later.getScheduleCode());
                }
            }
        });

        if (!clashes.isEmpty()) {
            throw new BadRequestException(
                    "Rate cards with the same scope have overlapping validity windows, so a trade in the "
                            + "overlap would be priced by whichever the resolver happened to prefer: "
                            + String.join("; ", clashes));
        }
    }

    /** Open-ended runs to the end of time, so it overlaps anything that starts after it. */
    private static boolean overlaps(ChargeScheduleEntity earlier, ChargeScheduleEntity later) {
        LocalDate earlierEnd = earlier.getEndDate();
        return earlierEnd == null || !earlierEnd.isBefore(later.getStartDate());
    }

    /**
     * The dimensions the resolver matches on. Two cards differing in any of them are different
     * scopes and may overlap freely — that is how delivery and intraday coexist for one broker.
     */
    private static String scope(ChargeScheduleEntity schedule) {
        return String.join("|",
                Objects.toString(schedule.getBrokerName(), ""),
                Objects.toString(schedule.getAssetType(), ""),
                Objects.toString(schedule.getSegment(), ""),
                Objects.toString(schedule.getExchange(), ""),
                Objects.toString(schedule.getPlanCode(), ""));
    }
}
