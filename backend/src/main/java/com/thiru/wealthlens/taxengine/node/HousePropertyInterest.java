package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.HouseOccupancy;
import com.thiru.wealthlens.taxengine.input.HouseProperty;
import com.thiru.wealthlens.taxengine.input.HousingLoan;
import com.thiru.wealthlens.taxengine.input.LoanPurpose;
import com.thiru.wealthlens.taxengine.input.LoanUse;
import com.thiru.wealthlens.taxengine.input.PurposeLoanInterest;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.definition.OwnResidenceInterestLimit;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.definition.TaxYearSpan;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Interest on capital borrowed for a house, for {@link HousePropertyNode} (S2 s.22(1)(b), (c), (2)–(6) with FA 2026
 * s.38; 1961 s.24(b), s.25; {@code DR 01} R16; {@code DR 06} G1.4; D-156). A line per loan and kind of interest: the
 * year's interest and the pre-construction instalment, less what s.130 or s.131 took, nil where payable abroad
 * untaxed. A house other than an own residence deducts it whole; an own residence's interest, gathered across the
 * houses, is limited once — the houses failing the test to one figure, those passing to another, all to the
 * aggregate — and in the new tax regime only what its rows allow there is left to limit.
 */
final class HousePropertyInterest {

    static final Set<RuleSetId> RULES = Set.of(RuleSetIds.RS_HP_INTEREST, RuleSetIds.RS_HP_PRE_CONSTRUCTION,
            RuleSetIds.RS_HP_OWN_RESIDENCE_INTEREST, RuleSetIds.RS_HP_OWN_RESIDENCE_PRE_CONSTRUCTION,
            RuleSetIds.RS_HP_OWN_RESIDENCE_INTEREST_CAP);

    private static final BaseId INTEREST = new BaseId("INTEREST");

    private final NodeContext ctx;
    private final List<TraceLine> lines;
    private Money ownResidenceConditionsMet = Money.ZERO;
    private Money ownResidenceAnyOtherCase = Money.ZERO;

    /** Collects into {@code lines}, the node's own list, so the sequence numbers run on. */
    HousePropertyInterest(NodeContext ctx, List<TraceLine> lines) {
        this.ctx = ctx;
        this.lines = lines;
    }

    /**
     * S2 s.130(4), s.131(3): interest one of them deducts no other provision may. The housing loans say how much of
     * their interest went there; it must be what the residential-house loans under those sections carry, or the same
     * interest is entered twice.
     */
    static void requireChapterEightShareMatches(List<HouseProperty> properties, List<PurposeLoanInterest> purposeLoans) {
        List<HousingLoan> loans = properties.stream().flatMap(property -> property.loans().stream()).toList();
        if (loans.isEmpty()) {
            return;
        }
        Money leftOut = loans.stream().map(HousingLoan::interestDeductedUnderChapterEight).reduce(Money.ZERO,
                Money::plus);
        Money underChapterEight = purposeLoans.stream()
                .filter(loan -> loan.purpose() == LoanPurpose.RESIDENTIAL_HOUSE)
                .map(PurposeLoanInterest::interestPayable)
                .reduce(Money.ZERO, Money::plus);
        if (!leftOut.equals(underChapterEight)) {
            throw new IllegalArgumentException("The housing loans leave out " + leftOut + " of interest as deducted"
                    + " under s.130 or s.131, but the house loans entered under those sections carry "
                    + underChapterEight + "; interest deducted there may not be deducted again (S2 s.130(4), s.131(3);"
                    + " 1961 s.80EE, s.80EEA) — enter the same amount on both sides");
        }
    }

