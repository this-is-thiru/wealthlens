package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxrules.id.RegimeCode;
import com.thiru.wealthlens.taxrules.regime.ElectionState;
import java.util.Map;

/**
 * An immutable projection of the regime election history, at a point in time.
 *
 * <h2>This is the defect A3 was written to fix</h2>
 *
 * <p>{@code DR 23} A3, defect G-2. {@code X1.2} says {@code taxengine} is pure and cannot reach a
 * repository; {@code 17} L3 then called {@code history.of(profileId, regimeCode)} inside regime
 * resolution. Both could not be true. The resolution is this record: <b>the history is passed in,
 * not fetched</b>, and {@code eligibleRegimes} becomes a pure function of it.
 *
 * <p>That is why it is a snapshot rather than a handle. A handle would let the engine ask a
 * question whose answer depends on when it was asked, and a computation that re-reads the database
 * cannot be reproduced years later.
 *
 * @param stateByRegime where the taxpayer stands with each regime; an absent entry means
 *     {@link ElectionState#NEVER_ELECTED}, which is why {@link #stateOf} never returns null
 * @param withdrawalsUsed how many withdrawals have been spent per regime, for the protocols that
 *     allow a bounded number
 */
public record RegimeElectionSnapshot(
        Map<RegimeCode, ElectionState> stateByRegime, Map<RegimeCode, Integer> withdrawalsUsed) {

    public RegimeElectionSnapshot {
        if (stateByRegime == null || withdrawalsUsed == null) {
            throw new IllegalArgumentException(
                    "RegimeElectionSnapshot maps may not be null; pass empty maps for a taxpayer"
                            + " who has never elected anything");
        }
        stateByRegime = Map.copyOf(stateByRegime);
        withdrawalsUsed = Map.copyOf(withdrawalsUsed);
    }

    /** A taxpayer with no election history at all — a first return. */
    public static RegimeElectionSnapshot none() {
        return new RegimeElectionSnapshot(Map.of(), Map.of());
    }

    /**
     * Where the taxpayer stands with one regime.
     *
     * <p>Absent means {@link ElectionState#NEVER_ELECTED} — A3 specifies the default here rather
     * than at each call site, so that "no row" and "never elected" cannot drift apart.
     */
    public ElectionState stateOf(RegimeCode regime) {
        return stateByRegime.getOrDefault(regime, ElectionState.NEVER_ELECTED);
    }

    /** Withdrawals already spent on one regime; none recorded means none used. */
    public int withdrawalsUsedFor(RegimeCode regime) {
        return withdrawalsUsed.getOrDefault(regime, 0);
    }
}
