package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.input.RentDeclaration;
import com.thiru.wealthlens.taxengine.input.Tenancy;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * s.134 — rent paid for one's own residence without HRA — for {@link ChapterEightNode} (S2; S4 r.65; D-141). Claimed
 * by the Form No. 31 declaration on the tenancies' rent; the least of rent over 10% of total income, ₹5000 for each
 * month of stay, and 25% of total income, where total income is before this deduction (s.134(5)) — so it runs last,
 * on what the other deductions leave of gross total income. A nil line names the regime or the first s.134(4) bar.
 */
final class RentPaidDeduction {

    static final Set<RuleSetId> RULES = Set.of(RuleSetIds.RS_DED_RENT_PAID);

    private static final BaseId RENT_PAID = new BaseId("RENT_PAID");
    private static final BaseId TOTAL_INCOME = new BaseId("TOTAL_INCOME");
    private static final BaseId MONTH = new BaseId("MONTH");

    private RentPaidDeduction() {
    }

    /** The line, numbered {@code sequence}, on total income before this deduction; none without a declaration. */
    static Optional<TraceLine> line(NodeContext ctx, Money totalIncomeBefore, int sequence) {
        Optional<RentDeclaration> claimed = ctx.inputs().entered().rentDeclaration();
        if (claimed.isEmpty()) {
            return Optional.empty();
        }
        if (!(ctx.inputs().assessee() instanceof Individual)) {
            throw new IllegalArgumentException("s.134 is claimed for the assessee's own residence; a Hindu undivided"
                    + " family's claim is not built (D-141)");
        }
        RentDeclaration declaration = claimed.get();
        requireMonthsOccupied(ctx, declaration);
        Money rent = rented(ctx).stream().map(Tenancy::rentPaid).reduce(Money.ZERO, Money::plus);
        RuleVersion version = TemporallyApportionedCap.governingVersion(ctx.ruleOf(RuleSetIds.RS_DED_RENT_PAID),
                ctx.inputs().temporal());
        Optional<String> bar = bar(ctx, version, declaration);
        if (bar.isPresent()) {
            return Optional.of(line(ctx, sequence, version, rent, Money.ZERO, bar.get()));
        }
        CapResult allowed = TemporallyApportionedCap.apply(ctx.ruleOf(RuleSetIds.RS_DED_RENT_PAID), ctx.inputs().temporal(),
                new CapContext(Map.of(RENT_PAID, rent, TOTAL_INCOME, totalIncomeBefore), Map.of(),
                        Map.of(MONTH, declaration.monthsOfStay())));
        return Optional.of(line(ctx, sequence, version, rent, allowed.value(), allowed.limitedBy()
                + ", on total income before this deduction " + totalIncomeBefore));
    }

    /** Whether a rent declaration is entered at all. */
    static boolean entered(NodeContext ctx) {
        return ctx.inputs().entered().rentDeclaration().isPresent();
    }

    /** The regime, then s.134(4) in its order: a home owned at the place, an occupied home elsewhere, HRA. */
    private static Optional<String> bar(NodeContext ctx, RuleVersion version, RentDeclaration declaration) {
        if (!ctx.inRegime(ctx.ruleIdOf(RuleSetIds.RS_DED_RENT_PAID), version).applies()) {
            return Optional.of(ctx.inRegime(ctx.ruleIdOf(RuleSetIds.RS_DED_RENT_PAID), version).whyNot());
        }
        if (declaration.ownsHomeWhereResidesOrWorks()) {
            return Optional.of("a residential accommodation owned at the place of residence or work (s.134(4)(a)(i))");
        }
        if (declaration.ownsOccupiedHomeElsewhere()) {
            return Optional.of("an occupied residential accommodation owned elsewhere (s.134(4)(a)(ii))");
        }
        boolean receivesHra = ctx.get(NodeIds.INPUTS_COMPONENTS, ComponentSet.class).in(HeadCode.SALARIES).stream()
                .anyMatch(line -> line.code().equals(ComponentCodes.HRA) && line.amount().isGreaterThan(Money.ZERO));
        if (receivesHra) {
            return Optional.of("house rent allowance received, income in Schedule III Sl. 11 (s.134(4)(b))");
        }
        return Optional.empty();
    }

    /** The declared months of stay may not exceed the calendar months the tenancies occupy in the tax year (C2a). */
    private static void requireMonthsOccupied(NodeContext ctx, RentDeclaration declaration) {
        TaxYearId year = ctx.inputs().taxYear();
        Set<YearMonth> occupied = new HashSet<>();
        for (Tenancy tenancy : rented(ctx)) {
            if (!tenancy.occupied().overlapsTaxYear(year)) {
                continue;
            }
            LocalDate from = later(tenancy.occupied().from(), year.startsOn());
            LocalDate to = tenancy.occupied().to().map(end -> earlier(end, year.endsOn())).orElse(year.endsOn());
            for (YearMonth month = YearMonth.from(from); !month.isAfter(YearMonth.from(to)); month = month.plusMonths(1)) {
                occupied.add(month);
            }
        }
        if (declaration.monthsOfStay() > occupied.size()) {
            throw new IllegalArgumentException("The rent declaration states " + declaration.monthsOfStay()
                    + " months of stay, but the rented tenancies occupy " + occupied.size() + " calendar months of "
                    + year.label() + " (D-141)");
        }
    }

    private static LocalDate later(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate earlier(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static TraceLine line(
            NodeContext ctx, int sequence, RuleVersion version, Money rent, Money allowed, String limitedBy) {
        return TraceLine.of(sequence, NodeIds.CHAPTERVIII, ctx.ruleIdOf(RuleSetIds.RS_DED_RENT_PAID), StepKind.DEDUCTION,
                        MoneyValue.of(rent), MoneyValue.of(allowed), version.provenance())
                .withLimitedBy(limitedBy);
    }

    /** The tenancies that are rented: a home the assessee owns pays no rent. */
    private static List<Tenancy> rented(NodeContext ctx) {
        return ctx.inputs().entered().tenancies().stream().filter(tenancy -> !tenancy.ownedByAssessee()).toList();
    }
}
