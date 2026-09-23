package com.thiru.wealthlens.taxengine.rules.cap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.util.Map;
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
}
