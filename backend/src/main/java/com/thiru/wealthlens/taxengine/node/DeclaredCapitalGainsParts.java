package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Term;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsParts;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;

/**
 * The capital-gains parts as {@code CG-PARTS} declares them for the year (D-309): the term of a part's loss, and whether
 * a long-term loss may be set off against it (S2 s.108(2)(b), s.111(1)(a)(ii); 1961 s.70(3), s.74(1)). Both the year's
 * own set-off and the carried one ask here, so the restriction is the row's for both.
 */
final class DeclaredCapitalGainsParts {

    static final RuleSetId PARTS = RuleSetIds.RS_CG_PARTS;

    private final RuleId row;

    private final CapitalGainsParts parts;

    private DeclaredCapitalGainsParts(RuleId row, CapitalGainsParts parts) {
        this.row = row;
        this.parts = parts;
    }

    /** The parts the year's row declares; the caller's node declares {@code RS-CG-PARTS}. */
    static DeclaredCapitalGainsParts of(NodeContext ctx) {
        GoverningRow governing = GoverningRow.of(ctx, PARTS);
        return new DeclaredCapitalGainsParts(governing.id(),
                RuleDefinitions.require(governing.version().definition(), CapitalGainsParts.class, governing.id()));
    }

    /**
     * Refuses a part holding gains that the row does not declare, or declares with a term the engine does not know: a
     * loss would later be set off against it on a basis the row has not stated.
     *
     * @throws IllegalArgumentException if so
     */
    void requireDeclared(CapitalGainsPart part) {
        collectsLongTerm(part);
    }

    /**
     * Whether {@code part} collects long-term gains, so that a loss on it is a long-term loss: the term the row declares.
     *
     * @throws IllegalArgumentException if the row does not declare the part, or declares a term the engine does not know
     */
    boolean collectsLongTerm(CapitalGainsPart part) {
        String term = declared(part).term();
        if (term.equals(Term.LONG_TERM.name())) {
            return true;
        }
        if (term.equals(Term.SHORT_TERM.name())) {
            return false;
        }
        throw new IllegalArgumentException(row + " declares " + part + " with the term " + term + ", which is neither "
                + Term.SHORT_TERM + " nor " + Term.LONG_TERM);
    }

    /**
     * Whether a long-term loss may be set off against {@code part}.
     *
     * @throws IllegalArgumentException if the row does not declare the part
     */
    boolean canAbsorbLongTermLoss(CapitalGainsPart part) {
        return declared(part).canAbsorbLongTermLoss();
    }

    private CapitalGainsParts.Declared declared(CapitalGainsPart part) {
        return parts.of(part).orElseThrow(() -> new IllegalArgumentException(row + " declares no part " + part
                + ", so neither its term nor the losses it may absorb are known"));
    }
}
