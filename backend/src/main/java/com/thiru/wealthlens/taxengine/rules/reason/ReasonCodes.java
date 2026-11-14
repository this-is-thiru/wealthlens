package com.thiru.wealthlens.taxengine.rules.reason;

import com.thiru.wealthlens.taxengine.rules.id.ReasonCode;
import java.util.Map;
import java.util.Set;

/**
 * Every reason code the engine publishes, with the names of the facts its reason carries (W-327a, D-678). The content
 * must word each code, and a code's sentence may fill only these facts; the pack refuses anything else.
 */
public final class ReasonCodes {

    /** The regime forbids the rule by a disallowance set (D-165). */
    public static final ReasonCode REGIME_DISALLOWS = new ReasonCode("REGIME_DISALLOWS");

    /** The rule's row is written for other regimes (D-166). */
    public static final ReasonCode REGIME_EXCLUDES_ROW = new ReasonCode("REGIME_EXCLUDES_ROW");

    /** A {@code Fixed} cap: the amount itself (W-327a1, D-679). */
    public static final ReasonCode CAP_FIXED = new ReasonCode("CAP_FIXED");

    /** A {@code PercentOfBase} cap: the share taken. */
    public static final ReasonCode CAP_SHARE = new ReasonCode("CAP_SHARE");

    /** An {@code AmountInExcess} cap: the threshold's figure, above which the amount counts. */
    public static final ReasonCode CAP_IN_EXCESS = new ReasonCode("CAP_IN_EXCESS");

    /** An {@code AggregateWithCeiling} whose ceiling did not bind. */
    public static final ReasonCode CAP_ACTUAL_AMOUNTS = new ReasonCode("CAP_ACTUAL_AMOUNTS");

    /** An {@code AggregateWithCeiling} whose ceiling bound: the ceiling. */
    public static final ReasonCode CAP_AGGREGATE_CEILING = new ReasonCode("CAP_AGGREGATE_CEILING");

    /** Each code, with the facts its reason carries. */
    public static final Map<ReasonCode, Set<String>> FACTS = Map.of(
            REGIME_DISALLOWS, Set.of("regime"),
            REGIME_EXCLUDES_ROW, Set.of("regime"),
            CAP_FIXED, Set.of("amount"),
            CAP_SHARE, Set.of("share"),
            CAP_IN_EXCESS, Set.of("threshold"),
            CAP_ACTUAL_AMOUNTS, Set.of(),
            CAP_AGGREGATE_CEILING, Set.of("ceiling"));

    private ReasonCodes() {
    }
}
