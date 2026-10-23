package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.Assessee;
import com.thiru.wealthlens.taxengine.input.DisabledDependantSupport;
import com.thiru.wealthlens.taxengine.input.FamilySnapshot;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.input.SpecifiedDiseaseTreatment;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.condition.DisabilitySeverity;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * s.154, s.127 and s.128 — a disabled assessee, a disabled dependant, and treatment of a specified disease — for
 * {@link ChapterEightNode} (S2; D-138).
 *
 * <p>s.154 reads only the assessee's own certificate and residence. s.127 allows the lesser of what was spent or
 * deposited and the limit for the dependant's severity — "up to" read as a ceiling — for one dependant; a second is
 * refused (OQ-19). s.128 allows the lesser of what was paid less what was reimbursed and the limit, the senior
 * citizen's where the person treated is one; treatment for a senior and a non-senior in one year is refused (OQ-20).
 * A non-resident assessee gets none of them; a row the regime excludes gives a nil line where something is entered.
 * Each row's cap carries its rule — the lesser of, or a fixed sum: TY 2025-26's 1961 s.80DD is a fixed sum (D-146).
 */
final class DisabilityDeduction {

    static final Set<RuleSetId> RULES = Set.of(RuleSetIds.RS_DED_DISABILITY, RuleSetIds.RS_DED_DISABILITY_SEVERE,
            RuleSetIds.RS_DED_DISABLED_DEPENDANT, RuleSetIds.RS_DED_DISABLED_DEPENDANT_SEVERE, RuleSetIds.RS_DED_SPECIFIED_DISEASE,
            RuleSetIds.RS_DED_SPECIFIED_DISEASE_SENIOR, RuleSetIds.RS_DED_DEPENDANT);

    /** The certified severity — the {@code DisabilitySeverity} name — which the severity rows read (W-156c, D-348). */
    static final ItemFactId SEVERITY = new ItemFactId("disability.severity");

    /** A family member's relationship to the assessee, which {@code DED-DEPENDANT} reads. */
    static final ItemFactId RELATIONSHIP = new ItemFactId("dependant.relationship");

    /** Whether a family member is wholly or mainly dependent on the assessee, which {@code DED-DEPENDANT} reads. */
    static final ItemFactId WHOLLY_OR_MAINLY = new ItemFactId("dependant.wholly.or.mainly");

    /** s.154's rows, the plain then the severe: the one reaching the assessee's severity applies. */
    private static final List<RuleSetId> OWN = List.of(RuleSetIds.RS_DED_DISABILITY, RuleSetIds.RS_DED_DISABILITY_SEVERE);

    /** s.127's rows, the plain then the severe: the one reaching the dependant's severity applies. */
    private static final List<RuleSetId> DEPENDANT = List.of(RuleSetIds.RS_DED_DISABLED_DEPENDANT,
            RuleSetIds.RS_DED_DISABLED_DEPENDANT_SEVERE);

    private static final BaseId AMOUNT_CLAIMED = new BaseId("AMOUNT_CLAIMED");

    private DisabilityDeduction() {
    }

    /** The lines, numbered from {@code firstSequence}; none where nothing applies or is entered. */
    static List<TraceLine> lines(NodeContext ctx, int firstSequence) {
        List<TraceLine> lines = new ArrayList<>();
        ownDisability(ctx, firstSequence, lines);
        dependant(ctx, firstSequence, lines);
        treatment(ctx, firstSequence, lines);
        return lines;
    }

    /** s.154: the row reaching the assessee's severity; a nil line naming the person condition that failed. */
    private static void ownDisability(NodeContext ctx, int firstSequence, List<TraceLine> lines) {
        if (!(ctx.inputs().assessee() instanceof Individual individual) || individual.disability().isEmpty()) {
            return;
        }
        RuleId row = severityRow(ctx, OWN, individual.disability().get());
        RuleVersion version = version(ctx, row);
        Optional<Condition> unmet = version.conditions().stream()
                .filter(condition -> !condition.isAboutItem())
                .filter(condition -> !condition.holds(ctx.conditions()))
                .findFirst();
        if (unmet.isPresent()) {
            lines.add(ctx.traceLine(firstSequence + lines.size(), row, StepKind.DEDUCTION, Money.ZERO, Money.ZERO)
                    .withLimitedBy("not met: " + unmet.get().describe()));
            return;
        }
        Money amount = limit(ctx, row);
        lines.add(applies(ctx, row, version)
                ? ctx.traceLine(firstSequence + lines.size(), row, StepKind.DEDUCTION, amount, amount)
                        .withLimitedBy("certified")
                : nil(ctx, firstSequence + lines.size(), row, version, amount));
    }

