package com.thiru.wealthlens.taxengine.api;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.List;

/**
 * One computation's figures and how they were reached.
 *
 * @param computationId the run
 * @param receipt what a person can quote to find it again
 * @param taxYear the year's label
 * @param regime the regime's code
 * @param totalIncome total income, rounded
 * @param bill the tax, surcharge and cess before anything already paid
 * @param credits tax already paid for the year
 * @param finalAmount the bill less credits, rounded: above nil payable, below nil refundable
 * @param inputsFingerprint what identifies the inputs
 * @param lawAndExec the rule pack's layer versions
 * @param trace each step, in order
 * @param capitalGainsBuckets what was decided for each bucket (D-410)
 */
public record ComputedTax(String computationId, String receipt, String taxYear, String regime, Money totalIncome, Money bill,
        Money credits, Money finalAmount, String inputsFingerprint, List<String> lawAndExec, List<Step> trace,
        List<Bucket> capitalGainsBuckets) {

    /**
     * One capital-gains bucket's outcome. {@code leftOutBy} is null where the head took it; the term's three are null where not.
     *
     * @param facts the bucket
     * @param gain the gain after charges, before any exemption
     * @param term the term decided
     * @param termRow the {@code RS-CG-TERM} row it met
     * @param monthsRead that row's holding period
     * @param leftOutBy the row that left it to another head
     */
    public record Bucket(BucketFacts facts, Money gain, String term, String termRow, Integer monthsRead, String leftOutBy) {
    }

    /** One line of the trace, in plain values. */
    public record Step(int sequence, String node, String rule, String kind, String limitedBy) {
    }
}
