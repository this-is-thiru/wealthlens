package com.thiru.wealthlens.taxrules.regime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxrules.condition.AgeSubject;
import com.thiru.wealthlens.taxrules.condition.ConditionContext;
import com.thiru.wealthlens.taxrules.condition.HasIncomeUnderHead;
import com.thiru.wealthlens.taxrules.condition.HeadCode;
import com.thiru.wealthlens.taxrules.condition.Not;
import com.thiru.wealthlens.taxrules.condition.PersonType;
import com.thiru.wealthlens.taxrules.id.RegimeCode;
import com.thiru.wealthlens.taxrules.id.RuleId;
import com.thiru.wealthlens.taxrules.id.TaxYearId;
import com.thiru.wealthlens.taxrules.registry.DraftOrFinal;
import com.thiru.wealthlens.taxrules.registry.Provenance;
import com.thiru.wealthlens.taxrules.registry.VerificationStatus;
import com.thiru.wealthlens.taxrules.temporal.DateRange;
import com.thiru.wealthlens.taxrules.validate.PackValidationException;
import java.net.URI;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Two windows, and why one is not enough.
 *
 * <p>W-026b extends this class with the election protocol, and W-026c with {@code RegimeSpec}.
 */
class RegimeLifecycleTest {

    private static final RuleId S201 = new RuleId("REG-S201-ENTRY-001");

    private static Provenance provenance() {
        return new Provenance(
                URI.create("https://egazette.gov.in/WriteReadData/2025/000000.pdf"),
                "Income-tax Act 2025, s.201(2)",
                DraftOrFinal.FINAL,
                LocalDate.of(2026, 9, 19),
                VerificationStatus.VERIFIED,
                null);
    }

    /**
     * The s.201 shape as it stands today: entry closed on 31 March 2024, application open with no
     * end date, incumbents continuing indefinitely.
     */
    private static RegimeLifecycle grandfathered() {
        return new RegimeLifecycle(
                DateRange.between(LocalDate.of(2019, 4, 1), LocalDate.of(2024, 3, 31)),
                DateRange.fromOnwards(LocalDate.of(2019, 4, 1)),
                new ContinuationPolicy.ContinueIndefinitely(),
                new InvalidationPolicy.RetroactiveFromFailureYear(new RuleId("REG-S203-FAIL-001")),
                provenance());
    }

    @Nested
    @DisplayName("two windows — the ss.201/204 shape, today")
    class TwoWindows {

        @Test
        void aRegimeOpenToEntrantsIsAlsoApplicable() {
            RegimeLifecycle lifecycle = grandfathered();

            assertThat(lifecycle.openToNewEntrants(new TaxYearId(2022))).isTrue();
            assertThat(lifecycle.applicable(new TaxYearId(2022))).isTrue();
            assertThat(lifecycle.grandfatheredOnly(new TaxYearId(2022))).isFalse();
        }

        @Test
        void afterEntryClosesTheRegimeStillApplies() {
            // The whole point: a taxpayer who elected in by 31 March 2024 keeps using it
            RegimeLifecycle lifecycle = grandfathered();

            assertThat(lifecycle.openToNewEntrants(new TaxYearId(2026))).isFalse();
            assertThat(lifecycle.applicable(new TaxYearId(2026))).isTrue();
            assertThat(lifecycle.grandfatheredOnly(new TaxYearId(2026))).isTrue();
        }

        @Test
        void beforeTheRegimeExistsItIsNeitherOpenNorApplicable() {
            RegimeLifecycle lifecycle = grandfathered();

            assertThat(lifecycle.applicable(new TaxYearId(2017))).isFalse();
            assertThat(lifecycle.grandfatheredOnly(new TaxYearId(2017))).isFalse();
        }

        @Test
        void theSunsetBoundaryIsInclusiveOfTheYearItEndsOn() {
            // In force to 31 March 2026. TY 2025-26 runs 1 Apr 2025 to 31 Mar 2026, so the window
            // covers all of it — an off-by-one year here retires a regime twelve months early.
            RegimeLifecycle sunset = new RegimeLifecycle(
                    DateRange.between(LocalDate.of(2019, 4, 1), LocalDate.of(2024, 3, 31)),
                    DateRange.between(LocalDate.of(2019, 4, 1), LocalDate.of(2026, 3, 31)),
                    new ContinuationPolicy.FallToDefault(S201),
                    new InvalidationPolicy.NoInvalidation(),
                    provenance());

            assertThat(sunset.applicable(new TaxYearId(2025))).isTrue();
            assertThat(sunset.grandfatheredOnly(new TaxYearId(2025))).isTrue();
        }