    /**
     * s.127: one disabled dependant, under the severity's row — the lesser of what was spent or deposited and the limit
     * in TY 2026-27, a fixed sum under 1961 s.80DD in TY 2025-26 (the row's cap; D-146). Nothing spent or deposited
     * gives nothing in either: the deduction is for having incurred or paid something.
     */
    private static void dependant(NodeContext ctx, int firstSequence, List<TraceLine> lines) {
        List<DisabledDependantSupport> supports = ctx.inputs().entered().disabledDependantSupport();
        if (supports.isEmpty()) {
            return;
        }
        if (supports.size() > 1) {
            throw new IllegalArgumentException(supports.size() + " disabled dependants: whether s.127 gives one"
                    + " deduction or one each is not settled (OQ-19), so it is refused rather than guessed");
        }
        DisabledDependantSupport support = supports.getFirst();
        FamilySnapshot.Member member = dependantMember(ctx, support.familyMember(), "s.127");
        if (support.dependantClaimsSection154()) {
            throw new IllegalArgumentException("A dependant who claims s.154 for themselves is not a dependant under"
                    + " s.127 (s.127(8))");
        }
        DisabilitySeverity severity = member.disability().orElseThrow(() -> new IllegalArgumentException(
                "Family member " + support.familyMember() + " has no certified disability (s.127(1))"));
        RuleId row = severityRow(ctx, DEPENDANT, severity);
        if (!support.spentOrDeposited().isGreaterThan(Money.ZERO)) {
            lines.add(ctx.traceLine(firstSequence + lines.size(), row, StepKind.DEDUCTION, Money.ZERO, Money.ZERO)
                    .withLimitedBy("nothing spent or deposited"));
            return;
        }
        claimed(ctx, firstSequence, lines, row, support.spentOrDeposited());
    }

    /** s.128: the lesser of what was paid less what was reimbursed and the limit, a senior citizen's where so. */
    private static void treatment(NodeContext ctx, int firstSequence, List<TraceLine> lines) {
        List<SpecifiedDiseaseTreatment> treatments = ctx.inputs().entered().specifiedDiseaseTreatment();
        if (treatments.isEmpty()) {
            return;
        }
        RuleVersion seniorRow = version(ctx, ctx.ruleIdOf(RuleSetIds.RS_DED_SPECIFIED_DISEASE_SENIOR));
        Money net = Money.ZERO;
        List<Boolean> seniors = new ArrayList<>();
        for (SpecifiedDiseaseTreatment treatment : treatments) {
            net = net.plus(treatment.paid().minus(treatment.reimbursed()).floorAtZero());
            seniors.add(treatment.forAssessee()
                    ? HealthInsuranceDeduction.isSenior(ctx, seniorRow, individual(ctx).dateOfBirth(), resident(ctx))
                    : seniorMember(ctx, seniorRow, dependantMember(ctx, treatment.familyMember().orElseThrow(),
                            "s.128")));
        }
        if (seniors.contains(true) && seniors.contains(false)) {
            throw new IllegalArgumentException("Specified-disease treatment for a senior citizen and someone who is not"
                    + " in one year: which limit applies is not settled (s.128(4); OQ-20), so it is refused");
        }
        RuleId row = seniors.contains(true) ? ctx.ruleIdOf(RuleSetIds.RS_DED_SPECIFIED_DISEASE_SENIOR) : ctx.ruleIdOf(RuleSetIds.RS_DED_SPECIFIED_DISEASE);
        claimed(ctx, firstSequence, lines, row, net);
    }

