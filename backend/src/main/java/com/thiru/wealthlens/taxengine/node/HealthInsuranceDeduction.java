package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.Assessee;
import com.thiru.wealthlens.taxengine.input.FamilySnapshot;
import com.thiru.wealthlens.taxengine.input.HealthPayment;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.AggregateOutcome;
import com.thiru.wealthlens.taxengine.rules.cap.AggregateWithCeiling;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.condition.AgeSubject;
import com.thiru.wealthlens.taxengine.rules.condition.ConditionContext;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * s.126 — health insurance, preventive check-ups and medical expenditure, for {@link ChapterEightNode} (S2 s.126,
 * s.2(100); {@code DR 06} G2.3; D-136).
 *
 * <p>Two buckets, the assessee with the spouse and dependant children, and the parents, each an aggregate under its
 * own ceiling; the check-up limit first, across both, its allowance split between them in the ratio claimed (G2.3's
 * reading). A bucket takes its senior-citizen row where a premium or scheme payment in it covers a senior citizen —
 * resident in India and of the row's age at any time in the year; one premium covering a senior and a non-senior is
 * refused (OQ-18). Medical expenditure on a senior citizen for whom a premium was paid is refused (s.126(7)); so is
 * cash other than for a check-up (s.126(9)), and a lump-sum premium counts a year's fraction (s.126(8)(b), (10)).
 */
final class HealthInsuranceDeduction {

    static final Set<RuleSetId> RULES = Set.of(RuleSetIds.RS_DED_HEALTH_FAMILY, RuleSetIds.RS_DED_HEALTH_FAMILY_SENIOR,
            RuleSetIds.RS_DED_HEALTH_PARENTS, RuleSetIds.RS_DED_HEALTH_PARENTS_SENIOR, RuleSetIds.RS_DED_HEALTH_CHECKUP,
            RuleSetIds.RS_DED_HEALTH_CASH_PERMITTED, RuleSetIds.RS_DED_HEALTH_SPREAD,
            RuleSetIds.RS_DED_HEALTH_INSURANCE_PAID, RuleSetIds.RS_DED_HEALTH_INSURANCE_LIMB,
            RuleSetIds.RS_DED_HEALTH_MEDICAL_LIMB);

    /** Who a payment covers, one person at a time — the relationship, or {@link #SELF} (W-156b1, D-346). */
    static final ItemFactId COVERED_RELATIONSHIP = new ItemFactId("health.covered.relationship");

    /** Whether the covered person is a dependant. */
    static final ItemFactId COVERED_DEPENDANT = new ItemFactId("health.covered.dependant");

    /** What the payment is for — the {@code HealthPaymentKind} constant name. */
    static final ItemFactId KIND = new ItemFactId("health.kind");

    /** The relationship code the bucket rows read for the assessee, who is not a family member. */
    private static final String SELF = "SELF";

    /** s.126(2)'s two aggregates, each by the set of its row without the senior substitution. */
    private enum Bucket {
        FAMILY(RuleSetIds.RS_DED_HEALTH_FAMILY),
        PARENTS(RuleSetIds.RS_DED_HEALTH_PARENTS);

        private final RuleSetId row;

        Bucket(RuleSetId row) {
            this.row = row;
        }
    }

    /**
     * Where a payment's amount accumulates in its bucket — s.126(2)'s insurance or medical limb, or s.126(3)'s check-up
     * limit — each by the set of the row that says which kinds fill it (W-156b2, D-347).
     */
    private enum Limb {
        INSURANCE(RuleSetIds.RS_DED_HEALTH_INSURANCE_LIMB),
        CHECKUP(RuleSetIds.RS_DED_HEALTH_CHECKUP),
        MEDICAL(RuleSetIds.RS_DED_HEALTH_MEDICAL_LIMB);

        private final RuleSetId row;

        Limb(RuleSetId row) {
            this.row = row;
        }
    }

    /** What one bucket was paid, by limb, this year. */
    private static final class Paid {
        private Money insurance = Money.ZERO;
        private Money checkups = Money.ZERO;
        private Money medical = Money.ZERO;
        private boolean coversSenior;
    }

    private HealthInsuranceDeduction() {
    }

