package com.thiru.wealthlens.brokercharges.engine;

import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.rule;
import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.trade;
import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeCategory;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeRuleSource;
import com.thiru.wealthlens.brokercharges.entity.ChargeLine;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A percentage of other charges, named explicitly.
 *
 * <p>This class is the D1 regression suite. The superseded implementation applied GST to a merged
 * government-charges bucket that included securities transaction tax and stamp duty, neither of
 * which is taxable — roughly ₹17 of overcharge on a ₹1,00,000 sell. Naming the base codes is what
 * makes that impossible to express by accident.
 */
class DerivedChargeCalculatorTest {

    private final DerivedChargeCalculator calculator = new DerivedChargeCalculator();

    @Test
    void basis_isDerived() {
        assertThat(calculator.basis()).isEqualTo(ChargeBasis.DERIVED);
    }

    @Test
    void compute_isThePercentageOfTheNamedLine() {
        // Given
        ChargeAccumulator accumulator = accumulatorWith(line("BROKERAGE", 20.00));

        // When / Then — 18% of 20
        assertThat(calculator.compute(gst("BROKERAGE"), trade(), accumulator))
                .isEqualByComparingTo("3.6000");
    }

    @Test
    void compute_sumsEveryNamedLineBeforeApplyingTheRate() {
        // Given
        ChargeAccumulator accumulator = accumulatorWith(
                line("BROKERAGE", 20.00), line("EXCHANGE_TXN", 2.97), line("SEBI_FEE", 0.10));

        // When / Then — 18% of 23.07, rounded once by the engine afterwards rather than per base
        assertThat(calculator.compute(gst("BROKERAGE", "EXCHANGE_TXN", "SEBI_FEE"), trade(), accumulator))
                .isEqualByComparingTo("4.1526");
    }

    @Test
    void compute_excludesChargesTheRuleDoesNotName() {
        // Given — securities transaction tax and stamp duty are present and are not taxable
        ChargeAccumulator accumulator = accumulatorWith(
                line("BROKERAGE", 20.00), line("STT", 100.00), line("STAMP_DUTY", 15.00));

        // When — the rule names brokerage alone
        // Then — 18% of 20, never of 135. This is D1.
        assertThat(calculator.compute(gst("BROKERAGE"), trade(), accumulator))
                .isEqualByComparingTo("3.6000");
    }

    @Test
    void compute_whenANamedCodeWasNeverCharged_contributesNothing() {
        // Given — a rule may legitimately name a base that this trade's side filtered out. Depository
        // charges are on the card and on a sell only, so a buy names a code that is not there.
        ChargeAccumulator accumulator = accumulatorWith(line("BROKERAGE", 20.00));

        // When / Then — 18% of 20, and no failure over the missing DP line
        assertThat(calculator.compute(gst("BROKERAGE", "DP"), trade(), accumulator))
                .isEqualByComparingTo("3.6000");
    }

    @Test
    void compute_whenANamedCodeWasChargedZero_contributesNothing() {
        ChargeAccumulator accumulator = accumulatorWith(line("BROKERAGE", 0.0));

        assertThat(calculator.compute(gst("BROKERAGE"), trade(), accumulator))
                .isEqualByComparingTo("0");
    }

    @Test
    void compute_whenNoBaseIsNamed_isZero() {
        // Given — a derived rule naming nothing has no base to apply its rate to. Taxing everything
        // charged so far would be a guess, and would silently reintroduce D1.
        ChargeRule rule = rule("GST", ChargeBasis.DERIVED);
        rule.setRate(18.0);
        rule.setBaseCodes(List.of());

        assertThat(calculator.compute(rule, trade(), accumulatorWith(line("BROKERAGE", 20.00))))
                .isEqualByComparingTo("0");
    }

    @Test
    void compute_whenTheRateIsAbsent_isZero() {
        ChargeRule rule = rule("GST", ChargeBasis.DERIVED);
        rule.setRate(null);
        rule.setBaseCodes(List.of("BROKERAGE"));

        assertThat(calculator.compute(rule, trade(), accumulatorWith(line("BROKERAGE", 20.00))))
                .isEqualByComparingTo("0");
    }

    @Test
    void compute_readsALineEmittedByAnEarlierDerivedRule() {
        // Given — a cess on a tax is a percentage of a percentage, and the accumulator does not
        // care which basis produced a line
        ChargeAccumulator accumulator = accumulatorWith(line("GST", 3.60));

        ChargeRule cess = rule("CESS", ChargeBasis.DERIVED);
        cess.setRate(10.0);
        cess.setBaseCodes(List.of("GST"));

        // When / Then
        assertThat(calculator.compute(cess, trade(), accumulator)).isEqualByComparingTo("0.360");
    }

    private static ChargeRule gst(String... baseCodes) {
        ChargeRule rule = rule("GST", ChargeBasis.DERIVED);
        rule.setRate(18.0);
        rule.setBaseCodes(List.of(baseCodes));
        return rule;
    }

    private static ChargeAccumulator accumulatorWith(ChargeLine... lines) {
        ChargeAccumulator accumulator = new ChargeAccumulator();
        for (ChargeLine line : lines) {
            accumulator.add(line);
        }
        return accumulator;
    }

    private static ChargeLine line(String code, double amount) {
        ChargeLine line = new ChargeLine();
        line.setCode(code);
        line.setCategory(ChargeCategory.BROKERAGE);
        line.setBasis(ChargeBasis.FLAT);
        line.setSource(ChargeRuleSource.SCHEDULE);
        line.setAmount(amount);
        return line;
    }
}