        @Test
        void afterSunsetTheRegimeIsNotApplicable() {
            RegimeLifecycle sunset = new RegimeLifecycle(
                    DateRange.between(LocalDate.of(2019, 4, 1), LocalDate.of(2024, 3, 31)),
                    DateRange.between(LocalDate.of(2019, 4, 1), LocalDate.of(2026, 3, 31)),
                    new ContinuationPolicy.FallToDefault(S201),
                    new InvalidationPolicy.NoInvalidation(),
                    provenance());

            // TY 2026-27 begins the day after the window closes
            assertThat(sunset.applicable(new TaxYearId(2026))).isFalse();
            assertThat(sunset.grandfatheredOnly(new TaxYearId(2026))).isFalse();
        }

        @Test
        void anEntryWindowOutsideTheInForceWindowIsRejected() {
            // A regime cannot be entered in a year it does not apply
            assertThatThrownBy(() ->
                            new RegimeLifecycle(
                                    DateRange.between(LocalDate.of(2019, 4, 1), LocalDate.of(2030, 3, 31)),
                                    DateRange.between(LocalDate.of(2019, 4, 1), LocalDate.of(2026, 3, 31)),
                                    new ContinuationPolicy.ContinueIndefinitely(),
                                    new InvalidationPolicy.NoInvalidation(),
                                    provenance()))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("cannot be entered in a year it does not apply");
        }