    /** The s.126 lines, numbered from {@code firstSequence}; none where nothing is entered. */
    static List<TraceLine> lines(NodeContext ctx, int firstSequence) {
        List<HealthPayment> payments = ctx.inputs().entered().healthPayments();
        if (payments.isEmpty()) {
            return List.of();
        }
        if (!(ctx.inputs().assessee() instanceof Individual)) {
            throw new IllegalArgumentException("s.126(5)–(6), a Hindu undivided family's health deduction, is not"
                    + " modelled (D-136)");
        }
        Map<Bucket, Paid> buckets = new EnumMap<>(Bucket.class);
        for (Bucket bucket : Bucket.values()) {
            buckets.put(bucket, new Paid());
        }
        payments.forEach(payment -> record(ctx, payment, payments, buckets));
        List<TraceLine> lines = new ArrayList<>();
        allocateCheckups(ctx, buckets, firstSequence, lines);
        for (Bucket bucket : Bucket.values()) {
            bucketLine(ctx, bucket, buckets.get(bucket), firstSequence, lines);
        }
        return lines;
    }

    private static void record(NodeContext ctx, HealthPayment payment, List<HealthPayment> all,
            Map<Bucket, Paid> buckets) {
        if (payment.paidInCash()) {
            requireReached(ctx, RuleSetIds.RS_DED_HEALTH_CASH_PERMITTED, payment, "paid in cash");
        }
        if (payment.relevantTaxYears() > 1) {
            requireReached(ctx, RuleSetIds.RS_DED_HEALTH_SPREAD, payment,
                    "spread over " + payment.relevantTaxYears() + " tax years");
        }
        Bucket bucket = bucketOf(ctx, payment);
        Paid paid = buckets.get(bucket);
        switch (limbOf(ctx, payment)) {
            case INSURANCE -> {
                paid.insurance = paid.insurance.plus(payment.amount().apportioned(1, payment.relevantTaxYears()));
                paid.coversSenior |= insurancePaid(ctx, payment) && coversSenior(ctx, payment, bucket);
            }
            case CHECKUP -> paid.checkups = paid.checkups.plus(payment.amount());
            case MEDICAL -> {
                requireNotInsuredSenior(ctx, payment, all);
                paid.medical = paid.medical.plus(payment.amount());
            }
        }
    }

    /** Refuses a payment whose kind the row does not reach for what was done with it (s.126(8)(b), (9)). */
    private static void requireReached(NodeContext ctx, RuleSetId set, HealthPayment payment, String what) {
        RuleRow row = ctx.ruleOf(set);
        if (!RowReach.holdsFor(ctx, row, kindOf(payment))) {
            throw new IllegalArgumentException("A " + payment.kind() + " of " + payment.amount() + " was " + what
                    + ", which " + row.id() + " does not allow for it");
        }
    }

    /** The limb whose row reaches the payment's kind; exactly one must. */
    private static Limb limbOf(NodeContext ctx, HealthPayment payment) {
        List<Limb> reaching = Arrays.stream(Limb.values())
                .filter(limb -> RowReach.holdsFor(ctx, ctx.ruleOf(limb.row), kindOf(payment)))
                .toList();
        if (reaching.size() != 1) {
            throw new IllegalStateException("A " + payment.kind() + " is reached by " + reaching + " of the limb rows;"
                    + " exactly one must take it");
        }
        return reaching.getFirst();
    }

    /**
     * Whether the payment is one "paid to effect or to keep in force the health insurance" of a person — what raises a
     * bucket to its senior row and bars that senior's medical expenditure (s.126(7), (8)(a); W-156b2, D-347).
     */
    private static boolean insurancePaid(NodeContext ctx, HealthPayment payment) {
        return RowReach.holdsFor(ctx, ctx.ruleOf(RuleSetIds.RS_DED_HEALTH_INSURANCE_PAID), kindOf(payment));
    }

    private static ItemFacts kindOf(HealthPayment payment) {
        return ItemFacts.of(KIND, payment.kind().name());
    }

    /**
     * The bucket whose row reaches every person the payment covers — which people and which kinds each takes is the
     * rows' to say (s.126(2), (10)(b); W-156b1, D-346).
     */
    private static Bucket bucketOf(NodeContext ctx, HealthPayment payment) {
        Set<Bucket> covered = new HashSet<>();
        if (payment.coversAssessee()) {
            covered.add(bucketReaching(ctx, "the assessee", person(SELF, false, payment)));
        }
        for (int position : payment.familyMembers()) {
            FamilySnapshot.Member member = member(ctx, position);
            covered.add(bucketReaching(ctx, "family member " + position,
                    person(member.relationship().name(), member.dependant(), payment)));
        }
        if (covered.size() != 1) {
            throw new IllegalArgumentException("A health payment covers both the family and a parent; s.126(2) limits"
                    + " them apart, so enter each part separately");
        }
        return covered.iterator().next();
    }

