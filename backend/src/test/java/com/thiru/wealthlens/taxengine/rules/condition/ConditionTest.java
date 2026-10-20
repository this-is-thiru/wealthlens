package com.thiru.wealthlens.taxengine.rules.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * One positive and one negative case per predicate, plus the two behaviours the algebra's usefulness
 * rests on: a missing fact throws rather than denying, and a failing condition can say why.
 *
 * <p>W-021b and W-021c extend this class with the amount, structure and composition predicates.
 */
class ConditionTest {

    private static final ItemFactId VEHICLE_ENGINE = new ItemFactId("vehicle.engine");
    private static final ItemFactId ENGINE_CC = new ItemFactId("vehicle.engine.cc");
    private static final ItemFactId ACQUIRED_ON = new ItemFactId("capitalgains.acquired.on");

    private static ConditionContext resident(int age) {
        return ConditionContext.residentIndividualAged(age);
    }

    private static ConditionContext nonResident(int age) {
        return new ConditionContext(
                false, PersonType.INDIVIDUAL, Map.of(AgeSubject.SELF, age), Map.of());
    }

    @Nested
    @DisplayName("IsResident")
    class Residency {

        @Test
        void holds_whenResident() {
            assertThat(new IsResident().holds(resident(35))).isTrue();
        }

        @Test
        void doesNotHold_whenNotResident() {
            // RATE-SLAB-AGE-001: the higher old-regime exemptions are for residents only
            assertThat(new IsResident().holds(nonResident(65))).isFalse();
        }

        @Test
        void describe_readsAsAReasonAClaimWasRefused() {
            assertThat(new IsResident().describe()).isEqualTo("the assessee is resident");
        }
    }

    @Nested
    @DisplayName("AgeAtLeast — the subject matters")
    class Age {

        @Test
        void holds_atExactlyTheThreshold() {
            // "60 or over" includes 60
            assertThat(new AgeAtLeast(60, AgeSubject.SELF).holds(resident(60))).isTrue();
        }

        @Test
        void doesNotHold_oneYearBelow() {
            assertThat(new AgeAtLeast(60, AgeSubject.SELF).holds(resident(59))).isFalse();
        }

        @Test
        void selfAndInsuredPersonAreDifferentQuestions() {
            // Given — DED-80D-007: the taxpayer is 45, the insured parent is 70
            ConditionContext ctx = new ConditionContext(
                    true,
                    PersonType.INDIVIDUAL,
                    Map.of(AgeSubject.SELF, 45, AgeSubject.INSURED_PERSON, 70),
                    Map.of());

            // Then — without the subject field these two would be the same predicate,
            // and the rule would collapse to a test of the taxpayer's own age
            assertThat(new AgeAtLeast(60, AgeSubject.SELF).holds(ctx)).isFalse();
            assertThat(new AgeAtLeast(60, AgeSubject.INSURED_PERSON).holds(ctx)).isTrue();
        }

