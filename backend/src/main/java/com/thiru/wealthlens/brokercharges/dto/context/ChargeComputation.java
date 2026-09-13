package com.thiru.wealthlens.brokercharges.dto.context;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.entity.ChargeLine;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * What one event cost, line by line, together with why those lines and no others.
 *
 * <p>An empty result is never silent: {@code resolution} distinguishes a corporate action that was
 * deliberately exempt from a period with no rate card on file.
 */
public record ChargeComputation(
        String scheduleId,
        String scheduleCode,
        String instrumentId,
        ChargeResolution resolution,
        List<ChargeLine> lines,
        double total) {

    public static ChargeComputation empty(ChargeResolution resolution) {
        return new ChargeComputation(null, null, null, resolution, List.of(), 0.0);
    }

    /** The amount charged under a code, or zero if it was not charged. */
    public double amountOf(String code) {
        return lines.stream()
                .filter(line -> line.getCode().equals(code))
                .mapToDouble(ChargeLine::getAmount)
                .sum();
    }

    /** Denormalised for aggregation. Preserves evaluation order so a breakdown reads naturally. */
    public Map<String, Double> amountByCode() {
        return lines.stream().collect(Collectors.toMap(
                ChargeLine::getCode,
                ChargeLine::getAmount,
                Double::sum,
                LinkedHashMap::new));
    }

    /** Whether anything was actually charged. */
    public boolean isEmpty() {
        return lines.isEmpty();
    }
}
