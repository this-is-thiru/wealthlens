package com.thiru.wealthlens.taxengine.rules.cap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.reason.Reason;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonCodes;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonFact;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The two cap shapes that exist, and the contract the other four will have to keep.
 *
 * <p>Expected values are copied from {@code 01-rulebook.md}, never computed here. Deriving a test's
 * expectation from the code under test proves only that the code agrees with itself.
 */
class CapShapeTest {

    private static final BaseId ANNUAL_VALUE = new BaseId("ANNUAL_VALUE");

    @Nested
    @DisplayName("Fixed")
    class FixedCap {

        /** SAL-STDDED-001 — 01-rulebook.md:373 — ₹75,000, VERIFIED for TY 2026-27. */
        @Test
        void apply_whenStandardDeduction_returnsTheStatedAmount() {
            // Given
            Fixed cap = new Fixed(Money.of("75000"));

            // When
            CapResult result = cap.apply(new CapContext(Map.of()));

            // Then
            assertThat(result.value()).isEqualTo(Money.of("75000"));
            assertThat(result.limitedBy()).isEqualTo("fixed");
        }

        @Test
        void apply_readsNothingFromTheContext() {
            // Given — a context that would throw on any lookup
            Fixed cap = new Fixed(Money.of("75000"));

            // When / Then
            assertThat(cap.apply(new CapContext(Map.of())).value()).isEqualTo(Money.of("75000"));
        }