    /**
     * A house other than an own residence: its interest, deducted whole. An own residence's is only gathered, for
     * {@link #ownResidenceAllowed}; nothing is deducted for it here.
     */
    Money deductedFor(int position, HouseProperty property) {
        boolean ownResidence = property.occupancy() == HouseOccupancy.OWN_RESIDENCE;
        RuleId current = ownResidence ? ctx.ruleIdOf(RuleSetIds.RS_HP_OWN_RESIDENCE_INTEREST) : ctx.ruleIdOf(RuleSetIds.RS_HP_INTEREST);
        RuleId prior = ownResidence ? ctx.ruleIdOf(RuleSetIds.RS_HP_OWN_RESIDENCE_PRE_CONSTRUCTION) : ctx.ruleIdOf(RuleSetIds.RS_HP_PRE_CONSTRUCTION);
        Money deducted = Money.ZERO;
        for (HousingLoan loan : property.loans()) {
            Money interest = allowed(position, current, loan, yearsInterest(loan), "the year's interest");
            Money instalment = allowed(position, prior, loan, instalment(property, loan), "the pre-construction"
                    + " instalment");
            if (ownResidence && qualifiesForHigherLimit(property, loan)) {
                ownResidenceConditionsMet = ownResidenceConditionsMet.plus(interest).plus(instalment);
            } else if (ownResidence) {
                ownResidenceAnyOtherCase = ownResidenceAnyOtherCase.plus(interest).plus(instalment);
            } else {
                deducted = deducted.plus(interest).plus(instalment);
            }
        }
        return deducted;
    }

    /**
     * S2 s.22(2), (5); 1961 s.24(b) provisos: the houses failing the test limited together, then all to the aggregate
     * — sub-limit first, then the ceiling, the order {@code AggregateWithCeiling} records.
     */
    Money ownResidenceAllowed() {
        Money gathered = ownResidenceConditionsMet.plus(ownResidenceAnyOtherCase);
        if (gathered.equals(Money.ZERO)) {
            return Money.ZERO;
        }
        RuleVersion version = version(ctx.ruleIdOf(RuleSetIds.RS_HP_OWN_RESIDENCE_INTEREST_CAP));
        OwnResidenceInterestLimit limit = limit(version);
        Money allowed = Money.min(Money.min(ownResidenceAnyOtherCase, limit.anyOtherCase())
                .plus(Money.min(ownResidenceConditionsMet, limit.conditionsMet())), limit.aggregate());
        lines.add(line(ctx.ruleIdOf(RuleSetIds.RS_HP_OWN_RESIDENCE_INTEREST_CAP), version, gathered, allowed)
                .withLimitedBy("own residences: " + ownResidenceConditionsMet + " within " + limit.conditionsMet()
                        + ", " + ownResidenceAnyOtherCase + " within " + limit.anyOtherCase() + ", together within "
                        + limit.aggregate()));
        return allowed;
    }

    /** One kind of interest on one loan, through its row: nil outside the regime, else the row's share of it. */
    private Money allowed(int position, RuleId row, HousingLoan loan, Money amount, String what) {
        if (amount.equals(Money.ZERO)) {
            return Money.ZERO;
        }
        RuleVersion version = version(row);
        String where = "property " + position + ", " + what;
        if (!ctx.inRegime(row, version).applies()) {
            lines.add(line(row, version, amount, Money.ZERO)
                    .withLimitedBy(where + ": " + ctx.inRegime(row, version).whyNot()));
            return Money.ZERO;
        }
        if (loan.payableOutsideIndiaUntaxed()) {
            lines.add(line(row, version, amount, Money.ZERO).withLimitedBy(where + ": payable outside India, untaxed"
                    + " and with no agent in India (S2 s.22(6); 1961 s.25)"));
            return Money.ZERO;
        }
        // The instalment rows define a span and limit nothing; the year's-interest rows state the whole as a cap.
        Money allowed = version.cap() == null ? amount
                : TemporallyApportionedCap.apply(ctx.rule(row), ctx.inputs().temporal(),
                        CapContext.of(INTEREST, amount)).value();
        lines.add(line(row, version, amount, allowed).withLimitedBy(where));
        return allowed;
    }

