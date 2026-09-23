package com.thiru.wealthlens.taxengine.rules.cap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * T5-1, T5-2 and T5-3 from {@code 08-golden-tests.md}, copied figure for figure.
 *
 * <p>T5-3 is the one that matters most: the wrong ordering is ₹60,000 too generous and no single
 * step of it looks wrong.
 */
class AggregateWithCeilingTest {

    private static final BaseId SALARY_BASE = new BaseId("SALARY_BASE");
    private static final ComponentCode EMPLOYEE_PF = new ComponentCode("EMPLOYEE_PF");
    private static final ComponentCode LIC_PREMIUM = new ComponentCode("LIC_PREMIUM");
    private static final ComponentCode TUITION_FEES = new ComponentCode("TUITION_FEES");
    private static final ComponentCode EMPLOYEE_NPS = new ComponentCode("EMPLOYEE_NPS");

    /** DED-80C-001 ceiling ₹1,50,000; DED-80C-NPS-CAP-001 sub-caps NPS at 10% of salary. */
    private static AggregateWithCeiling scheduleXv(List<ComponentCode> contributors) {
        return new AggregateWithCeiling(
                contributors,
                Money.of("150000"),
                Map.of(EMPLOYEE_NPS, new PercentOfBase(Rate.percent("10"), SALARY_BASE)));
    }

    /** The same ceiling with no sub-cap declared — for contributors that have none. */
    private static AggregateWithCeiling plainCeiling(List<ComponentCode> contributors) {
        return new AggregateWithCeiling(contributors, Money.of("150000"), Map.of());
    }

    private static CapContext context(Money salary, Map<ComponentCode, Money> contributions) {
        return new CapContext(Map.of(SALARY_BASE, salary), contributions, Map.of());
    }

    @Nested
    @DisplayName("T5-3 — sub-cap ordering, 08-golden-tests.md:492")
    class SubCapOrdering {

        /** Basic ₹4,00,000. Employee NPS ₹1,50,000. Nothing else. */
        private static AggregateOutcome outcome() {
            return scheduleXv(List.of(EMPLOYEE_NPS))
                    .applyDetailed(context(Money.of("400000"), Map.of(EMPLOYEE_NPS, Money.of("150000"))));
        }

        @Test
        void subCapIsAppliedBeforeTheCeiling() {
            // 10% × 4,00,000 = 40,000, which is well under the 1,50,000 ceiling
            assertThat(outcome().allowed()).isEqualTo(Money.of("40000"));
        }

        @Test
        void theCeilingDoesNotBind() {
            // The wrong order would report the ceiling binding at 1,50,000
            assertThat(outcome().limitedBy()).isEqualTo("actual amounts");
        }

        @Test
        void theResidueIsWhatTheSubCapExcluded() {
            // 1,50,000 − 40,000 = 1,10,000, which s.124(3) then deducts, capped at 50,000
            assertThat(outcome().subCapResidue()).containsEntry(EMPLOYEE_NPS, Money.of("110000"));
        }

        @Test
        void theWrongOrderWouldHaveLeftNoResidue() {
            // Ceiling first gives min(1,50,000, 1,50,000) = 1,50,000, residue 0, total 1,50,000 —
            // ₹60,000 too generous once s.124(3)'s ₹50,000 is added to the correct ₹40,000
            AggregateOutcome result = outcome();

            assertThat(result.allowed()).isNotEqualTo(Money.of("150000"));
            assertThat(result.subCapResidue().get(EMPLOYEE_NPS)).isGreaterThan(Money.ZERO);
        }
    }

    @Nested
    @DisplayName("T5-1 — the full Schedule XV set, 08-golden-tests.md:456")
    class FullScheduleXv {

        /** Basic ₹10,00,000, no DA. PF ₹1,20,000. LIC ₹30,000. Tuition ₹60,000. NPS ₹1,50,000. */
        private static AggregateOutcome outcome() {
            Map<ComponentCode, Money> contributions = new LinkedHashMap<>();
            contributions.put(EMPLOYEE_PF, Money.of("120000"));
            contributions.put(LIC_PREMIUM, Money.of("30000"));
            contributions.put(TUITION_FEES, Money.of("60000"));
            contributions.put(EMPLOYEE_NPS, Money.of("150000"));

            return scheduleXv(List.of(EMPLOYEE_PF, LIC_PREMIUM, TUITION_FEES, EMPLOYEE_NPS))
                    .applyDetailed(context(Money.of("1000000"), contributions));
        }

