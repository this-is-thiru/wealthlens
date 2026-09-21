package com.thiru.wealthlens.taxengine.result;

import com.thiru.wealthlens.taxengine.input.InputsFingerprint;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import com.thiru.wealthlens.taxrules.id.RegimeCode;
import com.thiru.wealthlens.taxrules.id.TaxYearId;
import com.thiru.wealthlens.taxrules.money.Money;
import com.thiru.wealthlens.taxrules.pack.RulePackLayer;
import com.thiru.wealthlens.taxrules.pack.RulePackRef;
import java.util.List;
import java.util.Map;

/**
 * What the statute computes from the year's facts alone.
 *
 * <h2>Date-independent, and that is the whole point</h2>
 *
 * <p>{@code 23-architecture-consolidation.md} A6, defect G-5. {@code 18} introduced
 * {@code FILING_DATE}-keyed rules, {@code 21} made form selection depend on when you file, and
 * {@code C1} made late-filing interest accrue by month — so <b>the same inputs legitimately produce
 * different answers on different days</b>, while {@code 19} E3's idempotency key had no date in it.
 *
 * <p>The fix is this split. Everything here is computed from the year's facts and the law; nothing
 * here moves because a day passed. So it is cached on
 * {@code InputsFingerprint} + {@code law} + {@code exec} and <b>no date</b>, indefinitely.
 * Everything that does move with the date is in {@link FilingPosition}, which is thin and cheap and
 * recomputed freely.
 *
 * <p>That is what makes a countdown affordable: recomputing thirty-one nodes on every page load to
 * show one would not have been, and recomputing an overlay is.
 *
 * <p><b>No component of this record is a date.</b> A test asserts it structurally rather than by
 * inspection, because the property is easy to break by adding one obviously-useful field.
 *
 * @param id this computation's own identity — not an input to anything, and deliberately not in the
 *     fingerprint
 * @param taxYear the year computed
 * @param regime the regime computed under
 * @param lawAndExec the pack layers that can change a number. {@code content} and {@code forms} are
 *     excluded on purpose, which is why seven editorial revisions to the claim catalogue invalidate
 *     not one cached figure (G-11)
 * @param inputs the fingerprint of what this was computed from — the other half of the cache key
 * @param trace the whole explanation; the trace <b>is</b> the result ({@code D-VIEWFROMTRACE}), so
 *     it travels with the answer rather than being reconstructible from it
 * @param totalIncome total income, after every deduction and set-off
 * @param taxByBasis tax split by the basis it was charged on — slab and each special rate apart,
 *     because the rebate reads one and not the other
 * @param netPayableBeforeFilingEffects the bill the statute computes before anything that depends
 *     on when the return is furnished
 */
public record TaxComputation(
        ComputationId id,
        TaxYearId taxYear,
        RegimeCode regime,
        Map<RulePackLayer, RulePackRef> lawAndExec,
        InputsFingerprint inputs,
        List<TraceLine> trace,
        Money totalIncome,
        TaxByBasis taxByBasis,
        Money netPayableBeforeFilingEffects) {

    public TaxComputation {
        requirePresent(id, "id");
        requirePresent(taxYear, "taxYear");
        requirePresent(regime, "regime");
        requirePresent(lawAndExec, "lawAndExec");
        requirePresent(inputs, "inputs");
        requirePresent(trace, "trace");
        requirePresent(totalIncome, "totalIncome");
        requirePresent(taxByBasis, "taxByBasis");
        requirePresent(netPayableBeforeFilingEffects, "netPayableBeforeFilingEffects");

        if (lawAndExec.isEmpty()) {
            throw new IllegalArgumentException(
                    "TaxComputation.lawAndExec may not be empty; without the pack versions this"
                            + " answer cannot be reproduced, which is the point of recording it");
        }
        if (lawAndExec.keySet().stream().anyMatch(layer -> !layer.affectsComputation())) {
            throw new IllegalArgumentException(
                    "TaxComputation.lawAndExec holds " + lawAndExec.keySet() + ", but only layers"
                            + " that affect the computation belong in the cache key -- including an"
                            + " editorial layer would invalidate every cached figure on a wording"
                            + " change (G-11)");
        }
        lawAndExec = Map.copyOf(lawAndExec);
        trace = List.copyOf(trace);
    }

    private static void requirePresent(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException("TaxComputation." + name + " may not be null");
        }
    }

    /** The short, shareable reference for this computation. */
    public ComputationReceipt receipt() {
        return ComputationReceipt.of(id, taxYear);
    }

    /** Every figure is addressable by rule id — {@code M5.1}. */
    public List<TraceLine> linesFor(com.thiru.wealthlens.taxrules.id.RuleId rule) {
        return trace.stream().filter(line -> line.rule().equals(rule)).toList();
    }
}
