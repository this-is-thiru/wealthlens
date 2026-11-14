package com.thiru.wealthlens.taxengine.api;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
 * @param advanceTax whether advance tax is payable and what each instalment still asks (W-089c)
 * @param interestAndFees each interest and fee on the return, by the row that charges it (W-112)
 * @param interestOnRefund the interest the Government adds to a refund, by the row that adds it; never in the amount
 *     payable (W-112b C4a)
 * @param finalAmount the bill less credits, with interest and fees, rounded: above nil payable, below nil refundable
 * @param inputsFingerprint what identifies the inputs
 * @param lawAndExec the rule pack's layer versions
 * @param trace each step, in order
 * @param capitalGainsBuckets what was decided for each bucket (D-410)
 */
public record ComputedTax(String computationId, String receipt, String taxYear, String regime, Money totalIncome, Money bill,
        Money credits, AdvanceTax advanceTax, List<Owed> interestAndFees, List<Owed> interestOnRefund, Money finalAmount, String inputsFingerprint, List<String> lawAndExec, List<Step> trace,
        List<Bucket> capitalGainsBuckets) {

    /**
     * One capital-gains bucket's outcome. {@code leftOutBy} is null where the head took it; the term's three are null where not.
     *
     * @param facts the bucket
     * @param gain the gain after charges, before any exemption
     * @param charges each charge and its limb
     * @param term the term decided
     * @param termRow the {@code RS-CG-TERM} row it met
     * @param monthsRead that row's holding period
     * @param leftOutBy the row that left it to another head
     */
    public record Bucket(BucketFacts facts, Money gain, List<Charge> charges, String term, String termRow, Integer monthsRead,
            String leftOutBy) {

        /** One charge and where the rows put it: cost, transfer expenditure or not deducted. */
        public record Charge(String side, String code, Money amount, String limb) {
        }
    }

    /**
     * The year's advance tax (2025 ss.403 – 408; 1961 ss.207 – 211).
     *
     * @param liable whether it is payable at all
     * @param amount the bill less tax at source
     * @param instalments each date, what is due by then, paid by then and still owed; empty where not liable
     * @param reliefPaidBy where income s.425(4) / s.234C relieves arose after an instalment date, the day its tax must be
     *     paid by for the relief to hold (W-114 C4a, D-665); empty otherwise
     */
    public record AdvanceTax(boolean liable, Money amount, List<Instalment> instalments, Optional<LocalDate> reliefPaidBy) {

        /** One instalment. */
        public record Instalment(LocalDate dueOn, Money dueByThen, Money paidByThen, Money stillOwed) {
        }
    }

    /**
     * One interest or fee owed on the return.
     *
     * @param rule the row that charges it
     * @param kind {@code INTEREST} or {@code FEE}: interest grows while a person waits, a fee does not
     * @param amount the amount
     */
    public record Owed(String rule, String kind, Money amount) {
    }

    /**
     * One line of the trace, in plain values.
     *
     * @param in what the line started from, where it is money
     * @param out what the line produced, where it is money
     * @param explanation the line in plain language, from the pack's content; the rule id where it has none (W-116)
     */
    public record Step(int sequence, String node, String rule, String kind, String limitedBy, Optional<Money> in,
            Optional<Money> out, String explanation) {
    }
}