        @Test
        void npsIsSubCappedAtTenPercentOfSalary() {
            // DED-80C-NPS-CAP-001: min(1,50,000 , 1,00,000) = 1,00,000
            assertThat(outcome().capped()).containsEntry(EMPLOYEE_NPS, Money.of("100000"));
        }

        @Test
        void theOtherContributionsAreUncapped() {
            assertThat(outcome().capped())
                    .containsEntry(EMPLOYEE_PF, Money.of("120000"))
                    .containsEntry(LIC_PREMIUM, Money.of("30000"))
                    .containsEntry(TUITION_FEES, Money.of("60000"));
        }

        @Test
        void theRawAggregateIsThreeLakhTenThousand() {
            assertThat(outcome().aggregate()).isEqualTo(Money.of("310000"));
        }

        @Test
        void theDeductionIsTheCeiling() {
            assertThat(outcome().allowed()).isEqualTo(Money.of("150000"));
            assertThat(outcome().limitedBy()).isEqualTo("aggregate ceiling");
            assertThat(outcome().headroom()).isEqualTo(Money.ZERO);
        }

        @Test
        void theNpsResidueIsFiftyThousand() {
            // 1,50,000 − 1,00,000 = 50,000, on which s.124(3) is computed
            assertThat(outcome().subCapResidue()).containsEntry(EMPLOYEE_NPS, Money.of("50000"));
        }
    }

    @Nested
    @DisplayName("T5-2 — the H-7 regression, 08-golden-tests.md:478")
    class ProvidentFundOmitted {

        @Test
        void theDeductionIsUnchangedButTheAggregateIsNot() {
            // Given — the same facts, but employee PF is not treated as a Schedule XV item
            Map<ComponentCode, Money> withoutPf = new LinkedHashMap<>();
            withoutPf.put(LIC_PREMIUM, Money.of("30000"));
            withoutPf.put(TUITION_FEES, Money.of("60000"));
            withoutPf.put(EMPLOYEE_NPS, Money.of("150000"));

            // When
            AggregateOutcome result =
                    scheduleXv(List.of(LIC_PREMIUM, TUITION_FEES, EMPLOYEE_NPS))
                            .applyDetailed(context(Money.of("1000000"), withoutPf));

            // Then — the tax figure is identical, which is why this bug is invisible in it
            assertThat(result.allowed()).isEqualTo(Money.of("150000"));

            // ...and the trace differs by exactly the omitted contribution
            assertThat(result.aggregate()).isEqualTo(Money.of("190000"));
            assertThat(FullScheduleXv.outcome().aggregate().minus(result.aggregate()))
                    .isEqualTo(Money.of("120000"));
            assertThat(result.capped()).doesNotContainKey(EMPLOYEE_PF);
        }
    }

    @Nested
    @DisplayName("headroom and construction")
    class Contract {

        @Test
        void headroom_isWhatRemainsOfTheCeiling() {
            AggregateOutcome result =
                    plainCeiling(List.of(LIC_PREMIUM))
                            .applyDetailed(context(Money.of("400000"), Map.of(LIC_PREMIUM, Money.of("30000"))));

            assertThat(result.headroom()).isEqualTo(Money.of("120000"));
        }

        @Test
        void headroom_isNeverNegative() {
            AggregateOutcome result =
                    plainCeiling(List.of(LIC_PREMIUM))
                            .applyDetailed(context(Money.of("400000"), Map.of(LIC_PREMIUM, Money.of("900000"))));

            assertThat(result.headroom()).isEqualTo(Money.ZERO);
        }

        @Test
        void construction_whenAContributorIsListedTwice_isRejected() {
            assertThatThrownBy(() ->
                            new AggregateWithCeiling(
                                    List.of(LIC_PREMIUM, LIC_PREMIUM), Money.of("150000"), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("counted twice");
        }

        @Test
        void construction_whenASubCapNamesANonContributor_isRejected() {
            assertThatThrownBy(() ->
                            new AggregateWithCeiling(
                                    List.of(LIC_PREMIUM),
                                    Money.of("150000"),
                                    Map.of(EMPLOYEE_NPS, new Fixed(Money.ZERO))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("never apply");
        }

        @Test
        void apply_whenAContributionIsMissingFromTheContext_failsLoudly() {
            assertThatThrownBy(() ->
                            plainCeiling(List.of(LIC_PREMIUM))
                                    .applyDetailed(context(Money.of("400000"), Map.of())))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("LIC_PREMIUM");
        }
    }
}
