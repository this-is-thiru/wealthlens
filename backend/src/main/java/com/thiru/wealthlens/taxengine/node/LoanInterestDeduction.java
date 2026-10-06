package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.EducationLoanInterest;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.input.LoanPurpose;
import com.thiru.wealthlens.taxengine.input.PurposeLoanInterest;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.definition.SanctionedLoanLimit;
import com.thiru.wealthlens.taxengine.rules.definition.TaxYearSpan;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * s.129–132 — interest on an education loan, a first-home loan and an electric-vehicle loan — for
 * {@link ChapterEightNode} (S2; D-139). A line per loan: the whole education-loan interest within its run of years;
 * a house or vehicle loan's interest within its section's limit where every condition of its loan holds, else nil
 * with the first that fails. A house loan is tried under s.130 first, under s.131 only where s.130 fails (s.131(1)).
 */
final class LoanInterestDeduction {

    static final Set<RuleId> RULES = Set.of(RuleIds.DED_EDUCATION_LOAN, RuleIds.DED_HOUSE_LOAN_EARLIER,
            RuleIds.DED_HOUSE_LOAN, RuleIds.DED_ELECTRIC_VEHICLE_LOAN);

    private LoanInterestDeduction() {
    }

    /** The lines, numbered from {@code firstSequence}; none where no loan is entered. */
    static List<TraceLine> lines(NodeContext ctx, int firstSequence) {
        List<EducationLoanInterest> education = ctx.inputs().entered().educationLoanInterest();
        List<PurposeLoanInterest> loans = ctx.inputs().entered().purposeLoanInterest();
        if (education.isEmpty() && loans.isEmpty()) {
            return List.of();
        }
        if (!(ctx.inputs().assessee() instanceof Individual)) {
            throw new IllegalArgumentException("s.129–132 are an individual's deductions (D-139)");
        }
        List<TraceLine> lines = new ArrayList<>();
        for (EducationLoanInterest loan : education) {
            lines.add(education(ctx, loan, firstSequence + lines.size()));
        }
        for (PurposeLoanInterest loan : loans) {
            lines.add(purpose(ctx, loan, firstSequence + lines.size()));
        }
        return lines;
    }

    /** s.129: the whole interest, in the initial tax year and the span of years after it. */
    private static TraceLine education(NodeContext ctx, EducationLoanInterest loan, int sequence) {
        RuleVersion version = version(ctx, RuleIds.DED_EDUCATION_LOAN);
        Money interest = loan.interestPaid();
        if (!applies(ctx, version)) {
            return line(sequence, RuleIds.DED_EDUCATION_LOAN, version, interest, Money.ZERO, notInRegime(ctx));
        }
        TaxYearSpan span = RuleDefinitions.require(version.definition(), TaxYearSpan.class,
                RuleIds.DED_EDUCATION_LOAN);
        if (!span.holds(loan.initialTaxYear(), ctx.inputs().taxYear())) {
            return line(sequence, RuleIds.DED_EDUCATION_LOAN, version, interest, Money.ZERO, "outside the initial"
                    + " tax year " + loan.initialTaxYear().label() + " and the " + span.succeedingYears() + " after it");
        }
        return line(sequence, RuleIds.DED_EDUCATION_LOAN, version, interest, interest, "the interest paid");
    }

    /** s.130–132: a house loan under s.130, else s.131; a vehicle loan under s.132. */
    private static TraceLine purpose(NodeContext ctx, PurposeLoanInterest loan, int sequence) {
        if (loan.purpose() == LoanPurpose.ELECTRIC_VEHICLE) {
            return limited(ctx, loan, RuleIds.DED_ELECTRIC_VEHICLE_LOAN, sequence);
        }
        Optional<String> earlier = failure(RuleDefinitions.require(version(ctx, RuleIds.DED_HOUSE_LOAN_EARLIER).definition(), SanctionedLoanLimit.class,
                RuleIds.DED_HOUSE_LOAN_EARLIER), loan);
        return limited(ctx, loan, earlier.isEmpty() ? RuleIds.DED_HOUSE_LOAN_EARLIER : RuleIds.DED_HOUSE_LOAN,
                sequence);
    }

    private static TraceLine limited(NodeContext ctx, PurposeLoanInterest loan, RuleId row, int sequence) {
        RuleVersion version = version(ctx, row);
        Money interest = loan.interestPayable();
        if (!applies(ctx, version)) {
            return line(sequence, row, version, interest, Money.ZERO, notInRegime(ctx));
        }
        SanctionedLoanLimit limit = RuleDefinitions.require(version.definition(), SanctionedLoanLimit.class, row);
        Optional<String> failed = failure(limit, loan);
        if (failed.isPresent()) {
            return line(sequence, row, version, interest, Money.ZERO, failed.get());
        }
        Money allowed = Money.min(interest, limit.limit());
        return line(sequence, row, version, interest, allowed,
                allowed.isLessThan(interest) ? "the limit " + limit.limit() : "the interest payable");
    }

    /** The first of the loan's conditions that fails, if any. */
    private static Optional<String> failure(SanctionedLoanLimit limit, PurposeLoanInterest loan) {
        if (loan.sanctionedOn().isBefore(limit.sanctionedFrom()) || loan.sanctionedOn().isAfter(limit.sanctionedTo())) {
            return Optional.of("sanctioned " + loan.sanctionedOn() + ", outside " + limit.sanctionedFrom() + " to "
                    + limit.sanctionedTo());
        }
        if (limit.maxAmountSanctioned().filter(max -> loan.amountSanctioned().isGreaterThan(max)).isPresent()) {
            return Optional.of("loan sanctioned " + loan.amountSanctioned() + ", above "
                    + limit.maxAmountSanctioned().get());
        }
        Optional<String> value = withinValue("value of the house", limit.maxPropertyValue(), loan.propertyValue());
        if (value.isPresent()) {
            return value;
        }
        Optional<String> stampDuty = withinValue("stamp duty value", limit.maxStampDutyValue(), loan.stampDutyValue());
        if (stampDuty.isPresent()) {
            return stampDuty;
        }
        if (limit.requiresNoHouseOwned() && loan.ownedResidentialHouseOnSanction()) {
            return Optional.of("a residential house was owned on the date of sanction");
        }
        return Optional.empty();
    }

    private static Optional<String> withinValue(String what, Optional<Money> max, Optional<Money> entered) {
        if (max.isEmpty()) {
            return Optional.empty();
        }
        if (entered.isEmpty()) {
            return Optional.of("the " + what + " was not entered");
        }
        return entered.get().isGreaterThan(max.get())
                ? Optional.of("the " + what + " " + entered.get() + ", above " + max.get())
                : Optional.empty();
    }

    private static boolean applies(NodeContext ctx, RuleVersion version) {
        return version.regimes().appliesIn(ctx.regime().code());
    }

    private static String notInRegime(NodeContext ctx) {
        return "not in the " + ctx.regime().code().value() + " regime";
    }

    private static TraceLine line(int sequence, RuleId row, RuleVersion version, Money in, Money out,
            String limitedBy) {
        return TraceLine.of(sequence, NodeIds.CHAPTERVIII, row, StepKind.DEDUCTION, MoneyValue.of(in),
                        MoneyValue.of(out), version.provenance())
                .withLimitedBy(limitedBy);
    }

    private static RuleVersion version(NodeContext ctx, RuleId row) {
        return TemporallyApportionedCap.governingVersion(ctx.rule(row), ctx.inputs().temporal());
    }
}
