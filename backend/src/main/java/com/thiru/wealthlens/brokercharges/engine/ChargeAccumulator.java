package com.thiru.wealthlens.brokercharges.engine;

import com.thiru.wealthlens.brokercharges.entity.ChargeLine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The running state of one charge evaluation.
 *
 * <p>Rules are evaluated in ascending order and each appends a line here. A derived rule such as
 * GST then reads back the lines its {@code baseCodes} name — which is what keeps tax off securities
 * transaction tax and stamp duty, neither of which is taxable, and which the superseded
 * implementation got wrong by taxing a merged bucket.
 *
 * <p>Amounts are held as {@code BigDecimal}. GST is a percentage of a sum of already-rounded lines,
 * and accumulating that in {@code double} drifts into paise against a real contract note.
 *
 * <p>Not thread-safe, and does not need to be: an accumulator belongs to a single evaluation.
 */
public class ChargeAccumulator {

    private final List<ChargeLine> lines = new ArrayList<>();

    /** Appends a computed line. Order is preserved, so a contract note reads as it was applied. */
    public void add(ChargeLine line) {
        if (line == null) {
            throw new IllegalArgumentException("Charge line must not be null");
        }
        lines.add(line);
    }

    /**
     * The summed amount of the named codes — the base a derived rule applies its rate to.
     *
     * <p>A named code that was never charged contributes nothing rather than failing: a rule may
     * legitimately name a base that this event's side or eligibility filtered out.
     */
    public BigDecimal sumOf(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return lines.stream()
                .filter(line -> codes.contains(line.getCode()))
                .map(line -> BigDecimal.valueOf(line.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * The amount charged under one code, summing repeats. A per-lot rule emits one line per lot
     * under a single code.
     */
    public BigDecimal amountOf(String code) {
        return sumOf(List.of(code));
    }

    public BigDecimal total() {
        return lines.stream()
                .map(line -> BigDecimal.valueOf(line.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** The lines so far, in evaluation order. Unmodifiable: the engine owns the evaluation. */
    public List<ChargeLine> lines() {
        return Collections.unmodifiableList(lines);
    }
}
