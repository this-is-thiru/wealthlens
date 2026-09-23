package com.thiru.wealthlens.taxengine.rules.cap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The prescribed limits of Rules 2026 r.280(2), and the s.134 rent limb.
 *
 * <p>Every figure is copied from {@code 01-rulebook.md}. All are VERIFIED, read directly from the
 * Rules and the Act.
 */
class PerUnitPerPeriodTest {

    private static final BaseId CHILD = new BaseId("CHILD");
    private static final BaseId MONTH = new BaseId("MONTH");

    private static CapContext counts(Map<BaseId, Integer> counts) {
        return new CapContext(Map.of(), Map.of(), counts);
    }

    @Nested
    @DisplayName("SAL-ALW-CEA-2026 — ₹3,000/month/child, max two — 01-rulebook.md:494")
    class ChildrenEducationAllowance {

        private static final PerUnitPerPeriod CEA =
                PerUnitPerPeriod.perUnitPerPeriod(Money.of("3000"), CHILD, 2, MONTH);

        @Test
        void apply_twoChildrenAllYear_isSeventyTwoThousand() {
            // 3,000 × 2 children × 12 months
            CapResult result = CEA.apply(counts(Map.of(CHILD, 2, MONTH, 12)));

            assertThat(result.value()).isEqualTo(Money.of("72000"));
        }

        @Test
        void apply_oneChildAllYear_isThirtySixThousand() {
            assertThat(CEA.apply(counts(Map.of(CHILD, 1, MONTH, 12))).value())
                    .isEqualTo(Money.of("36000"));
        }

        @Test
        void apply_threeChildren_isCappedAtTwo() {
            // r.280(2) Sl. 7: "max two children". The third child claims nothing.
            assertThat(CEA.apply(counts(Map.of(CHILD, 3, MONTH, 12))).value())
                    .isEqualTo(CEA.apply(counts(Map.of(CHILD, 2, MONTH, 12))).value());
        }

        @Test
        void apply_partYear_multipliesTheMonthsGiven() {
            // Seven months, two children — the caller resolved the month count, this shape trusts it
            assertThat(CEA.apply(counts(Map.of(CHILD, 2, MONTH, 7))).value())
                    .isEqualTo(Money.of("42000"));
        }

        @Test
        void apply_noChildren_isNil() {
            assertThat(CEA.apply(counts(Map.of(CHILD, 0, MONTH, 12))).value()).isEqualTo(Money.ZERO);
        }

        @Test
        void apply_noMonths_isNilAndDoesNotDivide() {
            // O-10's divide-by-zero has no place to happen: zero periods is a multiplication by zero
            assertThat(CEA.apply(counts(Map.of(CHILD, 2, MONTH, 0))).value()).isEqualTo(Money.ZERO);
        }

        @Test
        void shapeName_statesTheLimitAsTheRulesDo() {
            assertThat(CEA.shapeName()).isEqualTo("3000.00 per MONTH per CHILD, max 2");
        }
    }

    @Nested
    @DisplayName("SAL-ALW-HOSTEL-2026 — ₹9,000/month/child, max two — 01-rulebook.md:495")
    class HostelAllowance {

        @Test
        void apply_twoChildrenAllYear_isTwoLakhSixteenThousand() {
            PerUnitPerPeriod hostel =
                    PerUnitPerPeriod.perUnitPerPeriod(Money.of("9000"), CHILD, 2, MONTH);

            // 9,000 × 2 × 12
            assertThat(hostel.apply(counts(Map.of(CHILD, 2, MONTH, 12))).value())
                    .isEqualTo(Money.of("216000"));
        }
    }

    @Nested
    @DisplayName("DED-80GG-001 limb (b) — ₹5,000 per month, no unit — 01-rulebook.md:791")
    class RentLimbWithNoUnit {

        private static final PerUnitPerPeriod LIMB_B =
                PerUnitPerPeriod.perPeriod(Money.of("5000"), MONTH);