        @Test
        void holds_whenTheSubjectsAgeWasNeverSupplied_throwsRatherThanDenying() {
            // D-NOSILENT: a denial that looks like a legitimate result is worse than an error
            assertThatThrownBy(() -> new AgeAtLeast(60, AgeSubject.INSURED_PERSON).holds(resident(70)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("INSURED_PERSON");
        }

        @Test
        void describe_namesTheSubject() {
            assertThat(new AgeAtLeast(60, AgeSubject.SELF).describe()).isEqualTo("you are 60 or over");
            assertThat(new AgeAtLeast(60, AgeSubject.INSURED_PERSON).describe())
                    .isEqualTo("the insured person is 60 or over");
        }

        @Test
        void construction_rejectsANegativeThresholdAndAMissingSubject() {
            assertThatThrownBy(() -> new AgeAtLeast(-1, AgeSubject.SELF))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new AgeAtLeast(60, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("whose age");
        }
    }

    @Nested
    @DisplayName("HasPersonType")
    class Kind {

        @Test
        void holds_forTheDeclaredType() {
            assertThat(new HasPersonType(Set.of(PersonType.INDIVIDUAL)).holds(resident(35))).isTrue();
        }

        @Test
        void construction_whenTheSetIsEmpty_isRejected() {
            // A rule that applies to nobody is a pack mistake, not a rule
            assertThatThrownBy(() -> new HasPersonType(Set.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("applies to nobody");
        }
    }

    @Nested
    @DisplayName("IsCertifiedDisabled — a minimum, not an equality")
    class Disability {

        private static ConditionContext certified(DisabilitySeverity severity) {
            return new ConditionContext(
                    true,
                    PersonType.INDIVIDUAL,
                    Map.of(AgeSubject.SELF, 40),
                    Map.of(AgeSubject.SELF, severity));
        }

        @Test
        void holds_atTheSameSeverity() {
            assertThat(new IsCertifiedDisabled(DisabilitySeverity.DISABILITY)
                            .holds(certified(DisabilitySeverity.DISABILITY)))
                    .isTrue();
        }

        @Test
        void holds_whenCertifiedMoreSeverely() {
            // DED-80U-001's ₹1,25,000 rung does not stop the ₹75,000 rung's condition being met
            assertThat(new IsCertifiedDisabled(DisabilitySeverity.DISABILITY)
                            .holds(certified(DisabilitySeverity.SEVERE_DISABILITY)))
                    .isTrue();
        }

        @Test
        void doesNotHold_whenCertifiedLessSeverely() {
            assertThat(new IsCertifiedDisabled(DisabilitySeverity.SEVERE_DISABILITY)
                            .holds(certified(DisabilitySeverity.DISABILITY)))
                    .isFalse();
        }

        @Test
        void doesNotHold_whenNoCertificateWasRecorded() {
            // s.154(2) requires the certificate with the return, so its absence IS the answer —
            // unlike a missing age, which is a gap in what the taxpayer told us
            assertThat(new IsCertifiedDisabled(DisabilitySeverity.DISABILITY).holds(resident(40)))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("ConditionContext")
    class Context {

        @Test
        void maps_areDefensivelyCopied() {
            Map<AgeSubject, Integer> mutable = new java.util.HashMap<>();
            mutable.put(AgeSubject.SELF, 60);
            ConditionContext ctx =
                    new ConditionContext(true, PersonType.INDIVIDUAL, mutable, Map.of());

            mutable.put(AgeSubject.SELF, 20);

            assertThat(ctx.age(AgeSubject.SELF)).isEqualTo(60);
        }

        @Test
        void construction_whenPersonTypeIsMissing_isRejected() {
            assertThatThrownBy(() -> new ConditionContext(true, null, Map.of(), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ---------------------------------------------------------------- W-021b

    private static final NodeId TOTAL_INCOME = new NodeId("total.income");
    private static final ComponentCode HRA = new ComponentCode("HRA");

    /** A context carrying computation facts as well as person facts. */
    private static ConditionContext claim(
            Map<NodeId, Money> values,
            Set<HeadCode> heads,
            Set<ComponentCode> components,
            Set<EvidenceType> evidence,
            Optional<Boolean> nonCash) {
        return new ConditionContext(
                true,
                PersonType.INDIVIDUAL,
                Map.of(AgeSubject.SELF, 40),
                Map.of(),
                values,
                heads,
                components,
                evidence,
                nonCash,
                ItemFacts.NONE);
    }

    private static ConditionContext withIncome(String totalIncome) {
        return claim(
                Map.of(TOTAL_INCOME, Money.of(totalIncome)),
                Set.of(), Set.of(), Set.of(), Optional.empty());
    }

    @Nested
    @DisplayName("IncomeAtMost and IncomeAtLeast — inclusive at the boundary")
    class Amounts {

        @Test
        void incomeAtMost_holdsExactlyAtTheLimit() {
            // The rebate applies where income "does not exceed" the threshold — 12,00,000 qualifies
            assertThat(new IncomeAtMost(TOTAL_INCOME, Money.of("1200000")).holds(withIncome("1200000")))
                    .isTrue();
        }

        @Test
        void incomeAtMost_doesNotHoldOneRupeeAbove() {
            assertThat(new IncomeAtMost(TOTAL_INCOME, Money.of("1200000")).holds(withIncome("1200001")))
                    .isFalse();
        }

        @Test
        void incomeAtLeast_holdsExactlyAtTheThreshold() {
            assertThat(new IncomeAtLeast(TOTAL_INCOME, Money.of("5000000")).holds(withIncome("5000000")))
                    .isTrue();
        }

        @Test
        void incomeAtLeast_doesNotHoldOneRupeeBelow() {
            assertThat(new IncomeAtLeast(TOTAL_INCOME, Money.of("5000000")).holds(withIncome("4999999")))
                    .isFalse();
        }

        @Test
        void holds_whenTheNodeWasNeverComputed_throws() {
            // A threshold compared against a value nobody produced is a pipeline ordering bug,
            // not a taxpayer who earns zero
            assertThatThrownBy(() ->
                            new IncomeAtMost(new NodeId("never.computed"), Money.ZERO)
                                    .holds(withIncome("100")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("never.computed");
        }
    }

    @Nested
    @DisplayName("HasIncomeUnderHead — the s.202(4)(a) trap")
    class Heads {

        @Test
        void holds_whenTheHeadIsPresent() {
            ConditionContext ctx = claim(
                    Map.of(),
                    Set.of(HeadCode.SALARIES, HeadCode.BUSINESS_OR_PROFESSION),
                    Set.of(), Set.of(), Optional.empty());

            assertThat(new HasIncomeUnderHead(HeadCode.BUSINESS_OR_PROFESSION).holds(ctx)).isTrue();
        }

        @Test
        void doesNotHold_forASalariedTaxpayerWithNoBusinessIncome() {
            // REG-OPTOUT-YEARLY-001: this is exactly why a salaried person may switch every year
            ConditionContext ctx =
                    claim(Map.of(), Set.of(HeadCode.SALARIES), Set.of(), Set.of(), Optional.empty());

            assertThat(new HasIncomeUnderHead(HeadCode.BUSINESS_OR_PROFESSION).holds(ctx)).isFalse();
        }

        @Test
        void presenceIsTestedNotAmount() {
            // F32's trap: ₹50,000 of freelance work is not a small amount of business income
            // for this purpose. It is business income, and the lock-in attaches.
            ConditionContext ctx = claim(
                    Map.of(TOTAL_INCOME, Money.of("50000")),
                    Set.of(HeadCode.BUSINESS_OR_PROFESSION),
                    Set.of(), Set.of(), Optional.empty());

            assertThat(new HasIncomeUnderHead(HeadCode.BUSINESS_OR_PROFESSION).holds(ctx)).isTrue();
        }
    }

    @Nested
    @DisplayName("ComponentPresent")
    class Components {

        @Test
        void holdsOnPresenceRegardlessOfAmount() {
            ConditionContext ctx =
                    claim(Map.of(), Set.of(), Set.of(HRA), Set.of(), Optional.empty());

            assertThat(new ComponentPresent(HRA).holds(ctx)).isTrue();
            assertThat(new ComponentPresent(new ComponentCode("LTA")).holds(ctx)).isFalse();
        }
    }

    @Nested
    @DisplayName("EvidenceRecorded — absence is an answer, not a gap")
    class Evidence {

        @Test
        void holds_whenTheArtefactWasFurnished() {
            ConditionContext ctx = claim(
                    Map.of(), Set.of(), Set.of(),
                    Set.of(EvidenceType.INSTITUTION_REPORT_80G), Optional.empty());

            assertThat(new EvidenceRecorded(EvidenceType.INSTITUTION_REPORT_80G).holds(ctx)).isTrue();
        }

        @Test
        void doesNotHold_whenNothingWasFurnished() {
            // s.133(6): an 80G claim is allowed on the institution's report, not on a receipt —
            // so "no report" is the answer, not missing information
            ConditionContext ctx =
                    claim(Map.of(), Set.of(), Set.of(), Set.of(), Optional.empty());

            assertThat(new EvidenceRecorded(EvidenceType.INSTITUTION_REPORT_80G).holds(ctx)).isFalse();
        }

        @Test
        void aReceiptForOneClaimIsNotEvidenceForAnother() {
            ConditionContext ctx = claim(
                    Map.of(), Set.of(), Set.of(),
                    Set.of(EvidenceType.LANDLORD_PAN), Optional.empty());

            assertThat(new EvidenceRecorded(EvidenceType.LENDER_PAN).holds(ctx)).isFalse();
        }
    }

    @Nested
    @DisplayName("PaidOtherThanInCash — s.126(9), s.133(5)")
    class PaymentMode {

        @Test
        void holds_whenPaidByAnyNonCashMode() {
            ConditionContext ctx =
                    claim(Map.of(), Set.of(), Set.of(), Set.of(), Optional.of(true));

            assertThat(new PaidOtherThanInCash().holds(ctx)).isTrue();
        }

        @Test
        void doesNotHold_whenPaidInCash() {
            ConditionContext ctx =
                    claim(Map.of(), Set.of(), Set.of(), Set.of(), Optional.of(false));

            assertThat(new PaidOtherThanInCash().holds(ctx)).isFalse();
        }

        @Test
        void holds_whenNoPaymentModeWasRecorded_throws() {
            // Assuming either answer silently allows or denies a deduction
            ConditionContext ctx =
                    claim(Map.of(), Set.of(), Set.of(), Set.of(), Optional.empty());

            assertThatThrownBy(() -> new PaidOtherThanInCash().holds(ctx))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("s.126(9)");
        }
    }

    // ---------------------------------------------------------------- W-021c

    @Nested
    @DisplayName("AllOf, AnyOf, Not")
    class Composition {

        @Test
        void anyOf_isTheShapeDED80D007IsIn() {
            // 15-review-findings.md:533 — either the taxpayer or the insured person is 60 or over.
            // Taxpayer 45, insured parent 70.
            ConditionContext ctx = new ConditionContext(
                    true,
                    PersonType.INDIVIDUAL,
                    Map.of(AgeSubject.SELF, 45, AgeSubject.INSURED_PERSON, 70),
                    Map.of());

            Condition rule = AnyOf.of(
                    new AgeAtLeast(60, AgeSubject.SELF),
                    new AgeAtLeast(60, AgeSubject.INSURED_PERSON));

            assertThat(rule.holds(ctx)).isTrue();
        }

        @Test
        void anyOf_doesNotHold_whenNoLimbDoes() {
            ConditionContext ctx = new ConditionContext(
                    true,
                    PersonType.INDIVIDUAL,
                    Map.of(AgeSubject.SELF, 45, AgeSubject.INSURED_PERSON, 50),
                    Map.of());

            assertThat(AnyOf.of(
                                    new AgeAtLeast(60, AgeSubject.SELF),
                                    new AgeAtLeast(60, AgeSubject.INSURED_PERSON))
                            .holds(ctx))
                    .isFalse();
        }

        @Test
        void allOf_requiresEveryLimb() {
            ConditionContext ctx = resident(65);

            assertThat(AllOf.of(new IsResident(), new AgeAtLeast(60, AgeSubject.SELF)).holds(ctx))
                    .isTrue();
            assertThat(AllOf.of(new IsResident(), new AgeAtLeast(80, AgeSubject.SELF)).holds(ctx))
                    .isFalse();
        }

        @Test
        void not_negatesTheAnswer() {
            assertThat(new Not(new IsResident()).holds(nonResident(40))).isTrue();
            assertThat(new Not(new IsResident()).holds(resident(40))).isFalse();
        }

        @Test
        void nesting_composesToArbitraryDepth() {
            // resident AND (60+ OR certified disabled) — the shape DED-80U-001 needs
            ConditionContext ctx = new ConditionContext(
                    true,
                    PersonType.INDIVIDUAL,
                    Map.of(AgeSubject.SELF, 45),
                    Map.of(AgeSubject.SELF, DisabilitySeverity.SEVERE_DISABILITY));

            Condition rule = AllOf.of(
                    new IsResident(),
                    AnyOf.of(
                            new AgeAtLeast(60, AgeSubject.SELF),
                            new IsCertifiedDisabled(DisabilitySeverity.DISABILITY)));

            assertThat(rule.holds(ctx)).isTrue();
            assertThat(new Not(rule).holds(ctx)).isFalse();
        }
    }

    @Nested
    @DisplayName("short-circuiting — a settled condition does not demand more facts")
    class ShortCircuit {

        /** Taxpayer is 65; the insured person's age was never supplied. */
        private static ConditionContext taxpayerSixtyFiveOnly() {
            return ConditionContext.residentIndividualAged(65);
        }

        @Test
        void anyOf_stopsAtTheFirstLimbThatHolds() {
            // Evaluating every limb would throw for INSURED_PERSON, blocking a valid claim to
            // collect a fact that cannot change the outcome
            Condition rule = AnyOf.of(
                    new AgeAtLeast(60, AgeSubject.SELF),
                    new AgeAtLeast(60, AgeSubject.INSURED_PERSON));

            assertThat(rule.holds(taxpayerSixtyFiveOnly())).isTrue();
        }

        @Test
        void anyOf_withTheLimbsReversed_throwsOnTheMissingFact() {
            // The honest cost of short-circuiting: with facts missing, order decides.
            // Rules should list their cheapest and most certain limbs first.
            Condition rule = AnyOf.of(
                    new AgeAtLeast(60, AgeSubject.INSURED_PERSON),
                    new AgeAtLeast(60, AgeSubject.SELF));

            assertThatThrownBy(() -> rule.holds(taxpayerSixtyFiveOnly()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("INSURED_PERSON");
        }

        @Test
        void allOf_stopsAtTheFirstLimbThatFails() {
            Condition rule = AllOf.of(
                    new IsResident(), new AgeAtLeast(60, AgeSubject.INSURED_PERSON));

            // Not resident, so the missing insured-person age is never asked for
            assertThat(rule.holds(nonResident(65))).isFalse();
        }

        @Test
        void not_propagatesTheFailureRatherThanInvertingIt() {
            // Turning a missing fact into true would allow a deduction because nobody knew
            assertThatThrownBy(() ->
                            new Not(new AgeAtLeast(60, AgeSubject.INSURED_PERSON))
                                    .holds(taxpayerSixtyFiveOnly()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("explaining a refusal")
    class Explanation {

        @Test
        void allOf_namesTheLimbThatFailed() {
            AllOf rule = AllOf.of(new IsResident(), new AgeAtLeast(60, AgeSubject.SELF));

            assertThat(rule.firstFailing(nonResident(65)))
                    .get()
                    .extracting(Condition::describe)
                    .isEqualTo("the assessee is resident");
        }

        @Test
        void describe_readsAsProse() {
            assertThat(AllOf.of(new IsResident(), new AgeAtLeast(60, AgeSubject.SELF)).describe())
                    .isEqualTo("the assessee is resident and you are 60 or over");
            assertThat(new Not(new IsResident()).describe())
                    .isEqualTo("not (the assessee is resident)");
        }
    }

    @Nested
    @DisplayName("empty composites are rejected")
    class EmptyComposites {

        @Test
        void allOf_empty_wouldAllowEveryTaxpayer() {
            assertThatThrownBy(() -> new AllOf(java.util.List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("allow every taxpayer");
        }

        @Test
        void anyOf_empty_wouldDenyEveryTaxpayer() {
            assertThatThrownBy(() -> new AnyOf(java.util.List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("deny every taxpayer");
        }

        @Test
        void not_requiresACondition() {
            assertThatThrownBy(() -> new Not(null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("ItemFactIs")
    class ItemFact {

        private ConditionContext aboutCar(String engine) {
            return resident(40).about(ItemFacts.of(VEHICLE_ENGINE, engine));
        }

        @Test
        void holds_whenTheItemHasTheCode_isTrue() {
            // Given
            Condition small = new ItemFactIs(VEHICLE_ENGINE, "UP_TO_1600_CC");

            // When / Then
            assertThat(small.holds(aboutCar("UP_TO_1600_CC"))).isTrue();
        }

        @Test
        void holds_whenTheItemHasAnotherCode_isFalse() {
            // Given
            Condition small = new ItemFactIs(VEHICLE_ENGINE, "UP_TO_1600_CC");

            // When / Then
            assertThat(small.holds(aboutCar("ABOVE_1600_CC"))).isFalse();
        }

        @Test
        void holds_whenTheItemLacksTheFact_throwsRatherThanChoosingAnotherRow() {
            // Given
            Condition small = new ItemFactIs(VEHICLE_ENGINE, "UP_TO_1600_CC");

            // When / Then
            assertThatThrownBy(() -> small.holds(resident(40)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("vehicle.engine");
        }

        @Test
        void construct_whenCodeBlank_isRefused() {
            assertThatThrownBy(() -> new ItemFactIs(VEHICLE_ENGINE, " "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("ItemFactAtMost")
    class ItemQuantity {

        // Table II's lower column: "does not exceed 1.6 litres" — 1600 cc (DR 01 PERQ-CAR-2026)
        private final Condition upTo1600 = new ItemFactAtMost(ENGINE_CC, 1600);

        private ConditionContext aboutCapacity(long cc) {
            return resident(40).about(new ItemFacts(Map.of(), Map.of(ENGINE_CC, cc), Map.of()));
        }

        @Test
        void holds_whenTheQuantityIsTheLimit_isTrue() {
            assertThat(upTo1600.holds(aboutCapacity(1600))).isTrue();
        }

        @Test
        void holds_whenTheQuantityIsOneAboveTheLimit_isFalse() {
            assertThat(upTo1600.holds(aboutCapacity(1601))).isFalse();
        }

        @Test
        void holds_whenTheItemLacksTheQuantity_throwsRatherThanChoosingAnotherRow() {
            assertThatThrownBy(() -> upTo1600.holds(resident(40)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("vehicle.engine.cc");
        }
    }

    @Nested
    @DisplayName("ItemDateBefore")
    class ItemDate {

        // S.O. 5054(E) clause (I): "before the 1st day of October, 2004" (egazette 2018/190360 p.3)
        private final Condition beforeOctober2004 = new ItemDateBefore(ACQUIRED_ON, LocalDate.of(2004, 10, 1));

        private ConditionContext acquiredOn(LocalDate date) {
            return resident(40).about(new ItemFacts(Map.of(), Map.of(), Map.of(ACQUIRED_ON, date)));
        }

        @Test
        void holds_whenTheDateIsTheDayBefore_isTrue() {
            assertThat(beforeOctober2004.holds(acquiredOn(LocalDate.of(2004, 9, 30)))).isTrue();
        }

        @Test
        void holds_whenTheDateIsTheDayItself_isFalse() {
            // "before" is strict: 1-10-2004 is clause (II)
            assertThat(beforeOctober2004.holds(acquiredOn(LocalDate.of(2004, 10, 1)))).isFalse();
        }

        @Test
        void holds_whenTheItemLacksTheDate_throwsRatherThanChoosingAnotherRow() {
            assertThatThrownBy(() -> beforeOctober2004.holds(resident(40)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("capitalgains.acquired.on");
        }
    }

    @Nested
    @DisplayName("the algebra is closed")
    class SealedFamily {

        /**
         * An exhaustive switch with no {@code default} over all sixteen predicates. This does not
         * merely pass — it <b>compiles</b>, and that is the assertion. A seventeenth {@code Condition}
         * breaks this line, which is the whole reason the interface is sealed.
         *
         * <p>{@code EffectiveBetween} is deliberately absent: withdrawn by 18-temporal-versioning
         * V-2, because a rule whose limit changes mid-year keeps one rule id — the id is the
         * contract — and the change is a second RuleVersion, not a date-guarded twin.
         */
        @Test
        void everyConditionIsAccountedFor() {
            java.util.List<Condition> all = java.util.List.of(
                    new IsResident(),
                    new AgeAtLeast(60, AgeSubject.SELF),
                    new HasPersonType(Set.of(PersonType.INDIVIDUAL)),
                    new IsCertifiedDisabled(DisabilitySeverity.DISABILITY),
                    new IncomeAtMost(TOTAL_INCOME, Money.ZERO),
                    new IncomeAtLeast(TOTAL_INCOME, Money.ZERO),
                    new HasIncomeUnderHead(HeadCode.SALARIES),
                    new ComponentPresent(HRA),
                    new EvidenceRecorded(EvidenceType.LANDLORD_PAN),
                    new PaidOtherThanInCash(),
                    new ItemFactIs(VEHICLE_ENGINE, "ELECTRIC"),
                    new ItemFactAtMost(ENGINE_CC, 1600),
                    new ItemDateBefore(ACQUIRED_ON, LocalDate.of(2004, 10, 1)),
                    AllOf.of(new IsResident()),
                    AnyOf.of(new IsResident()),
                    new Not(new IsResident()));

            for (Condition condition : all) {
                String group = switch (condition) {
                    case IsResident ignored -> "person";
                    case AgeAtLeast ignored -> "person";
                    case HasPersonType ignored -> "person";
                    case IsCertifiedDisabled ignored -> "person";
                    case IncomeAtMost ignored -> "amount";
                    case IncomeAtLeast ignored -> "amount";
                    case HasIncomeUnderHead ignored -> "structure";
                    case ComponentPresent ignored -> "structure";
                    case EvidenceRecorded ignored -> "structure";
                    case PaidOtherThanInCash ignored -> "structure";
                    case ItemFactIs ignored -> "item";
                    case ItemFactAtMost ignored -> "item";
                    case ItemDateBefore ignored -> "item";
                    case AllOf ignored -> "composition";
                    case AnyOf ignored -> "composition";
                    case Not ignored -> "composition";
                };
                assertThat(group).isNotBlank();
            }

            // Part E's list, after the W-021 erratum: 13, not the 16 the backlog claimed; and the three item facts
            assertThat(all).hasSize(16);
        }
    }
}
