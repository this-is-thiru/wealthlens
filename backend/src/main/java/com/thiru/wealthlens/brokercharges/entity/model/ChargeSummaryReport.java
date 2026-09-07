package com.thiru.wealthlens.brokercharges.entity.model;

import com.thiru.wealthlens.shared.util.time.TLocalDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Charges aggregated over a period, keyed by charge code.
 *
 * <p>Replaces the six fixed columns of the superseded {@code BrokerChargesReport}. A rate card that
 * introduces a charge introduces a key here, so a new charge reaches every report without a Java
 * change (AC-1). Under the old shape it was worse than a compile error: an unrecognised charge was
 * silently dropped, and a report that quietly under-reports is one nobody checks.
 *
 * <p>This is a derived projection. {@code UserChargeEntity} is the source of truth, and a
 * recomputation rebuilds the affected financial year from those rows rather than subtracting
 * deltas — a contribution already folded into a sum cannot be reliably taken back out.
 *
 * <p>Not a document of its own: it is embedded in the profit-and-loss hierarchy, which is why
 * {@code lastUpdatedTime} is stamped by {@link #merge} rather than left to
 * {@code @LastModifiedDate}. Spring Data's auditing callbacks fire for the aggregate root, not for
 * a nested object, so the annotation alone would leave the field permanently null.
 */
@Data
public class ChargeSummaryReport {

    /** Two decimal places: a report is denominated in paise, whatever precision a rule carried. */
    private static final int PAISE_SCALE = 2;

    @Field("amount_by_code")
    private Map<String, Double> amountByCode = new HashMap<>();

    @Field("total_charges")
    private double totalCharges;

    @Field("last_updated_time")
    private LocalDateTime lastUpdatedTime;

    /**
     * Folds one event's charge breakdown into this period.
     *
     * <p>Arithmetic is {@code BigDecimal} rather than {@code double}. A period accumulates hundreds
     * of paise-scale amounts, and {@code 0.10} added ten times in {@code double} is
     * {@code 0.9999999999999999} — which reaches a report as ₹1.00 only by luck of formatting, and
     * reaches a comparison against a broker's statement as a mismatch.
     *
     * <p>{@code totalCharges} is recomputed from the stored per-code amounts rather than
     * accumulated in parallel, so the total always equals the sum of the columns printed beside it.
     * Two figures that are supposed to agree should be derived from one, not maintained together.
     *
     * @param increments amounts by charge code, typically {@code UserChargeEntity.amountByCode}.
     *                   Null or empty is a no-op: a zero-charge row is legitimate, written for a
     *                   period with no rate card on file so the gap is visible rather than lost
     * @throws IllegalArgumentException if any amount is null, which is a caller defect that folding
     *                                  in as zero would hide
     */
    public void merge(Map<String, Double> increments) {
        if (increments == null || increments.isEmpty()) {
            return;
        }

        increments.forEach((code, increment) -> {
            if (increment == null) {
                throw new IllegalArgumentException("Charge amount for code " + code + " must not be null");
            }
            BigDecimal running = BigDecimal.valueOf(amountByCode.getOrDefault(code, 0.0));
            amountByCode.put(code, scaled(running.add(BigDecimal.valueOf(increment))));
        });

        totalCharges = scaled(amountByCode.values().stream()
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        lastUpdatedTime = TLocalDateTime.now();
    }

    private static double scaled(BigDecimal amount) {
        return amount.setScale(PAISE_SCALE, RoundingMode.HALF_UP).doubleValue();
    }
}