        @Test
        void apply_allYear_isSixtyThousand() {
            assertThat(LIMB_B.apply(counts(Map.of(MONTH, 12))).value()).isEqualTo(Money.of("60000"));
        }

        @Test
        void apply_readsNoUnitCount() {
            // The context defines no unit at all, and the shape must not ask for one
            assertThat(LIMB_B.apply(counts(Map.of(MONTH, 5))).value()).isEqualTo(Money.of("25000"));
        }

        @Test
        void shapeName_omitsTheUnit() {
            assertThat(LIMB_B.shapeName()).isEqualTo("5000.00 per MONTH");
        }
    }

    @Nested
    @DisplayName("construction and failure")
    class Contract {

        @Test
        void construction_whenMaxUnitsIsSetWithNoUnit_isRejected() {
            assertThatThrownBy(() -> new PerUnitPerPeriod(Money.of("5000"), Optional.empty(), 2, MONTH))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("a maximum of what?");
        }

        @Test
        void construction_whenMaxUnitsIsZeroOrNegative_isRejected() {
            assertThatThrownBy(() ->
                            new PerUnitPerPeriod(Money.of("3000"), Optional.of(CHILD), 0, MONTH))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least 1");
        }

        @Test
        void apply_whenThePeriodCountIsMissing_failsLoudly() {
            // D-NOSILENT: not zero, which would look like a taxpayer with no eligible months
            assertThatThrownBy(() ->
                            PerUnitPerPeriod.perPeriod(Money.of("5000"), MONTH).apply(counts(Map.of())))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("MONTH");
        }

        @Test
        void apply_whenAUnitCountIsMissing_failsLoudly() {
            assertThatThrownBy(() ->
                            PerUnitPerPeriod.perUnitPerPeriod(Money.of("3000"), CHILD, 2, MONTH)
                                    .apply(counts(Map.of(MONTH, 12))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CHILD");
        }

        @Test
        void apply_whenACountIsNegative_isRejected() {
            assertThatThrownBy(() ->
                            PerUnitPerPeriod.perPeriod(Money.of("5000"), MONTH)
                                    .apply(counts(Map.of(MONTH, -1))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Negative period count");
        }
    }

    @Nested
    @DisplayName("the family is closed")
    class SealedFamily {

        /**
         * An exhaustive switch with no {@code default}. This does not merely pass — it <b>compiles</b>,
         * and that is the assertion: adding a seventh {@code CapShape} breaks this line, which is the
         * whole point of sealing the family.
         */
        @Test
        void everyCapShapeIsAccountedFor() {
            java.util.List<CapShape> all = java.util.List.of(
                    new Fixed(Money.of("75000")),
                    new PercentOfBase(
                            com.thiru.wealthlens.taxengine.rules.money.Rate.percent("30"), new BaseId("ANNUAL_VALUE")),
                    new SlabLadder(
                            java.util.List.of(
                                    new SlabLadder.Band(
                                            Money.ZERO,
                                            Optional.empty(),
                                            com.thiru.wealthlens.taxengine.rules.money.Rate.percent("5"))),
                            new BaseId("TOTAL_INCOME")),
                    new LeastOf(java.util.List.of(new LeastOf.Limb("only", new Fixed(Money.ZERO)))),
                    new AggregateWithCeiling(
                            java.util.List.of(new com.thiru.wealthlens.taxengine.rules.id.ComponentCode("LIC_PREMIUM")),
                            Money.of("150000"),
                            Map.of()),
                    PerUnitPerPeriod.perPeriod(Money.of("5000"), MONTH));

            for (CapShape shape : all) {
                String kind = switch (shape) {
                    case Fixed ignored -> "fixed";
                    case PercentOfBase ignored -> "percent";
                    case SlabLadder ignored -> "slab";
                    case LeastOf ignored -> "least";
                    case AggregateWithCeiling ignored -> "aggregate";
                    case PerUnitPerPeriod ignored -> "perUnit";
                };
                assertThat(kind).isNotBlank();
            }

            assertThat(all).hasSize(6);
        }
    }
}
