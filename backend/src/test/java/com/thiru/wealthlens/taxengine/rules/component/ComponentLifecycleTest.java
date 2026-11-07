package com.thiru.wealthlens.taxengine.rules.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import java.net.URI;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Component withdrawal, and the invariants that stop a registry row being quietly useless.
 *
 * <p>The behaviour under test is mostly refusal: a draft naming a dead code must not be dropped, a
 * code nobody consumes must not be accepted, and a component's successor must not disagree with
 * where its drafts migrate.
 */
class ComponentLifecycleTest {

    private static final ComponentCode HRA = new ComponentCode("HRA");
    private static final ComponentCode HOUSE_RENT_ALLOWANCE = new ComponentCode("HOUSE_RENT_ALLOWANCE");
    private static final RuleId SAL_HRA_001 = new RuleId("SAL-HRA-001");
    private static final RuleId SAL_HRA_004 = new RuleId("SAL-HRA-004");

    private static Provenance provenance() {
        return new Provenance(
                URI.create("https://egazette.gov.in/WriteReadData/2026/271092.pdf"),
                "Income-tax Rules 2026, r.279(1)",
                LocalDate.of(2026, 9, 19),
                VerificationLevel.SOURCE_CHECKED,
                null);
    }

    private static ComponentDefinition hra(ComponentLifecycle lifecycle, Set<RuleId> feedsInto, boolean informational) {
        return new ComponentDefinition(
                HRA,
                "House rent allowance",
                Optional.of(HeadCode.SALARIES),
                ComponentKind.SALARY,
                CashNature.CASH,
                TaxTreatment.TAXABLE,
                feedsInto,
                Set.of(new SalaryBaseInclusion(SAL_HRA_004, false)),
                informational,
                lifecycle,
                provenance());
    }

    private static ComponentDefinition hra() {
        return hra(ComponentLifecycle.openEndedFrom(LocalDate.of(2020, 4, 1)), Set.of(SAL_HRA_001), false);
    }

    @Nested
    @DisplayName("lifecycle validity")
    class Validity {

        @Test
        void aComponentIsLiveInAYearItsValidityCovers() {
            assertThat(hra().lifecycle().isLiveIn(new TaxYearId(2026))).isTrue();
        }

        @Test
        void aWithdrawnComponentIsNotLiveAfterwards() {
            ComponentLifecycle withdrawn = new ComponentLifecycle(
                    DateRange.between(LocalDate.of(2020, 4, 1), LocalDate.of(2025, 3, 31)),
                    Optional.empty(),
                    new DeprecationPolicy.HistoricalOnly());

            assertThat(withdrawn.isLiveIn(new TaxYearId(2024))).isTrue();
            assertThat(withdrawn.isLiveIn(new TaxYearId(2026))).isFalse();
        }

