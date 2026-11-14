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

    /** Each code, with the facts its reason carries. */
    public static final Map<ReasonCode, Set<String>> FACTS = Map.of(
            REGIME_DISALLOWS, Set.of("regime"),
            REGIME_EXCLUDES_ROW, Set.of("regime"));

    private ReasonCodes() {
    }
}