    private static Bucket bucketReaching(NodeContext ctx, String who, ItemFacts person) {
        List<Bucket> reaching = Arrays.stream(Bucket.values())
                .filter(bucket -> RowReach.holdsFor(ctx, ctx.ruleOf(bucket.row), person))
                .toList();
        if (reaching.size() != 1) {
            throw new IllegalArgumentException("A health payment for " + who + " " + person.codes() + " is reached by "
                    + reaching.size() + " of the bucket rows " + Arrays.stream(Bucket.values())
                            .map(bucket -> ctx.ruleIdOf(bucket.row)).toList() + "; exactly one must take it");
        }
        return reaching.getFirst();
    }

    private static ItemFacts person(String relationship, boolean dependant, HealthPayment payment) {
        return new ItemFacts(Map.of(COVERED_RELATIONSHIP, relationship, COVERED_DEPENDANT, Boolean.toString(dependant),
                KIND, payment.kind().name()), Map.of(), Map.of());
    }

    private static boolean coversSenior(NodeContext ctx, HealthPayment payment, Bucket bucket) {
        RuleVersion seniorRow = version(ctx, bucket == Bucket.FAMILY
                ? ctx.ruleIdOf(RuleSetIds.RS_DED_HEALTH_FAMILY_SENIOR) : ctx.ruleIdOf(RuleSetIds.RS_DED_HEALTH_PARENTS_SENIOR));
        List<Boolean> seniors = new ArrayList<>();
        if (payment.coversAssessee()) {
            seniors.add(isSenior(ctx, seniorRow, assesseeBirth(ctx), resident(ctx.inputs().assessee())));
        }
        for (int position : payment.familyMembers()) {
            FamilySnapshot.Member member = member(ctx, position);
            seniors.add(isSenior(ctx, seniorRow, member.dateOfBirth(), member.residentInIndia()));
        }
        if (seniors.contains(true) && seniors.contains(false)) {
            throw new IllegalArgumentException("One premium covers a senior citizen and someone who is not; which"
                    + " limit applies is open (s.126(8)(a); OQ-18), so it is refused rather than guessed");
        }
        return seniors.contains(true);
    }

    /** s.126(7): medical expenditure on a senior citizen only where no premium was paid for that person. */
    private static void requireNotInsuredSenior(NodeContext ctx, HealthPayment medical, List<HealthPayment> all) {
        RuleVersion familySenior = version(ctx, ctx.ruleIdOf(RuleSetIds.RS_DED_HEALTH_FAMILY_SENIOR));
        List<HealthPayment> premiums = all.stream()
                .filter(payment -> insurancePaid(ctx, payment))
                .toList();
        if (medical.coversAssessee() && isSenior(ctx, familySenior, assesseeBirth(ctx),
                resident(ctx.inputs().assessee())) && premiums.stream().anyMatch(HealthPayment::coversAssessee)) {
            throw new IllegalArgumentException("Medical expenditure on the assessee, a senior citizen insured in the"
                    + " year, is not deductible (" + ctx.ruleIdOf(RuleSetIds.RS_DED_HEALTH_INSURANCE_PAID) + ")");
        }
        for (int position : medical.familyMembers()) {
            FamilySnapshot.Member member = member(ctx, position);
            if (isSenior(ctx, familySenior, member.dateOfBirth(), member.residentInIndia())
                    && premiums.stream().anyMatch(premium -> premium.familyMembers().contains(position))) {
                throw new IllegalArgumentException("Medical expenditure on family member " + position + ", a senior"
                        + " citizen insured in the year, is not deductible ("
                        + ctx.ruleIdOf(RuleSetIds.RS_DED_HEALTH_INSURANCE_PAID) + ")");
            }
        }
    }

    /** s.126(3): the check-ups together within their limit, split between the buckets in the ratio claimed. */
    private static void allocateCheckups(NodeContext ctx, Map<Bucket, Paid> buckets, int firstSequence,
            List<TraceLine> lines) {
        Money family = buckets.get(Bucket.FAMILY).checkups;
        Money total = family.plus(buckets.get(Bucket.PARENTS).checkups);
        if (!total.isGreaterThan(Money.ZERO)) {
            return;
        }
        RuleVersion version = version(ctx, ctx.ruleIdOf(RuleSetIds.RS_DED_HEALTH_CHECKUP));
        Money allowed = Money.min(total, TemporallyApportionedCap.apply(ctx.ruleOf(RuleSetIds.RS_DED_HEALTH_CHECKUP),
                ctx.inputs().temporal(), new CapContext(Map.of())).value());
        Money toFamily = family.scaledBy(allowed, total);
        buckets.get(Bucket.FAMILY).insurance = buckets.get(Bucket.FAMILY).insurance.plus(toFamily);
        buckets.get(Bucket.PARENTS).insurance = buckets.get(Bucket.PARENTS).insurance.plus(allowed.minus(toFamily));
        if (ctx.inRegime(ctx.ruleIdOf(RuleSetIds.RS_DED_HEALTH_CHECKUP), version).applies()) {
            lines.add(TraceLine.of(firstSequence + lines.size(), NodeIds.CHAPTERVIII, ctx.ruleIdOf(RuleSetIds.RS_DED_HEALTH_CHECKUP),
                            StepKind.MEASURE, MoneyValue.of(total), MoneyValue.of(allowed), version.provenance())
                    .withLimitedBy("split in the ratio claimed: " + toFamily + " family, " + allowed.minus(toFamily)
                            + " parents"));
        }
    }

