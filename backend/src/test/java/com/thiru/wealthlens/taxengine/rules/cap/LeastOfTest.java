package com.thiru.wealthlens.taxengine.rules.cap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The house rent allowance worked example, the standard deduction's salary cap, and the tie rule.
 *
 * <p>Expected values are copied from {@code 01-rulebook.md}, never computed here.
 */
class LeastOfTest {

    private static final BaseId SALARY_BASE = new BaseId("SALARY_BASE");

    private static LeastOf.Limb limb(String name, String amount) {
        return new LeastOf.Limb(name, new Fixed(Money.of(amount)));
    }

    @Nested
    @DisplayName("SAL-HRA-001 — 01-rulebook.md:429-441, TY 2026-27, Bengaluru, old regime")
    class HouseRentAllowance {

        /** Salary base = basic ₹12,00,000 + DA ₹1,20,000 (SAL-HRA-004). */
        private static final Money SALARY = Money.of("1320000");

        /**
         * limb (a) allowance received ₹6,00,000; limb (b) rent less 10% of salary ₹2,28,000;
         * limb (c) 50% of salary ₹6,60,000 (Bengaluru is in SAL-HRA-METRO-2026).
         */
        private static LeastOf hraExemption(String metroPercent) {
            return new LeastOf(List.of(
                    limb("received", "600000"),
                    limb("rent less 10% of salary", "228000"),
                    new LeastOf.Limb(
                            "percentage of salary",
                            new PercentOfBase(Rate.percent(metroPercent), SALARY_BASE))));
        }

        @Test
        void apply_atFiftyPercent_isTwoLakhTwentyEightThousand() {
            // When
            CapResult result = hraExemption("50").apply(CapContext.of(SALARY_BASE, SALARY));

            // Then — 01-rulebook.md:440: exemption = least of the three = 2,28,000
            assertThat(result.value()).isEqualTo(Money.of("228000"));
        }

        @Test
        void apply_namesLimbBAsTheBindingConstraint() {
            // Then — this is what makes "paying more rent would help" a true statement
            assertThat(hraExemption("50").apply(CapContext.of(SALARY_BASE, SALARY)).limitedBy())
                    .isEqualTo("rent less 10% of salary");
        }

        @Test
        void apply_atFortyPercent_isUnchanged() {
            // 01-rulebook.md:449 — the unverified four-city list leaves limb (c) at 5,28,000,
            // still above limb (b), so the exemption is unchanged
            CapResult result = hraExemption("40").apply(CapContext.of(SALARY_BASE, SALARY));

            assertThat(result.value()).isEqualTo(Money.of("228000"));
            assertThat(result.limitedBy()).isEqualTo("rent less 10% of salary");
        }

        @Test
        void apply_whenLimbCIsSmallest_reportsLimbC() {
            // Given — a very low salary base makes the percentage limb bind instead
            CapResult result = hraExemption("50").apply(CapContext.of(SALARY_BASE, Money.of("100000")));

            // Then — 50% of 1,00,000 = 50,000, below both other limbs
            assertThat(result.value()).isEqualTo(Money.of("50000"));
            assertThat(result.limitedBy()).isEqualTo("percentage of salary");
        }
    }

    @Nested
    @DisplayName("SAL-STDDED-003 — the standard deduction capped at salary")
    class StandardDeductionSalaryCap {

        /**
         * 01-rulebook.md:373,375 — ₹75,000 (SAL-STDDED-001), capped at the salary itself so it
         * cannot create or enlarge a loss under the head. Shape assigned by the M1.3 erratum.
         */
        private static LeastOf standardDeduction() {
            return new LeastOf(List.of(
                    limb("statutory amount", "75000"),
                    new LeastOf.Limb(
                            "salary", new PercentOfBase(Rate.percent("100"), SALARY_BASE))));
        }

        @Test
        void apply_whenSalaryExceedsTheStatutoryAmount_isTheStatutoryAmount() {
            CapResult result = standardDeduction().apply(CapContext.of(SALARY_BASE, Money.of("1200000")));

            assertThat(result.value()).isEqualTo(Money.of("75000"));
            assertThat(result.limitedBy()).isEqualTo("statutory amount");
        }

