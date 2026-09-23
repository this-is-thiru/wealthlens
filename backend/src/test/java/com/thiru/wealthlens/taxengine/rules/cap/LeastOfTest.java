package com.thiru.wealthlens.taxengine.rules.cap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.util.List;
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
}