        @Test
        void bothEdgePoliciesAreRequired() {
            // Leaving either undefined makes a taxpayer's position unanswerable
            assertThatThrownBy(() ->
                            new RegimeLifecycle(
                                    DateRange.fromOnwards(LocalDate.of(2019, 4, 1)),
                                    DateRange.fromOnwards(LocalDate.of(2019, 4, 1)),
                                    null,
                                    new InvalidationPolicy.NoInvalidation(),
                                    provenance()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unanswerable");
        }
    }

    @Nested
    @DisplayName("ContinuationPolicy — what happens to an incumbent at sunset")
    class Continuation {

        @Test
        void continueIndefinitelyCitesNothingBecauseNothingEnds() {
            assertThat(new ContinuationPolicy.ContinueIndefinitely().citedRule()).isEmpty();
        }

        @Test
        void theOtherTwoMustCiteTheRuleThatEndsTheRegime() {
            // "Your regime ended and you moved" is a statement about the law a user may challenge
            assertThat(new ContinuationPolicy.FallToDefault(S201).citedRule()).contains(S201);
            assertThat(new ContinuationPolicy.MigrateTo(new RegimeCode("S204_SUCCESSOR"), S201).citedRule())
                    .contains(S201);

            assertThatThrownBy(() -> new ContinuationPolicy.FallToDefault(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ContinuationPolicy.MigrateTo(null, S201))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void everyBranchIsAccountedFor() {
            java.util.List<ContinuationPolicy> all = java.util.List.of(
                    new ContinuationPolicy.FallToDefault(S201),
                    new ContinuationPolicy.MigrateTo(new RegimeCode("S204_SUCCESSOR"), S201),
                    new ContinuationPolicy.ContinueIndefinitely());

            for (ContinuationPolicy policy : all) {
                String outcome = switch (policy) {
                    case ContinuationPolicy.FallToDefault ignored -> "default";
                    case ContinuationPolicy.MigrateTo ignored -> "successor";
                    case ContinuationPolicy.ContinueIndefinitely ignored -> "continues";
                };
                assertThat(outcome).isNotBlank();
            }
            assertThat(all).hasSize(3);
        }
    }

    @Nested
    @DisplayName("InvalidationPolicy — s.203(2) is the severe one")
    class Invalidation {

        private static final RuleId S203 = new RuleId("REG-S203-FAIL-001");

        @Test
        void retroactiveInvalidationMustCiteItsBasis() {
            // It can reopen years the taxpayer considered closed, so the citation is not optional
            assertThat(new InvalidationPolicy.RetroactiveFromFailureYear(S203).citedRule()).contains(S203);

            assertThatThrownBy(() -> new InvalidationPolicy.RetroactiveFromFailureYear(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("considered closed");
        }

        @Test
        void theThreeOutcomesAreDistinct() {
            // Modelling this as a boolean would either overstate a taxpayer's exposure or hide it
            assertThat(new InvalidationPolicy.RetroactiveFromFailureYear(S203))
                    .isNotEqualTo(new InvalidationPolicy.ProspectiveOnly(S203));
            assertThat(new InvalidationPolicy.NoInvalidation().citedRule()).isEmpty();
        }

        @Test
        void everyBranchIsAccountedFor() {
            java.util.List<InvalidationPolicy> all = java.util.List.of(
                    new InvalidationPolicy.RetroactiveFromFailureYear(S203),
                    new InvalidationPolicy.ProspectiveOnly(S203),
                    new InvalidationPolicy.NoInvalidation());

            for (InvalidationPolicy policy : all) {
                String scope = switch (policy) {
                    case InvalidationPolicy.RetroactiveFromFailureYear ignored -> "that year and after";
                    case InvalidationPolicy.ProspectiveOnly ignored -> "after only";
                    case InvalidationPolicy.NoInvalidation ignored -> "none";
                };
                assertThat(scope).isNotBlank();
            }
            assertThat(all).hasSize(3);
        }
    }

    // --------------------------------------------------------------- W-026b

    private static Provenance provenance(VerificationStatus status, String whatToCheck) {
        return new Provenance(
                URI.create("https://egazette.gov.in/WriteReadData/2026/271092.pdf"),
                "Income-tax Rules 2026, rule 136 + Table Sl. 4",
                DraftOrFinal.FINAL,
                LocalDate.of(2026, 9, 19),
                status,
                whatToCheck);
    }

    private static ConditionContext taxpayerWith(HeadCode... heads) {
        return new ConditionContext(
                true,
                PersonType.INDIVIDUAL,
                java.util.Map.of(AgeSubject.SELF, 40),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Set.of(heads),
                java.util.Set.of(),
                java.util.Set.of(),
                java.util.Optional.empty());
    }

    /**
     * s.202(4): binds subsequent years, one withdrawal, then ineligible unless business income
     * ceases — but only for a person who has business income at all.
     */
    private static BindingSpec s202Binding() {
        return new BindingSpec(
                true,
                1,
                new AfterWithdrawal.IneligibleUnless(
                        new Not(new HasIncomeUnderHead(HeadCode.BUSINESS_OR_PROFESSION)),
                        new ElectionRoute("S202_4_B")),
                false,
                java.util.List.of(new HasIncomeUnderHead(HeadCode.BUSINESS_OR_PROFESSION)));
    }

    @Nested
    @DisplayName("rule 136 — the option rides on the return, TY 2026-27")
    class Vehicle {

        /** REG-OPTOUT-FORM-001 — 01-rulebook.md:66, VERIFIED (S4, rule 136 + Table Sl. 4). */
        @Test
        void theOptOutIsMadeInTheReturnWithNoSeparateForm() {
            ElectionProtocol protocol = new ElectionProtocol(
                    ElectionKind.OPT_OUT,
                    ElectionVehicle.IN_THE_RETURN,
                    java.util.Optional.empty(),
                    new DeadlineSpec.DueDateUnder("263(1)"),
                    s202Binding(),
                    provenance(VerificationStatus.VERIFIED, null));

            assertThat(protocol.form()).isEmpty();
            assertThat(protocol.deadline()).isEqualTo(new DeadlineSpec.DueDateUnder("263(1)"));
        }

        @Test
        void namingAFormAlongsideTheReturnIsRejected() {
            // Sends the taxpayer looking for something rule 136 says does not exist
            assertThatThrownBy(() ->
                            new ElectionProtocol(
                                    ElectionKind.OPT_OUT,
                                    ElectionVehicle.IN_THE_RETURN,
                                    java.util.Optional.of(new FormRef("10-IEA", "Regime option")),
                                    new DeadlineSpec.DueDateUnder("263(1)"),
                                    s202Binding(),
                                    provenance(VerificationStatus.VERIFIED, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not exist");
        }

        @Test
        void prescribingAFormWithoutNamingItIsRejected() {
            assertThatThrownBy(() ->
                            new ElectionProtocol(
                                    ElectionKind.OPT_OUT,
                                    ElectionVehicle.PRESCRIBED_FORM,
                                    java.util.Optional.empty(),
                                    new DeadlineSpec.DueDateUnder("263(1)"),
                                    s202Binding(),
                                    provenance(VerificationStatus.VERIFIED, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("and not what");
        }

        @Test
        void theTY2025Form10IEAProtocolIsCarriedAsNeedsVerification() {
            // 01-rulebook.md:1236 marks Form 10-IEA unverified; the pack must say so rather than
            // presenting it with the same confidence as rule 136
            ElectionProtocol protocol = new ElectionProtocol(
                    ElectionKind.OPT_OUT,
                    ElectionVehicle.PRESCRIBED_FORM,
                    java.util.Optional.of(new FormRef("10-IEA", "Option under s.115BAC(6)")),
                    new DeadlineSpec.DueDateUnder("139(1)"),
                    s202Binding(),
                    provenance(
                            VerificationStatus.NEEDS_VERIFICATION,
                            "open the Income-tax Rules 1962 for the TY 2025-26 option form"));

            assertThat(protocol.provenance().status()).isEqualTo(VerificationStatus.NEEDS_VERIFICATION);
            assertThat(protocol.form()).map(FormRef::number).contains("10-IEA");
        }

        @Test
        void theDefaultRegimeNeedsNoVehicleAndNoDeadline() {
            ElectionProtocol protocol = new ElectionProtocol(
                    ElectionKind.DEFAULT_NO_ACTION,
                    ElectionVehicle.NONE,
                    java.util.Optional.empty(),
                    new DeadlineSpec.NoDeadline(),
                    BindingSpec.free(),
                    provenance(VerificationStatus.VERIFIED, null));

            assertThat(protocol.kind()).isEqualTo(ElectionKind.DEFAULT_NO_ACTION);
            assertThat(protocol.binding().isUnlimitedWithdrawals()).isTrue();
        }
    }

    @Nested
    @DisplayName("bindingAppliesWhen — one regime, two sections")
    class Binding {

        @Test
        void aPersonWithBusinessIncomeIsBound() {
            // s.202(4)(a)
            assertThat(s202Binding().appliesTo(taxpayerWith(HeadCode.BUSINESS_OR_PROFESSION))).isTrue();
        }

        @Test
        void aSalariedPersonIsNotBoundByTheSameRegime() {
            // s.202(4)(b) — REG-OPTOUT-YEARLY-001: a free choice every year.
            // The previous design modelled lock-in as an enum on the regime, which made these
            // look like two regimes rather than one section with two limbs.
            assertThat(s202Binding().appliesTo(taxpayerWith(HeadCode.SALARIES))).isFalse();
        }

        @Test
        void anEmptyBindingAppliesToEveryone() {
            assertThat(BindingSpec.free().appliesTo(taxpayerWith(HeadCode.SALARIES))).isTrue();
        }

        @Test
        void withdrawalCountsAreNamedRatherThanCompared() {
            assertThat(s202Binding().isIrrevocable()).isFalse();
            assertThat(s202Binding().isUnlimitedWithdrawals()).isFalse();
            assertThat(new BindingSpec(true, 0, new AfterWithdrawal.PermanentlyIneligible(), false, null)
                            .isIrrevocable())
                    .isTrue();
        }

        @Test
        void aWithdrawalCountBelowMinusOneIsRejected() {
            assertThatThrownBy(() ->
                            new BindingSpec(true, -2, new AfterWithdrawal.MayReElect(), false, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("-1 (unlimited)");
        }
    }

    @Nested
    @DisplayName("AfterWithdrawal — s.202(4)(a)(iv), the revival edge")
    class Withdrawal {

        @Test
        void permanentIneligibilityIsNotAlwaysPermanent() {
            // "never eligible" EXCEPT "where such person ceases to have any income from business
            // or profession". As a boolean, that taxpayer is told they are barred for life from a
            // regime they are entitled to.
            AfterWithdrawal.IneligibleUnless carveOut =
                    (AfterWithdrawal.IneligibleUnless) s202Binding().afterWithdrawal();

            assertThat(carveOut.revival().holds(taxpayerWith(HeadCode.BUSINESS_OR_PROFESSION))).isFalse();
            assertThat(carveOut.revival().holds(taxpayerWith(HeadCode.SALARIES))).isTrue();
            assertThat(carveOut.thenUse()).isEqualTo(new ElectionRoute("S202_4_B"));
        }

        @Test
        void aRevivalWithNoConditionOrNoRouteIsRejected() {
            assertThatThrownBy(() ->
                            new AfterWithdrawal.IneligibleUnless(null, new ElectionRoute("S202_4_B")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("wearing a different name");
            assertThatThrownBy(() ->
                            new AfterWithdrawal.IneligibleUnless(
                                    new Not(new HasIncomeUnderHead(HeadCode.BUSINESS_OR_PROFESSION)), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("how to act on it");
        }

        @Test
        void everyBranchIsAccountedFor() {
            java.util.List<AfterWithdrawal> all = java.util.List.of(
                    new AfterWithdrawal.MayReElect(),
                    new AfterWithdrawal.PermanentlyIneligible(),
                    s202Binding().afterWithdrawal());

            for (AfterWithdrawal outcome : all) {
                String verdict = switch (outcome) {
                    case AfterWithdrawal.MayReElect ignored -> "open";
                    case AfterWithdrawal.PermanentlyIneligible ignored -> "barred";
                    case AfterWithdrawal.IneligibleUnless ignored -> "barred, with an escape";
                };
                assertThat(verdict).isNotBlank();
            }
            assertThat(all).hasSize(3);
        }
    }
}
