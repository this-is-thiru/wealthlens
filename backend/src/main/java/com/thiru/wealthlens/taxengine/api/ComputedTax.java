package com.thiru.wealthlens.taxengine.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.rules.filing.ComparedTotal;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * One computation's figures and how they were reached, in four groups (W-333, D-772; {@code CLAUDE.md} §4): what the run was,
 * its money, what is owed beyond the bill, and its workings. Each group is unwrapped in JSON, so the API's response and the stored
 * {@code resultJson} carry the same fields and values as before the split, and one stored before it still reads
 * ({@code ComputedTaxJsonTest}); the fields now come in group order.
 *
 * @param provenance what the run was
 * @param figures the money it produced
 * @param dues what is owed or added beyond the bill
 * @param workings each step, bucket and notice that reached it
 */
public record ComputedTax(@JsonUnwrapped Provenance provenance, @JsonUnwrapped Figures figures, @JsonUnwrapped Dues dues,
        @JsonUnwrapped Workings workings) {

    /**
     * What the run was.
     *
     * @param computationId the run
     * @param receipt what a person can quote to find it again
     * @param taxYear the year's label
     * @param regime the regime's code
     * @param inputsFingerprint what identifies the inputs
     * @param lawAndExec the rule pack's layer versions
     */
    public record Provenance(String computationId, String receipt, String taxYear, String regime, String inputsFingerprint,
            List<String> lawAndExec) {
    }

    /**
     * The money the run produced.
     *
     * @param totalIncome total income, rounded
     * @param bill the tax, surcharge and cess before anything already paid
     * @param credits tax already paid for the year
     * @param finalAmount the bill less credits, with interest and fees, rounded: above nil payable, below nil refundable
     * @param chapterEight the Chapter VIII deductions allowed in all, after s.122(2)'s cap: what a certificate's row 11 is compared with (W-130b)
     * @param otherHeads the house-property head (a loss negative) and the other-sources head, before set-off: what an employee
     *     reports to the employer as row 8, other-head income less a house-property loss (W-130d)
     */
    public record Figures(Money totalIncome, Money bill, Money credits, Money finalAmount, Money chapterEight, Money otherHeads) {

        /**
         * One of the totals a certificate is compared with, by its name in the layout (W-130a): {@code BILL}.
         *
         * @throws IllegalArgumentException for a name the layout's closed list does not hold
         */
        public Money total(String name) {
            return switch (ComparedTotal.valueOf(name)) {
                case BILL -> bill;
                case CHAPTER_VIII -> chapterEight;
                case OTHER_HEADS -> otherHeads;
            };
        }
    }

    /**
     * What is owed, or added, beyond the bill.
     *
     * @param advanceTax whether advance tax is payable and what each instalment still asks (W-089c)
     * @param interestAndFees each interest and fee on the return, by the row that charges it (W-112)
     * @param interestOnRefund the interest the Government adds to a refund, by the row that adds it; never in the amount
     *     payable (W-112b C4a)
     */
    public record Dues(AdvanceTax advanceTax, List<Owed> interestAndFees, List<Owed> interestOnRefund) {
    }

    /**
     * How the run reached its figures.
     *
     * @param trace each step, in order
     * @param capitalGainsBuckets what was decided for each bucket (D-410)
     * @param notices what the engine asks of the person, each on the step it is about (W-119c follow-up): a request, never
     *     a figure; none where a computation stored before it was read back
     */
    public record Workings(List<Step> trace, List<Bucket> capitalGainsBuckets, List<Notice> notices) {

        public Workings {
            notices = notices == null ? List.of() : List.copyOf(notices);
        }
    }

    /**
     * Something the engine asks of the person.
     *
     * @param sequence the step it is on
     * @param severity how much it matters, as the engine's {@code Severity} names it
     * @param message the request in words
     * @param relatedRules the rules it is about
     */
    public record Notice(int sequence, String severity, String message, List<String> relatedRules) {

        public Notice {
            relatedRules = List.copyOf(relatedRules);
        }
    }

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