    /** A claimed amount under its row's cap, for a resident assessee, in a regime the row applies in. */
    private static void claimed(NodeContext ctx, int firstSequence, List<TraceLine> lines, RuleId row, Money entered) {
        individual(ctx);
        RuleVersion version = version(ctx, row);
        if (!resident(ctx)) {
            lines.add(ctx.traceLine(firstSequence + lines.size(), row, StepKind.DEDUCTION, entered, Money.ZERO)
                    .withLimitedBy("the assessee is not resident in India"));
            return;
        }
        if (!applies(ctx, row, version)) {
            lines.add(nil(ctx, firstSequence + lines.size(), row, version, entered));
            return;
        }
        CapResult allowed = TemporallyApportionedCap.apply(ctx.rule(row), ctx.inputs().temporal(),
                CapContext.of(AMOUNT_CLAIMED, entered));
        lines.add(ctx.traceLine(firstSequence + lines.size(), row, StepKind.DEDUCTION, entered, allowed.value())
                .withLimitedBy(allowed.limitedBy()));
    }

    private static FamilySnapshot.Member dependantMember(NodeContext ctx, int position, String section) {
        List<FamilySnapshot.Member> members = ctx.inputs().family().members();
        if (position >= members.size()) {
            throw new IllegalArgumentException("A " + section + " claim names family member " + position + ", but "
                    + members.size() + " were entered");
        }
        FamilySnapshot.Member member = members.get(position);
        ItemFacts facts = new ItemFacts(Map.of(RELATIONSHIP, member.relationship().name(),
                WHOLLY_OR_MAINLY, Boolean.toString(member.dependant())), Map.of(), Map.of());
        RuleRow dependantRow = ctx.ruleOf(RuleSetIds.RS_DED_DEPENDANT);
        if (!RowReach.holdsFor(ctx, dependantRow, facts)) {
            throw new IllegalArgumentException("Family member " + position + " " + facts.codes() + " is not a"
                    + " dependant for " + section + ": " + dependantRow.id() + " does not reach them");
        }
        return member;
    }

    /** The row of the pair whose severity condition reaches the certified severity; exactly one must. */
    private static RuleId severityRow(NodeContext ctx, List<RuleSetId> pair, DisabilitySeverity severity) {
        ItemFacts facts = ItemFacts.of(SEVERITY, severity.name());
        List<RuleId> reaching = pair.stream().map(ctx::ruleIdOf)
                .filter(row -> RowReach.holdsFor(ctx, ctx.rule(row), facts))
                .toList();
        if (reaching.size() != 1) {
            throw new IllegalStateException(severity + " is reached by " + reaching + " of the rows of " + pair
                    + "; exactly one must take it");
        }
        return reaching.getFirst();
    }

    private static boolean seniorMember(NodeContext ctx, RuleVersion seniorRow, FamilySnapshot.Member member) {
        return HealthInsuranceDeduction.isSenior(ctx, seniorRow, member.dateOfBirth(), member.residentInIndia());
    }

    private static Individual individual(NodeContext ctx) {
        if (!(ctx.inputs().assessee() instanceof Individual individual)) {
            throw new IllegalArgumentException("s.127 and s.128 for a Hindu undivided family are not modelled (D-138)");
        }
        return individual;
    }

    private static boolean resident(NodeContext ctx) {
        return ctx.inputs().assessee().residence() != Assessee.ResidentialStatus.NON_RESIDENT;
    }

    private static boolean applies(NodeContext ctx, RuleId row, RuleVersion version) {
        return ctx.inRegime(row, version).applies();
    }

    private static Money limit(NodeContext ctx, RuleId row) {
        return TemporallyApportionedCap.apply(ctx.rule(row), ctx.inputs().temporal(), new CapContext(Map.of()))
                .value();
    }

    private static TraceLine nil(NodeContext ctx, int sequence, RuleId row, RuleVersion version, Money entered) {
        return ctx.traceLine(sequence, row, StepKind.DEDUCTION, entered, Money.ZERO)
                .withLimitedBy(ctx.inRegime(row, version).whyNot());
    }

    private static RuleVersion version(NodeContext ctx, RuleId row) {
        return TemporallyApportionedCap.governingVersion(ctx.rule(row), ctx.inputs().temporal());
    }
}