    private static void bucketLine(NodeContext ctx, Bucket bucket, Paid paid, int firstSequence,
            List<TraceLine> lines) {
        Money entered = paid.insurance.plus(paid.medical);
        if (!entered.isGreaterThan(Money.ZERO)) {
            return;
        }
        boolean family = bucket == Bucket.FAMILY;
        RuleId row = family
                ? (paid.coversSenior ? ctx.ruleIdOf(RuleSetIds.RS_DED_HEALTH_FAMILY_SENIOR) : ctx.ruleIdOf(RuleSetIds.RS_DED_HEALTH_FAMILY))
                : (paid.coversSenior ? ctx.ruleIdOf(RuleSetIds.RS_DED_HEALTH_PARENTS_SENIOR) : ctx.ruleIdOf(RuleSetIds.RS_DED_HEALTH_PARENTS));
        RuleVersion version = version(ctx, row);
        if (!ctx.inRegime(row, version).applies()) {
            lines.add(TraceLine.of(firstSequence + lines.size(), NodeIds.CHAPTERVIII, row, StepKind.DEDUCTION,
                            MoneyValue.of(entered), MoneyValue.of(Money.ZERO), version.provenance())
                    .withLimitedBy(ctx.inRegime(row, version).whyNot()));
            return;
        }
        ComponentCode insurance = family ? ComponentCodes.HEALTH_INSURANCE_FAMILY
                : ComponentCodes.HEALTH_INSURANCE_PARENTS;
        ComponentCode medical = family ? ComponentCodes.MEDICAL_EXPENDITURE_FAMILY
                : ComponentCodes.MEDICAL_EXPENDITURE_PARENTS;
        AggregateOutcome outcome = ((AggregateWithCeiling) version.cap().orElseThrow()).applyDetailed(
                CapContext.ofContributions(Map.of(insurance, paid.insurance, medical, paid.medical)));
        lines.add(TraceLine.of(firstSequence + lines.size(), NodeIds.CHAPTERVIII, row, StepKind.DEDUCTION,
                        MoneyValue.of(outcome.aggregate()), MoneyValue.of(outcome.allowed()), version.provenance())
                .withLimitedBy(outcome.limitedBy()));
    }

    /**
     * Senior citizen: resident in India and, at any time in the year, of the row's age (s.2(100)); s.128(4) reads it
     * too. Only the row's conditions about the person are tested: those about the item say which payments it reaches.
     */
    static boolean isSenior(NodeContext ctx, RuleVersion seniorRow, LocalDate born, boolean residentInIndia) {
        int age = Period.between(born, ctx.inputs().temporal().taxYearEnd()).getYears();
        ConditionContext person = new ConditionContext(residentInIndia,
                PersonType.INDIVIDUAL,
                Map.of(AgeSubject.INSURED_PERSON, age), Map.of());
        return residentInIndia && seniorRow.conditions().stream()
                .filter(condition -> !condition.isAboutItem())
                .allMatch(condition -> condition.holds(person));
    }

    private static LocalDate assesseeBirth(NodeContext ctx) {
        return ((Individual) ctx.inputs().assessee()).dateOfBirth();
    }

    private static boolean resident(Assessee assessee) {
        return assessee.residence() != Assessee.ResidentialStatus.NON_RESIDENT;
    }

    private static FamilySnapshot.Member member(NodeContext ctx, int position) {
        List<FamilySnapshot.Member> members = ctx.inputs().family().members();
        if (position >= members.size()) {
            throw new IllegalArgumentException("A health payment names family member " + position + ", but "
                    + members.size() + " were entered");
        }
        return members.get(position);
    }

    private static RuleVersion version(NodeContext ctx, RuleId row) {
        return TemporallyApportionedCap.governingVersion(ctx.rule(row), ctx.inputs().temporal());
    }
}
