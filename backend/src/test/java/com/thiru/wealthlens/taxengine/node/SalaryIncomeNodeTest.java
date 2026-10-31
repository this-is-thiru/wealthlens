package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Income under "Salaries": the income before medical travel abroad plus the part of it that is salary (D-112),
 * untraced because both already carry their lines.
 */
class SalaryIncomeNodeTest {

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");

    private static NodeResult run(String beforeTravel, String medicalTravel) {
        TaxYearId taxYear = TaxYearId.of(2026);
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, NEW_TAX_REGIME,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(2026, 9, 21)), plain.entered(), plain.assessee(),
                plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());
        return NodeHarness.with(pack)
                .regime(NEW_TAX_REGIME)
                .inputs(inputs)
                .given(NodeIds.SALARY_INCOMEBEFORETRAVEL, MoneyValue.of(Money.of(beforeTravel)))
                .given(NodeIds.SALARY_MEDICALTRAVEL, MoneyValue.of(Money.of(medicalTravel)))
                .run(new SalaryIncomeNode());
    }

    @Test
    void evaluate_whenMedicalTravelIsSalary_addsItToTheIncomeBeforeIt() {
        // When
        NodeResult result = run("825000", "250000");

        // Then — 8,25,000 + 2,50,000 = 10,75,000, with no line of its own
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("1075000")));
        assertThat(result.trace()).isEmpty();
    }

    @Test
    void evaluate_whenThereIsNoMedicalTravel_isTheIncomeBeforeIt() {
        // When
        NodeResult result = run("825000", "0");

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("825000")));
    }
}