        @Test
        void construction_whenAmountIsNull_isRejected() {
            assertThatThrownBy(() -> new Fixed(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("may not be null");
        }
    }

    @Nested
    @DisplayName("PercentOfBase")
    class PercentOfBaseCap {

        /**
         * HP-DED-30-001 — 01-rulebook.md:866 — 30% of the annual value, VERIFIED.
         *
         * <p>The worked figure is 01-rulebook.md:897: {@code 30% of 5,22,000 = 1,56,600}.
         */
        @Test
        void apply_whenThirtyPercentOfAnnualValue_matchesTheRulebook() {
            // Given
            PercentOfBase cap = new PercentOfBase(Rate.percent("30"), ANNUAL_VALUE);
            CapContext ctx = CapContext.of(ANNUAL_VALUE, Money.of("522000"));

            // When
            CapResult result = cap.apply(ctx);

            // Then
            assertThat(result.value()).isEqualTo(Money.of("156600"));
        }

        @Test
        void apply_whenBaseIsZero_isZeroRatherThanAnError() {
            // Given — 01-rulebook.md:908 records this case: 30% of 0 = 0
            PercentOfBase cap = new PercentOfBase(Rate.percent("30"), ANNUAL_VALUE);

            // When
            CapResult result = cap.apply(CapContext.of(ANNUAL_VALUE, Money.ZERO));

            // Then
            assertThat(result.value()).isEqualTo(Money.ZERO);
        }

        @Test
        void limitedBy_namesTheRateAndTheBase() {
            // Given
            PercentOfBase cap = new PercentOfBase(Rate.percent("30"), ANNUAL_VALUE);

            // When
            CapResult result = cap.apply(CapContext.of(ANNUAL_VALUE, Money.of("522000")));

            // Then — a trace line has to read as the rulebook reads
            assertThat(result.limitedBy()).isEqualTo("30% of ANNUAL_VALUE");
        }

        @Test
        void apply_whenTheBaseIsNotInTheContext_failsLoudly() {
            // Given — a pack referencing a base it never defined
            PercentOfBase cap = new PercentOfBase(Rate.percent("30"), new BaseId("NEVER_DEFINED"));

            // When / Then — D-NOSILENT: not zero, not a plausible wrong number
            assertThatThrownBy(() -> cap.apply(CapContext.of(ANNUAL_VALUE, Money.of("522000"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("NEVER_DEFINED")
                    .hasMessageContaining("ANNUAL_VALUE");
        }

        @Test
        void construction_whenRateOrBaseIsNull_isRejected() {
            assertThatThrownBy(() -> new PercentOfBase(null, ANNUAL_VALUE))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new PercentOfBase(Rate.percent("30"), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /** W-327a1 (D-679): the shapes that reach a trace line on their own publish their reason as figures. */
    @Nested
    class Reasons {

        private static final BaseId SALARY = new BaseId("SALARY");

        private static final BaseId CONTRIBUTIONS = new BaseId("CONTRIBUTIONS");

        @Test
        void apply_whenFixed_givesCapFixedWithTheAmount() {
            // When
            CapResult result = new Fixed(Money.of("75000")).apply(new CapContext(Map.of()));

            // Then
            assertThat(result.reason()).contains(new Reason(ReasonCodes.CAP_FIXED,
                    Map.of("amount", new ReasonFact.MoneyFact(Money.of("75000")))));
        }

        @Test
        void apply_whenPercentOfBase_givesCapShareWithTheRateNotTheBase() {
            // When
            CapResult result = new PercentOfBase(Rate.percent("30"), ANNUAL_VALUE)
                    .apply(CapContext.of(ANNUAL_VALUE, Money.of("522000")));

            // Then
            assertThat(result.reason()).contains(new Reason(ReasonCodes.CAP_SHARE,
                    Map.of("share", new ReasonFact.ShareFact(Rate.percent("30")))));
        }

        @Test
        void apply_whenAmountInExcess_givesTheThresholdsFigureForTheYear() {
            // Given — PERQ-EMPLOYER-CONTRIBUTION's shape: contributions above a fixed 7,50,000
            AmountInExcess excess = new AmountInExcess(CONTRIBUTIONS, new Fixed(Money.of("750000")));

            // When — 8,00,000 contributed: 50,000 above
            CapResult result = excess.apply(CapContext.of(CONTRIBUTIONS, Money.of("800000")));

            // Then
            assertThat(result.value()).isEqualTo(Money.of("50000"));
            assertThat(result.reason()).contains(new Reason(ReasonCodes.CAP_IN_EXCESS,
                    Map.of("threshold", new ReasonFact.MoneyFact(Money.of("750000")))));
        }

        @Test
        void apply_whenTheAggregateCeilingBinds_givesTheCeiling() {
            // Given — 1,00,000 + 80,000 = 1,80,000 against a 1,50,000 ceiling
            ComponentCode nps = new ComponentCode("EMPLOYEE_NPS");
            ComponentCode pf = new ComponentCode("EMPLOYEE_PF");
            AggregateWithCeiling aggregate = new AggregateWithCeiling(List.of(nps, pf), Money.of("150000"), Map.of());

            // When
            CapResult result = aggregate.apply(CapContext.ofContributions(Map.of(nps, Money.of("100000"),
                    pf, Money.of("80000"))));

            // Then
            assertThat(result.reason()).contains(new Reason(ReasonCodes.CAP_AGGREGATE_CEILING,
                    Map.of("ceiling", new ReasonFact.MoneyFact(Money.of("150000")))));
        }

        @Test
        void apply_whenTheAggregateIsWithinTheCeiling_givesActualAmounts() {
            // Given — 1,00,000 + 20,000 = 1,20,000, under 1,50,000
            ComponentCode nps = new ComponentCode("EMPLOYEE_NPS");
            ComponentCode pf = new ComponentCode("EMPLOYEE_PF");
            AggregateWithCeiling aggregate = new AggregateWithCeiling(List.of(nps, pf), Money.of("150000"), Map.of());

            // When
            CapResult result = aggregate.apply(CapContext.ofContributions(Map.of(nps, Money.of("100000"),
                    pf, Money.of("20000"))));

            // Then
            assertThat(result.reason()).contains(new Reason(ReasonCodes.CAP_ACTUAL_AMOUNTS, Map.of()));
        }

        @Test
        void apply_whenLeastOf_givesNoReasonBecauseItsLimbIsWordedByName() {
            // Given
            LeastOf least = new LeastOf(List.of(
                    new LeastOf.Limb("limit", new Fixed(Money.of("2000000"))),
                    new LeastOf.Limb("half", new PercentOfBase(Rate.percent("50"), SALARY))));

            // When
            CapResult result = least.apply(CapContext.of(SALARY, Money.of("100000")));

            // Then
            assertThat(result.limitedBy()).isEqualTo("half");
            assertThat(result.reason()).isEmpty();
        }
    }

    @Nested
    @DisplayName("CapResult")
    class Result {

        @Test
        void construction_whenNothingNamesTheLimit_isRejected() {
            // A cap that cannot say what bound it cannot be explained to a taxpayer
            assertThatThrownBy(() -> new CapResult(Money.ZERO, "  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must name what limited it");
            assertThatThrownBy(() -> new CapResult(Money.ZERO, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("CapContext")
    class Context {

        @Test
        void bases_areDefensivelyCopied() {
            // Given
            java.util.Map<BaseId, Money> mutable = new java.util.HashMap<>();
            mutable.put(ANNUAL_VALUE, Money.of("522000"));
            CapContext ctx = new CapContext(mutable);

            // When
            mutable.put(ANNUAL_VALUE, Money.of("999999"));

            // Then — a rule pack's inputs cannot be edited after a computation reads them
            assertThat(ctx.base(ANNUAL_VALUE)).isEqualTo(Money.of("522000"));
        }
    }

    @Nested
    @DisplayName("BaseId")
    class Identifier {

        @Test
        void construction_acceptsAValidCodeAndRejectsAnInvalidOne() {
            assertThat(new BaseId("SALARY_BASIC_PLUS_DA").value()).isEqualTo("SALARY_BASIC_PLUS_DA");
            assertThatThrownBy(() -> new BaseId("annual value"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("upper-case with underscores");
        }
    }

    /**
     * W-155c2: the bases each shape looks up in {@code CapContext.bases}, read off each shape's {@code apply} by hand —
     * {@code ctx.base(…)} only; counts, locations and contributions are other maps.
     */
    @Nested
    @DisplayName("amountsRead")
    class AmountsRead {

        private static final BaseId PAYMENT = new BaseId("PAYMENT_RECEIVED");
        private static final BaseId SALARY = new BaseId("SALARY");
        private static final BaseId PLACE = new BaseId("PLACE");
        private static final BaseId MONTH = new BaseId("MONTH");
        private static final BaseId CHILD = new BaseId("CHILD");

        @Test
        void amountsRead_whenFixed_isEmpty() {
            assertThat(new Fixed(Money.of("75000")).amountsRead()).isEmpty();
        }

        @Test
        void amountsRead_whenPercentOfBase_isItsBase() {
            assertThat(new PercentOfBase(Rate.percent("30"), ANNUAL_VALUE).amountsRead()).containsExactly(ANNUAL_VALUE);
        }

        @Test
        void amountsRead_whenSlabLadder_isItsBase() {
            // Given
            SlabLadder ladder = new SlabLadder(List.of(new SlabLadder.Band(Money.ZERO, Optional.empty(),
                    Rate.percent("10"))), SALARY);

            // When / Then
            assertThat(ladder.amountsRead()).containsExactly(SALARY);
        }

        @Test
        void amountsRead_whenLeastOf_isEveryLimbsBases() {
            // Given — RET-GRATUITY-OTHER's shape: received, a fixed limit, half a month's salary per year
            LeastOf least = new LeastOf(List.of(
                    new LeastOf.Limb("received", new PercentOfBase(Rate.percent("100"), PAYMENT)),
                    new LeastOf.Limb("limit", new Fixed(Money.of("2000000"))),
                    new LeastOf.Limb("half", new PercentOfBase(Rate.percent("50"), SALARY))));

            // When / Then
            assertThat(least.amountsRead()).containsExactlyInAnyOrder(PAYMENT, SALARY);
        }

        @Test
        void amountsRead_whenAggregateWithCeiling_isItsSubCapsBasesNotItsContributions() {
            // Given
            ComponentCode nps = new ComponentCode("EMPLOYEE_NPS");
            AggregateWithCeiling aggregate = new AggregateWithCeiling(List.of(nps, new ComponentCode("EMPLOYEE_PF")),
                    Money.of("150000"), Map.of(nps, new PercentOfBase(Rate.percent("10"), SALARY)));

            // When / Then
            assertThat(aggregate.amountsRead()).containsExactly(SALARY);
        }

        @Test
        void amountsRead_whenPerUnitPerPeriod_isEmptyBecauseItReadsCounts() {
            assertThat(new PerUnitPerPeriod(Money.of("3000"), Optional.of(CHILD), 2, MONTH).amountsRead()).isEmpty();
        }

        @Test
        void amountsRead_whenAmountInExcess_isItsAmountAndItsThresholdsBases() {
            // Given
            AmountInExcess excess = new AmountInExcess(PAYMENT, new PercentOfBase(Rate.percent("10"), SALARY));

            // When / Then
            assertThat(excess.amountsRead()).containsExactlyInAnyOrder(PAYMENT, SALARY);
        }

        @Test
        void amountsRead_whenPercentOfBaseByLocation_isItsBaseNotItsLocation() {
            // Given
            PercentOfBaseByLocation byCity = new PercentOfBaseByLocation(SALARY, PLACE,
                    List.of(new PercentOfBaseByLocation.Band(Set.of("MUMBAI"), Rate.percent("50"))),
                    Rate.percent("40"));

            // When / Then
            assertThat(byCity.amountsRead()).containsExactly(SALARY);
        }

        @Test
        void amountsRead_whenPercentOfBaseByPopulation_isItsBaseNotItsPopulation() {
            // Given
            PercentOfBaseByPopulation byPopulation = new PercentOfBaseByPopulation(SALARY, PLACE,
                    List.of(new PercentOfBaseByPopulation.Band(1_500_000, Rate.percent("7.5"))), Rate.percent("5"));

            // When / Then
            assertThat(byPopulation.amountsRead()).containsExactly(SALARY);
        }
    }

    /** {@link CapShape#countsRead}: the ids a shape looks up with {@code ctx.count(…)} only (W-156e). */
    @Nested
    @DisplayName("countsRead")
    class CountsRead {

        private static final BaseId PAYMENT = new BaseId("PAYMENT_RECEIVED");
        private static final BaseId SALARY = new BaseId("SALARY");
        private static final BaseId PLACE = new BaseId("PLACE");
        private static final BaseId MONTH = new BaseId("MONTH");
        private static final BaseId CHILD = new BaseId("CHILD");

        @Test
        void countsRead_whenFixed_isEmpty() {
            assertThat(new Fixed(Money.of("75000")).countsRead()).isEmpty();
        }

        @Test
        void countsRead_whenPercentOfBase_isEmpty() {
            assertThat(new PercentOfBase(Rate.percent("30"), ANNUAL_VALUE).countsRead()).isEmpty();
        }

        @Test
        void countsRead_whenSlabLadder_isEmpty() {
            // Given
            SlabLadder ladder = new SlabLadder(List.of(new SlabLadder.Band(Money.ZERO, Optional.empty(),
                    Rate.percent("10"))), SALARY);

            // When / Then
            assertThat(ladder.countsRead()).isEmpty();
        }

        @Test
        void countsRead_whenPerUnitPerPeriod_isItsUnitAndItsPeriod() {
            assertThat(new PerUnitPerPeriod(Money.of("3000"), Optional.of(CHILD), 2, MONTH).countsRead())
                    .containsExactlyInAnyOrder(CHILD, MONTH);
        }

        @Test
        void countsRead_whenPerPeriodOnly_isItsPeriod() {
            assertThat(PerUnitPerPeriod.perPeriod(Money.of("5000"), MONTH).countsRead()).containsExactly(MONTH);
        }

        @Test
        void countsRead_whenLeastOf_isEveryLimbsCounts() {
            // Given — SAL-ALW-CHILDREN-EDUCATION's shape: received, and an amount per month per child
            LeastOf least = new LeastOf(List.of(
                    new LeastOf.Limb("received", new PercentOfBase(Rate.percent("100"), PAYMENT)),
                    new LeastOf.Limb("per child", new PerUnitPerPeriod(Money.of("3000"), Optional.of(CHILD), 2,
                            MONTH))));

            // When / Then
            assertThat(least.countsRead()).containsExactlyInAnyOrder(CHILD, MONTH);
        }

        @Test
        void countsRead_whenAggregateWithCeiling_isItsSubCapsCounts() {
            // Given
            ComponentCode allowance = new ComponentCode("CHILDREN_EDUCATION");
            AggregateWithCeiling aggregate = new AggregateWithCeiling(List.of(allowance,
                    new ComponentCode("EMPLOYEE_PF")), Money.of("150000"),
                    Map.of(allowance, PerUnitPerPeriod.perPeriod(Money.of("3000"), MONTH)));

            // When / Then
            assertThat(aggregate.countsRead()).containsExactly(MONTH);
        }

        @Test
        void countsRead_whenAmountInExcess_isItsThresholdsCounts() {
            // Given
            AmountInExcess excess = new AmountInExcess(PAYMENT, PerUnitPerPeriod.perPeriod(Money.of("5000"), MONTH));

            // When / Then
            assertThat(excess.countsRead()).containsExactly(MONTH);
        }

        @Test
        void countsRead_whenPercentOfBaseByLocation_isEmpty() {
            // Given
            PercentOfBaseByLocation byCity = new PercentOfBaseByLocation(SALARY, PLACE,
                    List.of(new PercentOfBaseByLocation.Band(Set.of("MUMBAI"), Rate.percent("50"))),
                    Rate.percent("40"));

            // When / Then
            assertThat(byCity.countsRead()).isEmpty();
        }

        @Test
        void countsRead_whenPercentOfBaseByPopulation_isItsPopulation() {
            // Given
            PercentOfBaseByPopulation byPopulation = new PercentOfBaseByPopulation(SALARY, PLACE,
                    List.of(new PercentOfBaseByPopulation.Band(1_500_000, Rate.percent("7.5"))), Rate.percent("5"));

            // When / Then
            assertThat(byPopulation.countsRead()).containsExactly(PLACE);
        }
    }

    @Test
    void moreToRaiseBy_whenAPercentageOfABase_dividesByItsRate() {
        // When / Then — 30 % of a base: 30,000 more needs 1,00,000 more of the base (W-326a)
        PercentOfBase share = new PercentOfBase(Rate.percent("30"),
                new BaseId("AMOUNT_CLAIMED"));
        CapContext ctx = CapContext.of(new BaseId("AMOUNT_CLAIMED"),
                Money.of("10000"));
        assertThat(share.moreToRaiseBy(
                Money.of("30000"), ctx))
                .isEqualByComparingTo(Money.of("100000"));
    }

    @Test
    void moreToRaiseBy_whenFixed_isRefusedAsNothingRaisesIt() {
        // When / Then
        Fixed fixed = new Fixed(Money.of("10000"));
        assertThat(fixed.raisedBy(CapContext.of(
                new BaseId("INTEREST"),
                Money.of("1")))).isEmpty();
        assertThatThrownBy(() -> fixed.moreToRaiseBy(
                Money.of("1"), CapContext.of(
                        new BaseId("INTEREST"),
                        Money.of("1"))))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * The shapes no single amount raises, beyond {@code Fixed} above: none is a percentage of one base or an excess of
     * one amount, so each names no input and refuses to say how much more of one it needs (W-326a, W-326b).
     */
    private static List<CapShape> shapesNoSingleAmountRaises() {
        BaseId salary = new BaseId("SALARY");
        ComponentCode nps = new ComponentCode("EMPLOYEE_NPS");
        return List.of(
                new PerUnitPerPeriod(Money.of("3000"), Optional.of(new BaseId("CHILD")), 2, new BaseId("MONTH")),
                new SlabLadder(List.of(new SlabLadder.Band(Money.ZERO, Optional.empty(), Rate.percent("10"))), salary),
                new PercentOfBaseByPopulation(salary, new BaseId("PLACE"),
                        List.of(new PercentOfBaseByPopulation.Band(1_500_000, Rate.percent("7.5"))), Rate.percent("5")),
                new LeastOf(List.of(new LeastOf.Limb("limit", new Fixed(Money.of("2000000"))),
                        new LeastOf.Limb("half", new PercentOfBase(Rate.percent("50"), salary)))),
                new AggregateWithCeiling(List.of(nps, new ComponentCode("EMPLOYEE_PF")), Money.of("150000"),
                        Map.of(nps, new PercentOfBase(Rate.percent("10"), salary))));
    }

    @Test
    void raisingInput_whenNoSingleAmountRaisesTheShape_isEmpty() {
        // Given
        CapContext ctx = CapContext.of(new BaseId("SALARY"), Money.of("1"));

        // When / Then
        assertThat(shapesNoSingleAmountRaises()).hasSize(5).allSatisfy(shape -> {
            assertThat(shape.raisingInput()).isEmpty();
            assertThat(shape.raisedBy(ctx)).isEmpty();
        });
    }

    @Test
    void moreToRaiseBy_whenNoSingleAmountRaisesTheShape_isRefused() {
        // Given
        CapContext ctx = CapContext.of(new BaseId("SALARY"), Money.of("1"));

        // When / Then
        assertThat(shapesNoSingleAmountRaises()).hasSize(5).allSatisfy(shape ->
                assertThatThrownBy(() -> shape.moreToRaiseBy(Money.of("1"), ctx))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("No single amount raises a " + shape.shapeName()));
    }
}