        @Test
        void apply_whenSalaryIsBelowTheStatutoryAmount_isTheSalary() {
            // A part-year joiner earning ₹60,000 deducts ₹60,000, not ₹75,000 — no loss under the head
            CapResult result = standardDeduction().apply(CapContext.of(SALARY_BASE, Money.of("60000")));

            assertThat(result.value()).isEqualTo(Money.of("60000"));
            assertThat(result.limitedBy()).isEqualTo("salary");
        }
    }

    @Nested
    @DisplayName("the contract every caller relies on")
    class Contract {

        @Test
        void apply_whenTwoLimbsTie_theFirstDeclaredWins() {
            // The statute lists limbs in order; an arbitrary winner makes traces non-deterministic
            LeastOf cap = new LeastOf(List.of(limb("first", "50000"), limb("second", "50000")));

            CapResult result = cap.apply(new CapContext(java.util.Map.of()));

            assertThat(result.value()).isEqualTo(Money.of("50000"));
            assertThat(result.limitedBy()).isEqualTo("first");
        }

        @Test
        void apply_whenALimbIsNegative_returnsItWithoutFlooring() {
            // G1.3 floors at the caller. A node given −40,000 knows renting claims nothing here;
            // a node given 0 cannot tell that from a taxpayer who pays no rent at all.
            LeastOf cap = new LeastOf(List.of(
                    limb("received", "600000"), limb("rent less 10% of salary", "-40000")));

            CapResult result = cap.apply(new CapContext(java.util.Map.of()));

            assertThat(result.value()).isEqualTo(Money.of("-40000"));
            assertThat(result.limitedBy()).isEqualTo("rent less 10% of salary");
        }

        @Test
        void apply_withASingleLimb_returnsIt() {
            LeastOf cap = new LeastOf(List.of(limb("only", "1000")));

            assertThat(cap.apply(new CapContext(java.util.Map.of())).limitedBy()).isEqualTo("only");
        }