        @Test
        void aLifecycleMustSayWhatHappensOnWithdrawal() {
            // Without it, a draft naming a dead code has no defined behaviour
            assertThatThrownBy(() ->
                            new ComponentLifecycle(
                                    DateRange.fromOnwards(LocalDate.of(2020, 4, 1)), Optional.empty(), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("D-NOSILENT");
        }
    }

    @Nested
    @DisplayName("DeprecationPolicy — a draft is never silently dropped")
    class Withdrawal {

        @Test
        void requireUserCorrection_mustCarryActionableGuidance() {
            // A blank prompt blocks a draft without explaining how to unblock it
            assertThatThrownBy(() -> new DeprecationPolicy.RequireUserCorrection("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("what the user should do");
        }

        @Test
        void autoMigrate_mustNameASuccessorAndAFactor() {
            assertThatThrownBy(() -> new DeprecationPolicy.AutoMigrate(HOUSE_RENT_ALLOWANCE, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Rate.percent(\"100\")");
            assertThatThrownBy(() -> new DeprecationPolicy.AutoMigrate(null, Rate.percent("100")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void autoMigrate_forAPlainRenameIsOneHundredPercent() {
            DeprecationPolicy.AutoMigrate rename =
                    new DeprecationPolicy.AutoMigrate(HOUSE_RENT_ALLOWANCE, Rate.percent("100"));

            assertThat(rename.to()).isEqualTo(HOUSE_RENT_ALLOWANCE);
            assertThat(rename.conversionFactor()).isEqualTo(Rate.percent("100"));
        }

        @Test
        void aSuccessorThatDisagreesWithTheMigrationTargetIsRejected() {
            // A draft would move somewhere the registry does not point
            assertThatThrownBy(() ->
                            new ComponentLifecycle(
                                    DateRange.fromOnwards(LocalDate.of(2020, 4, 1)),
                                    Optional.of(HOUSE_RENT_ALLOWANCE),
                                    new DeprecationPolicy.AutoMigrate(
                                            new ComponentCode("SOMETHING_ELSE"), Rate.percent("100"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not point");
        }

        @Test
        void everyPolicyIsAccountedFor() {
            // Exhaustive switch, no default — a fourth policy breaks this line
            java.util.List<DeprecationPolicy> all = java.util.List.of(
                    new DeprecationPolicy.RequireUserCorrection("Re-enter this figure under its new code."),
                    new DeprecationPolicy.AutoMigrate(HOUSE_RENT_ALLOWANCE, Rate.percent("100")),
                    new DeprecationPolicy.HistoricalOnly());

            for (DeprecationPolicy policy : all) {
                String outcome = switch (policy) {
                    case DeprecationPolicy.RequireUserCorrection ignored -> "blocks";
                    case DeprecationPolicy.AutoMigrate ignored -> "migrates";
                    case DeprecationPolicy.HistoricalOnly ignored -> "keeps";
                };
                assertThat(outcome).isNotBlank();
            }
            assertThat(all).hasSize(3);
        }
    }

    @Nested
    @DisplayName("D-CASHFLAG — H-15 and O-13")
    class CashFlag {

        @Test
        void onlyCashCountsTowardTakeHome() {
            assertThat(hra().isCash()).isTrue();
        }

        @Test
        void aPerquisiteNeverReachesTakeHome() {
            // The reported bug: take-home included the annualised value of a car and its driver
            ComponentDefinition car = new ComponentDefinition(
                    new ComponentCode("PERQ_MOTOR_CAR"),
                    "Motor car",
                    Optional.of(HeadCode.SALARIES),
                    ComponentKind.PERQUISITE,
                    CashNature.NON_CASH,
                    TaxTreatment.TAXABLE,
                    Set.of(new RuleId("PERQ-CAR-2026")),
                    Set.of(),
                    false,
                    ComponentLifecycle.openEndedFrom(LocalDate.of(2026, 4, 1)),
                    provenance());

            assertThat(car.isCash()).isFalse();
        }

        @Test
        void cashOutflowIsNeitherIncomeNorTakeHome() {
            // EMPLOYEE_PF reduces cash but is not salary income, so it cannot be either of the others
            ComponentDefinition employeePf = new ComponentDefinition(
                    new ComponentCode("EMPLOYEE_PF"),
                    "Employee provident fund contribution",
                    Optional.of(HeadCode.SALARIES),
                    ComponentKind.EMPLOYEE_CONTRIBUTION,
                    CashNature.CASH_OUTFLOW,
                    TaxTreatment.DEDUCTIBLE,
                    Set.of(new RuleId("DED-80C-001")),
                    Set.of(),
                    false,
                    ComponentLifecycle.openEndedFrom(LocalDate.of(2026, 4, 1)),
                    provenance());

            assertThat(employeePf.isCash()).isFalse();
            assertThat(employeePf.cashNature()).isEqualTo(CashNature.CASH_OUTFLOW);
        }
    }

    @Nested
    @DisplayName("head — a qualifying payment is income under no head (D-130)")
    class Head {

        private ComponentDefinition tuitionFees(Optional<HeadCode> head, ComponentKind kind) {
            return new ComponentDefinition(new ComponentCode("TUITION_FEES"), "Tuition fees", head, kind,
                    CashNature.CASH_OUTFLOW, TaxTreatment.DEDUCTIBLE, Set.of(new RuleId("DED-SCHEDULE-XV")),
                    Set.of(), false, ComponentLifecycle.openEndedFrom(LocalDate.of(2026, 4, 1)), provenance());
        }

        @Test
        void componentDefinition_whenAQualifyingPaymentHasNoHead_isAccepted() {
            // When
            ComponentDefinition fees = tuitionFees(Optional.empty(), ComponentKind.QUALIFYING_PAYMENT);

            // Then
            assertThat(fees.head()).isEmpty();
        }

        @Test
        void componentDefinition_whenAQualifyingPaymentNamesAHead_isRejected() {
            // When / Then
            assertThatThrownBy(() -> tuitionFees(Optional.of(HeadCode.SALARIES), ComponentKind.QUALIFYING_PAYMENT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("income under no head");
        }

        @Test
        void componentDefinition_whenPayHasNoHead_isRejected() {
            // When / Then — only a qualifying payment may leave the partition
            assertThatThrownBy(() -> tuitionFees(Optional.empty(), ComponentKind.SALARY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("every other kind belongs to one");
        }
    }

    @Nested
    @DisplayName("reachability — invariant I3 extended")
    class Reachability {

        @Test
        void aComponentThatFeedsNoRuleIsRejected() {
            // A well-spelled code nobody consumes is a figure the user enters and the
            // computation ignores — the bug an enum could never have caught
            assertThatThrownBy(() ->
                            hra(ComponentLifecycle.openEndedFrom(LocalDate.of(2026, 4, 1)), Set.of(), false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("informationalOnly");
        }

        @Test
        void aComponentMarkedInformationalOnlyMayFeedNothing() {
            ComponentDefinition informational =
                    hra(ComponentLifecycle.openEndedFrom(LocalDate.of(2026, 4, 1)), Set.of(), true);

            assertThat(informational.feedsInto()).isEmpty();
            assertThat(informational.informationalOnly()).isTrue();
        }

        @Test
        void everyAttributeMustBeDeclared() {
            // Each is read rather than branched on, so none may be absent
            assertThatThrownBy(() ->
                            new ComponentDefinition(
                                    HRA, "House rent allowance", Optional.of(HeadCode.SALARIES), ComponentKind.SALARY,
                                    null, TaxTreatment.TAXABLE, Set.of(SAL_HRA_001), Set.of(), false,
                                    ComponentLifecycle.openEndedFrom(LocalDate.of(2026, 4, 1)), provenance()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("branched on");
        }

        @Test
        void salaryBaseForIsCarried() {
            // SAL-HRA-004's "basic + DA where the terms provide" is a set of components,
            // and W-062 has nowhere else to read it from
            assertThat(hra().salaryBaseFor()).containsExactly(new SalaryBaseInclusion(SAL_HRA_004, false));
        }
    }
}
