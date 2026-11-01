package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Term;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;

/**
 * What the engine decided for one capital-gains bucket, typed, beside the trace (W-109a, D-410): the trace's lines name no bucket
 * and hold the term in a display string. Exactly one of {@code term} and {@code leftOutBy} is present.
 *
 * @param facts the bucket
 * @param gain the consideration less the cost and the charges a row deducts, before any exemption
 * @param term the term decided, and the row and months that decided it
 * @param leftOutBy the row that left it to another head
 */
public record BucketOutcome(BucketFacts facts, Money gain, Optional<TermDecision> term, Optional<RuleId> leftOutBy) {

    public BucketOutcome {
        if (facts == null || gain == null || term == null || leftOutBy == null || term.isPresent() == leftOutBy.isPresent()) {
            throw new IllegalArgumentException("A bucket's outcome names its facts and gain, and either its term or the row"
                    + " that left it out, not both");
        }
    }

    /**
     * A term and how it was decided.
     *
     * @param term long or short
     * @param row the {@code RS-CG-TERM} row the bucket met
     * @param months the row's holding period, as read
     */
    public record TermDecision(Term term, RuleId row, int months) {
    }
}