        @Test
        void construction_whenTwoLimbsShareAName_isRejected() {
            // Then limitedBy could not say which one bound
            assertThatThrownBy(() -> new LeastOf(List.of(limb("rent", "1"), limb("rent", "2"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("both named");
        }

        @Test
        void construction_whenALimbIsUnnamed_isRejected() {
            assertThatThrownBy(() -> new LeastOf.Limb("  ", new Fixed(Money.ZERO)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be named");
        }

        @Test
        void construction_whenThereAreNoLimbs_isRejected() {
            assertThatThrownBy(() -> new LeastOf(List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one limb");
        }
    }

    @Test
    void shapeName_whenNamed_countsItsLimbs() {
        // When / Then — two limbs (W-144)
        assertThat(new LeastOf(List.of(limb("received", "600000"), limb("ceiling", "300000"))).shapeName())
                .isEqualTo("least of 2 limbs");
    }

    @Nested
    @DisplayName("Headroom — what would change it (W-326a, D-696); every figure worked by hand")
    class WhatWouldChangeIt {

        private static final BaseId RENT_PAID = new BaseId("RENT_PAID");

        private static final BaseId HRA_RECEIVED = new BaseId("HRA_RECEIVED");

        private static final BaseId SALARY = new BaseId("SALARY");

        private static final BaseId CLAIMED = new BaseId("AMOUNT_CLAIMED");

        private static final BaseId INTEREST = new BaseId("INTEREST");

        /** HRA's three limbs as the rule writes them: received, rent less 10 % of salary, 50 % of salary. */
        private static LeastOf hra() {
            return new LeastOf(List.of(
                    new LeastOf.Limb("received", new PercentOfBase(Rate.percent("100"), HRA_RECEIVED)),
                    new LeastOf.Limb("rent less 10% of salary", new AmountInExcess(RENT_PAID,
                            new PercentOfBase(Rate.percent("10"), SALARY))),
                    new LeastOf.Limb("50% of salary", new PercentOfBase(Rate.percent("50"), SALARY))));
        }

        private static CapContext hraFacts(String rent) {
            return new CapContext(Map.of(HRA_RECEIVED, Money.of("600000"), RENT_PAID, Money.of(rent),
                    SALARY, Money.of("1320000")), Map.of(), Map.of());
        }

        @Test
        void apply_whenRentBinds_saysHowMuchMoreRentReachesTheNextLimit() {
            // When — rent 3,60,000 less 10 % of 13,20,000 = 2,28,000; received 6,00,000; 50 % of salary 6,60,000
            CapResult result = hra().apply(hraFacts("360000"));

            // Then — the next limit is the 6,00,000 received; rent rises one for one, so 3,72,000 more
            assertThat(result.headroom()).hasValueSatisfying(headroom -> {
                assertThat(headroom.input()).isEqualTo(RENT_PAID);
                assertThat(headroom.more()).isEqualByComparingTo(Money.of("372000"));
                assertThat(headroom.next()).isEqualByComparingTo(Money.of("600000"));
            });
        }

        @Test
        void apply_whenRentIsStillBelowTheThreshold_addsTheShortfallFirst() {
            // When — rent 1,00,000 is 32,000 short of 10 % of 13,20,000, so that limb is nil; the other limit is 60,000
            CapResult result = new LeastOf(List.of(
                    new LeastOf.Limb("rent less 10% of salary", new AmountInExcess(RENT_PAID,
                            new PercentOfBase(Rate.percent("10"), SALARY))),
                    new LeastOf.Limb("ceiling", new Fixed(Money.of("60000"))))).apply(hraFacts("100000"));

            // Then — 32,000 to reach the threshold, then 60,000 more: 92,000
            assertThat(result.headroom()).hasValueSatisfying(headroom -> {
                assertThat(headroom.more()).isEqualByComparingTo(Money.of("92000"));
                assertThat(headroom.next()).isEqualByComparingTo(Money.of("60000"));
            });
        }

        @Test
        void apply_whenAPercentageBinds_dividesTheGapByItsRate() {
            // When — 50 % of 40,000 claimed = 20,000 against a limit of 50,000
            CapResult result = new LeastOf(List.of(
                    new LeastOf.Limb("half the claim", new PercentOfBase(Rate.percent("50"), CLAIMED)),
                    new LeastOf.Limb("limit", new Fixed(Money.of("50000")))))
                    .apply(CapContext.of(CLAIMED, Money.of("40000")));

            // Then — a gap of 30,000 at 50 % needs 60,000 more claimed
            assertThat(result.headroom()).hasValueSatisfying(headroom -> {
                assertThat(headroom.input()).isEqualTo(CLAIMED);
                assertThat(headroom.more()).isEqualByComparingTo(Money.of("60000"));
            });
        }

        @Test
        void apply_whenAFixedLimitBinds_saysNothing() {
            // When — interest 25,000 against a fixed 10,000: no amount raises a fixed limit
            CapResult result = new LeastOf(List.of(
                    new LeastOf.Limb("the interest", new PercentOfBase(Rate.percent("100"), INTEREST)),
                    new LeastOf.Limb("the limit", new Fixed(Money.of("10000")))))
                    .apply(CapContext.of(INTEREST, Money.of("25000")));

            // Then
            assertThat(result.limitedBy()).isEqualTo("the limit");
            assertThat(result.headroom()).isEmpty();
        }

        @Test
        void apply_whenAnotherLimbReadsTheSameInput_saysNothing() {
            // When — 10 % and 50 % of the same salary: raising salary moves both limbs (C3a)
            CapResult result = new LeastOf(List.of(
                    new LeastOf.Limb("tenth", new PercentOfBase(Rate.percent("10"), SALARY)),
                    new LeastOf.Limb("half", new PercentOfBase(Rate.percent("50"), SALARY))))
                    .apply(CapContext.of(SALARY, Money.of("1320000")));

            // Then
            assertThat(result.limitedBy()).isEqualTo("tenth");
            assertThat(result.headroom()).isEmpty();
        }

        @Test
        void apply_whenTwoLimitsTie_saysNothing() {
            // When — the interest 10,000 equals the fixed 10,000: raising it meets the other at once
            CapResult result = new LeastOf(List.of(
                    new LeastOf.Limb("the interest", new PercentOfBase(Rate.percent("100"), INTEREST)),
                    new LeastOf.Limb("the limit", new Fixed(Money.of("10000")))))
                    .apply(CapContext.of(INTEREST, Money.of("10000")));

            // Then
            assertThat(result.headroom()).isEmpty();
        }
    }
}
