package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Term;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.List;
import java.util.Optional;

/**
 * What the engine decided for one capital-gains bucket, typed, beside the trace (W-109a, D-410): the trace's lines name no bucket
 * and hold the term in a display string. Exactly one of {@code term} and {@code leftOutBy} is present.
 *
 * @param facts the bucket
 * @param gain the consideration less the cost and the charges a row deducts, before any exemption
 * @param charges each charge and where the rows put it
 * @param term the term decided, and the row and months that decided it
 * @param leftOutBy the row that left it to another head
 * @param consideration the full value of the consideration (W-093a, D-658)
 * @param costOfAcquisition the cost, with the charges a row classes as cost of acquisition
 * @param transferExpenditure the charges a row classes as expenditure on the transfer — so the consideration less
 *     these two is the gain
 * @param part the capital-gains part the bucket's gain went to; empty for a bucket left out
 */
public record BucketOutcome(BucketFacts facts, Money gain, List<ChargeOutcome> charges, Optional<TermDecision> term,
        Optional<RuleId> leftOutBy, Money consideration, Money costOfAcquisition, Money transferExpenditure,
        Optional<CapitalGainsPart> part) {

    public BucketOutcome {
        if (consideration == null || costOfAcquisition == null || transferExpenditure == null || part == null) {
            throw new IllegalArgumentException("A bucket's outcome states its consideration, cost and expenditure, and its"
                    + " part or none");
        }
        if (facts == null || gain == null || charges == null || term == null || leftOutBy == null || term.isPresent() == leftOutBy.isPresent()) {
            throw new IllegalArgumentException("A bucket's outcome names its facts and gain, and either its term or the row"
                    + " that left it out, not both");
        }
    }

    /**
     * One charge and where it stands in computing the gain.
     *
     * @param side acquisition or transfer
     * @param code its code
     * @param amount what it was
     * @param limb the {@code RS-CG-CHARGE} row's limb, by name
     */
    public record ChargeOutcome(String side, String code, Money amount, String limb) {
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
