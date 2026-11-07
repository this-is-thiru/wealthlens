package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.EmployerExpenseBenefit;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Holidays, credit-card and club bills, telephones and other benefits at arm's-length cost, for {@link PerquisiteNode}
 * (S4 r.15(5) Table IV Sl. 2, 5, 6, 9; Rules 1962 r.3(7)(ii), (v), (vi), (ix); D-119).
 *
 * <p>Each is its amount less the recovery, on the row of {@code RS-PERQ-EXPENSE} its kind selects; a bill a row of
 * {@code RS-PERQ-EXPENSE-EXCLUDED} reaches — by its kind and whether it was wholly for work, with the employer's
 * details and certificate, and for a club the uniform facilities — is nil there instead; one neither set reaches is
 * refused (W-154d), as is a fact no row of its kind reads (W-158a). Which club exclusions a year has is which rows it holds (OQ-12).
 */
final class ExpenseBenefitPerquisite {

    static final Set<RuleSetId> RULES = Set.of(RuleSetIds.RS_PERQ_EXPENSE, RuleSetIds.RS_PERQ_EXPENSE_EXCLUDED);

    private static final BaseId BENEFIT_VALUE = new BaseId("BENEFIT_VALUE");

    /** What the valuing and exclusion rows are chosen by (D-325, D-327). */
    private static final ItemFactId KIND = new ItemFactId("expense.kind");
    private static final ItemFactId WHOLLY_FOR_WORK = new ItemFactId("expense.wholly.for.work");
    private static final ItemFactId JOURNEYS_AND_CERTIFICATE_KEPT = new ItemFactId(
            "expense.journeys.and.certificate.kept");
    private static final ItemFactId HEALTH_CLUB_UNIFORM_FOR_ALL = new ItemFactId(
            "expense.health.club.uniform.for.all");
    /** A higher official use claimed on the employer's records — a vehicle's claim, read by no expense row today. */
    private static final ItemFactId HIGHER_OFFICIAL_USE = new ItemFactId("expense.higher.official.use");

    /**
     * The facts a benefit may carry or not; one no row of its kind reads is refused (W-158a, D-357). A higher official
     * use needs the records kept, and the records come with work, so each is checked before the fact it needs.
     */
    private static final List<ItemFactId> OPTIONAL = List.of(HIGHER_OFFICIAL_USE, JOURNEYS_AND_CERTIFICATE_KEPT,
            WHOLLY_FOR_WORK, HEALTH_CLUB_UNIFORM_FOR_ALL);

    private ExpenseBenefitPerquisite() {
    }

    /** One line per entry, numbered from {@code firstSequence}. */
    static List<TraceLine> lines(NodeContext ctx, int firstSequence) {
        List<TraceLine> lines = new ArrayList<>();
        List<EmployerExpenseBenefit> benefits = ctx.inputs().entered().expenseBenefits();
        for (int i = 0; i < benefits.size(); i++) {
            EmployerExpenseBenefit benefit = benefits.get(i);
            PerquisiteNode.requireEnteredEmployment(ctx, benefit.employment(), "expense benefit");
            int sequence = firstSequence + lines.size();
            int position = i + 1;
            ItemFacts facts = factsOf(benefit);
            ReadFacts.requireRead(ctx, RULES, facts, OPTIONAL, "Expense benefit " + position);
            lines.add(ctx.optionalRuleOf(RuleSetIds.RS_PERQ_EXPENSE_EXCLUDED, facts)
                    .map(row -> nil(ctx, row, benefit, sequence))
                    .or(() -> ctx.optionalRuleOf(RuleSetIds.RS_PERQ_EXPENSE, facts)
                            .map(row -> valued(ctx, row, benefit, sequence)))
                    .orElseThrow(() -> unvalued(ctx, position, facts)));
        }
        return lines;
    }

    /** The benefit's facts as the rows' conditions read them. */
    private static ItemFacts factsOf(EmployerExpenseBenefit benefit) {
        return new ItemFacts(Map.of(KIND, benefit.kind().name(),
                WHOLLY_FOR_WORK, Boolean.toString(benefit.whollyForWork()),
                JOURNEYS_AND_CERTIFICATE_KEPT, Boolean.toString(benefit.officialUse().journeysAndCertificateKept()),
                HEALTH_CLUB_UNIFORM_FOR_ALL, Boolean.toString(benefit.healthClubUniformForAll()),
                HIGHER_OFFICIAL_USE, Boolean.toString(benefit.officialUse().higherOfficialUse().isPresent())),
                Map.of(), Map.of());
    }

    /** A benefit no row of the year values or excludes. */
    private static IllegalArgumentException unvalued(NodeContext ctx, int position, ItemFacts facts) {
        String described = facts.codes().entrySet().stream()
                .map(fact -> fact.getKey() + "=" + fact.getValue())
                .sorted()
                .collect(Collectors.joining(", ", "{", "}"));
        return new IllegalArgumentException("Expense benefit " + position + ": no row of "
                + RuleSetIds.RS_PERQ_EXPENSE + " values it in TY " + ctx.inputs().taxYear().label()
                + " — its facts " + described);
    }

    private static TraceLine valued(NodeContext ctx, RuleRow row, EmployerExpenseBenefit benefit, int sequence) {
        Money net = benefit.amount().minus(benefit.recovered()).floorAtZero();
        Money value = TemporallyApportionedCap.apply(row, ctx.inputs().temporal(),
                CapContext.of(BENEFIT_VALUE, net)).value();
        return ctx.traceLine(sequence, row.id(), StepKind.INCOME, benefit.amount(), value)
                .withLimitedBy(benefit.kind() + ", less the recovery " + benefit.recovered());
    }

    private static TraceLine nil(NodeContext ctx, RuleRow row, EmployerExpenseBenefit benefit, int sequence) {
        return ctx.traceLine(sequence, row.id(), StepKind.INCOME, benefit.amount(), Money.ZERO)
                .withLimitedBy("not a perquisite: excluded by " + row.id());
    }
}