    /** The year's interest, less what s.130 or s.131 deducts. */
    private static Money yearsInterest(HousingLoan loan) {
        return loan.interestPayable().minus(loan.interestDeductedUnderChapterEight());
    }

    /**
     * S2 s.22(1)(c); 1961 s.24(b) Explanation: an equal share of the pre-construction interest in the tax year of
     * acquisition and each of the span after it, nil outside it.
     */
    private Money instalment(HouseProperty property, HousingLoan loan) {
        if (loan.use() != LoanUse.ACQUISITION_OR_CONSTRUCTION
                || loan.preConstructionInterest().equals(Money.ZERO)) {
            return Money.ZERO;
        }
        RuleId row = property.occupancy() == HouseOccupancy.OWN_RESIDENCE
                ? ctx.ruleIdOf(RuleSetIds.RS_HP_OWN_RESIDENCE_PRE_CONSTRUCTION) : ctx.ruleIdOf(RuleSetIds.RS_HP_PRE_CONSTRUCTION);
        TaxYearSpan span = RuleDefinitions.require(version(row).definition(), TaxYearSpan.class, row);
        if (!span.holds(taxYearOf(acquiredOn(property)), ctx.inputs().taxYear())) {
            return Money.ZERO;
        }
        int instalments = span.succeedingYears() + 1;
        return loan.preConstructionInterest().apportioned(1, instalments);
    }

    /**
     * S2 s.22(2)(a); 1961 s.24(b) second and third provisos: borrowed to acquire or construct, on or after the
     * earliest date where the Act sets one, completed within the years after the end of the tax year of borrowing,
     * and the lender's certificate furnished.
     */
    private boolean qualifiesForHigherLimit(HouseProperty property, HousingLoan loan) {
        OwnResidenceInterestLimit limit = limit(version(ctx.ruleIdOf(RuleSetIds.RS_HP_OWN_RESIDENCE_INTEREST_CAP)));
        if (loan.use() != LoanUse.ACQUISITION_OR_CONSTRUCTION || !loan.lenderCertificateHeld()) {
            return false;
        }
        if (limit.borrowedOnOrAfter().filter(earliest -> loan.borrowedOn().isBefore(earliest)).isPresent()) {
            return false;
        }
        LocalDate completeBy = taxYearOf(loan.borrowedOn()).endsOn()
                .plusYears(limit.completionYearsAfterBorrowingYear());
        return !acquiredOn(property).isAfter(completeBy);
    }

    /** A house acquired or constructed after the tax year has no income in it; its interest is still prior-period. */
    private LocalDate acquiredOn(HouseProperty property) {
        LocalDate acquired = property.acquiredOrConstructedOn().orElseThrow();
        if (acquired.isAfter(ctx.inputs().taxYear().endsOn())) {
            throw new IllegalArgumentException("A house acquired or constructed on " + acquired + ", after the tax"
                    + " year, has no income from house property in it; its interest is pre-construction interest"
                    + " (S2 s.22(1)(c))");
        }
        return acquired;
    }

    private static TaxYearId taxYearOf(LocalDate date) {
        return TaxYearId.of(date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1);
    }

    private OwnResidenceInterestLimit limit(RuleVersion version) {
        return RuleDefinitions.require(version.definition(), OwnResidenceInterestLimit.class,
                ctx.ruleIdOf(RuleSetIds.RS_HP_OWN_RESIDENCE_INTEREST_CAP));
    }

    private RuleVersion version(RuleId row) {
        return TemporallyApportionedCap.governingVersion(ctx.rule(row), ctx.inputs().temporal());
    }

    private TraceLine line(RuleId row, RuleVersion version, Money in, Money out) {
        return TraceLine.of(lines.size() + 1, NodeIds.HOUSEPROPERTY_INCOME, row, StepKind.DEDUCTION,
                MoneyValue.of(in), MoneyValue.of(out), version.provenance());
    }
}
