package com.thiru.wealthlens.taxengine.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxrules.money.Money;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The partition that stops the rebate being computed on the wrong base.
 */
class NodeValueTest {

    private static final SpecialRateId LTCG = new SpecialRateId("LTCG_198");
    private static final SpecialRateId STCG = new SpecialRateId("STCG_197");

    @Nested
    @DisplayName("TaxByBasis — R-5, the most dangerous flattening in the engine")
    class Partition {

        /**
         * T13-1's shape: ₹12,00,000 of income, mostly capital gains. Slab tax is small; the total
         * is not. A rebate computed on the total would wipe out a bill the taxpayer owes.
         */
        private static TaxByBasis mostlyCapitalGains() {
            return new TaxByBasis(Money.of("20000"), Map.of(LTCG, Money.of("100000")));
        }

        @Test
        void slabAndSpecialRatesAreReadableApart() {
            TaxByBasis tax = mostlyCapitalGains();

            assertThat(tax.atSlabRates()).isEqualTo(Money.of("20000"));
            assertThat(tax.at(LTCG)).isEqualTo(Money.of("100000"));
            assertThat(tax.totalAtSpecialRates()).isEqualTo(Money.of("100000"));
        }

        @Test
        void theRebateBaseAndTheTotalAreDifferentNumbers() {
            // The whole reason this type exists. Anything reading asMoney() where it means
            // atSlabRates() is off by the special-rate tax, in the taxpayer's favour.
            TaxByBasis tax = mostlyCapitalGains();

            assertThat(tax.asMoney()).isEqualTo(Money.of("120000"));
            assertThat(tax.atSlabRates()).isNotEqualTo(tax.asMoney());
        }

        @Test
        void asMoneySumsEverySpecialRateOntoTheSlabTax() {
            TaxByBasis tax = new TaxByBasis(
                    Money.of("20000"), Map.of(LTCG, Money.of("100000"), STCG, Money.of("5000")));

            assertThat(tax.asMoney()).isEqualTo(Money.of("125000"));
        }

        @Test
        void anUnchargedSpecialRateIsZeroRatherThanAbsent() {
            assertThat(mostlyCapitalGains().at(STCG)).isEqualTo(Money.ZERO);
        }

        @Test
        void slabOnlyIsTheOrdinaryCase() {
            TaxByBasis tax = TaxByBasis.atSlabRatesOnly(Money.of("60000"));

            assertThat(tax.asMoney()).isEqualTo(tax.atSlabRates());
            assertThat(tax.totalAtSpecialRates()).isEqualTo(Money.ZERO);
        }

        @Test
        void addingASpecialRateAccumulatesRatherThanReplaces() {
            TaxByBasis tax = TaxByBasis.atSlabRatesOnly(Money.of("60000"))
                    .plusSpecialRate(LTCG, Money.of("10000"))
                    .plusSpecialRate(LTCG, Money.of("2500"));

            assertThat(tax.at(LTCG)).isEqualTo(Money.of("12500"));
            assertThat(tax.atSlabRates()).isEqualTo(Money.of("60000"));
        }

        @Test
        void theMapIsDefensivelyCopied() {
            Map<SpecialRateId, Money> mutable = new LinkedHashMap<>();
            mutable.put(LTCG, Money.of("100000"));
            TaxByBasis tax = new TaxByBasis(Money.of("20000"), mutable);

            mutable.put(LTCG, Money.of("999999"));

            assertThat(tax.at(LTCG)).isEqualTo(Money.of("100000"));
        }

        @Test
        void nullSpecialRatesAreRejectedRatherThanTreatedAsEmpty() {
            // "No special-rate income" and "nobody computed it" are different, and only one of
            // them is safe to treat as zero
            assertThatThrownBy(() -> new TaxByBasis(Money.ZERO, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("pass an empty map");
        }
    }

    @Nested
    @DisplayName("MoneyValue")
    class SingleAmount {

        @Test
        void isItsOwnSummary() {
            assertThat(MoneyValue.of(Money.of("1275000")).asMoney()).isEqualTo(Money.of("1275000"));
        }

        @Test
        void aNullAmountIsRejected() {
            assertThatThrownBy(() -> new MoneyValue(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("the family is closed over what exists")
    class SealedFamily {

        /**
         * An exhaustive switch with no {@code default}. It compiles, which is the assertion.
         *
         * <p>{@code 15-review-findings.md} B4 also names {@code HeadResult}, {@code ComponentSet},
         * {@code ElectionOutcome} and {@code InterestBreakdown} in its {@code permits} clause — and
         * declares none of them anywhere. Each joins here with the node that produces it; adding one
         * without extending this switch does not compile.
         */
        @Test
        void everyNodeValueIsAccountedFor() {
            java.util.List<NodeValue> all = java.util.List.of(
                    MoneyValue.of(Money.of("1")), TaxByBasis.atSlabRatesOnly(Money.of("1")));

            for (NodeValue value : all) {
                String kind = switch (value) {
                    case MoneyValue ignored -> "amount";
                    case TaxByBasis ignored -> "tax by basis";
                };
                assertThat(kind).isNotBlank();
            }
            assertThat(all).hasSize(2);
        }
    }

    @Nested
    @DisplayName("SpecialRateId")
    class Identifier {

        @Test
        void acceptsAValidCodeAndRejectsAnInvalidOne() {
            assertThat(new SpecialRateId("LTCG_198").value()).isEqualTo("LTCG_198");
            assertThatThrownBy(() -> new SpecialRateId("ltcg 198"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("upper-case with underscores");
        }
    }
}
