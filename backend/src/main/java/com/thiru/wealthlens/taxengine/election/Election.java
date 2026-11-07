package com.thiru.wealthlens.taxengine.election;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import java.util.List;
import java.util.Optional;

/**
 * A choice the law leaves to the assessee and that changes the tax ({@code DR 15} C2; D-220).
 *
 * <p>Such choices were each handled ad hoc, or fixed by a declared default where the law is silent ({@code DR 15}
 * defect D-7). A variant only describes its choice — the provision that grants it, the choices, the one the assessee
 * stated, and how a choice enters a computation; {@link ElectionResolver} prices them, the same way for every variant.
 * The family grows one variant at a time (WI rule B2).
 *
 * @param <T> what is chosen
 */
public sealed interface Election<T> permits HouseElection, RegimeElection, SetOffOrderElection {

    /**
     * The rule a choice's priced line cites: what the choice changes, so the figure is addressable by it.
     *
     * @param rules the election's view of the rules: its {@link #node()}'s, so it reads a row only through a set that
     *     node declares, chosen as the node chooses (D-255)
     */
    RuleId basis(T choice, NodeContext rules);

    /** The node whose computation the choice changes; the alternatives' lines are placed under it. */
    NodeId node();

    /** Every choice open to the assessee, the declared default first — so a tie goes to it; {@code rules} as for basis. */
    List<T> candidates(NodeContext rules);

    /** The choice the assessee stated, if any: applied as stated, not priced against the others. */
    Optional<T> stated(EngineInputs inputs);

    /** The inputs with {@code choice} made. */
    EngineInputs choosing(T choice, EngineInputs inputs);

    /** The regime a choice is computed under — the one being computed, unless the choice is the regime (D-222). */
    RegimeSpec regimeFor(T choice, RegimeSpec computing, RulePack pack);

    /** The choice in words, for its line. */
    String describe(T choice, RulePack pack);
}
