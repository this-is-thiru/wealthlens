package com.thiru.wealthlens.taxengine.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.test.Fixtures;
import org.junit.jupiter.api.Test;

/**
 * The persona fixtures (W-145) run through the shipped pack and pipeline. Every expected figure is worked by hand in
 * {@code sources/2026-11-07-persona-figures.md} from the Gazette rates, never taken from a run; Priya's is
 * {@code DR 08} T8-1's.
 */
class PersonaFixturesTest {

    private static final GoldenCaseRunner RUNNER = new GoldenCaseRunner();

    private static final RegimeCode OLD_REGIME = new RegimeCode("FIRST_SCHEDULE_RATES");

    @Test
    void ravi_whenNewRegime_paysSlabTaxOnSalaryAndSpecialRatesOnGains() {
        // Given — salary 26,65,600 less 75,000; gains 1,50,000 long and 40,000 short
        EngineInputs inputs = Fixtures.ravi();

        // When
        GoldenCaseRunner.ShippedPipelineRun run = RUNNER.run(inputs);

        // Then — 25,90,600 + 1,90,000; 3,57,180 + 8,000 + 3,125 = 3,68,305, with cess 3,83,037.20 → 3,83,040
        assertThat(run.result().valueOf(NodeIds.ROUND_TOTALINCOME)).isEqualTo(money("2780600"));
        assertThat(run.result().valueOf(NodeIds.ROUND_NETPAYABLE)).isEqualTo(money("383040"));
    }

    @Test
    void ravi_whenOldRegime_takesHisRentAndProvidentFundOffSalary() {
        // Given — HRA exempt 3,92,000 (rent less a tenth of basic); standard deduction 50,000; employee PF 1,34,400
        EngineInputs inputs = inRegime(Fixtures.ravi(), OLD_REGIME);

        // When
        GoldenCaseRunner.ShippedPipelineRun run = RUNNER.run(inputs);

        // Then — 20,89,200 + 1,90,000; 4,39,260 + 11,125 = 4,50,385, with cess 4,68,400.40 → 4,68,400
        assertThat(run.result().valueOf(NodeIds.ROUND_TOTALINCOME)).isEqualTo(money("2279200"));
        assertThat(run.result().valueOf(NodeIds.ROUND_NETPAYABLE)).isEqualTo(money("468400"));
    }

    @Test
    void priya_whenTwoEmployersEachGaveTheStandardDeduction_isAllowedItOnce() {
        // Given — DR 08 T8-1: 9,00,000 and 12,00,000 from two employers
        EngineInputs inputs = Fixtures.priya();

        // When
        GoldenCaseRunner.ShippedPipelineRun run = RUNNER.run(inputs);

        // Then — 21,00,000 less one 75,000; 2,06,250 with cess 8,250
        assertThat(run.result().valueOf(NodeIds.ROUND_TOTALINCOME)).isEqualTo(money("2025000"));
        assertThat(run.result().valueOf(NodeIds.ROUND_NETPAYABLE)).isEqualTo(money("214500"));
    }

    @Test
    void anand_whenOldRegime_takesTheSeniorDeductionsAndSlabs() {
        // Given — pension 6,00,000 less 50,000; interest 1,04,000; 80TTB 50,000 and 80D 40,000
        EngineInputs inputs = inRegime(Fixtures.anand(), OLD_REGIME);

        // When
        GoldenCaseRunner.ShippedPipelineRun run = RUNNER.run(inputs);

        // Then — 5,64,000: 10,000 + 12,800 = 22,800, with cess 23,712 → 23,710
        assertThat(run.result().valueOf(NodeIds.ROUND_TOTALINCOME)).isEqualTo(money("564000"));
        assertThat(run.result().valueOf(NodeIds.ROUND_NETPAYABLE)).isEqualTo(money("23710"));
    }

    @Test
    void anand_whenNewRegime_owesNothingAfterTheRebate() {
        // Given — 6,00,000 less 75,000, plus 1,04,000; neither senior deduction is allowed
        EngineInputs inputs = Fixtures.anand();

        // When
        GoldenCaseRunner.ShippedPipelineRun run = RUNNER.run(inputs);

        // Then — 6,29,000: slab tax 11,450, all of it rebated
        assertThat(run.result().valueOf(NodeIds.ROUND_TOTALINCOME)).isEqualTo(money("629000"));
        assertThat(run.result().valueOf(NodeIds.ROUND_NETPAYABLE)).isEqualTo(money("0"));
    }

    @Test
    void meera_whenNewRegime_hasHerSurchargeHeldByMarginalRelief() {
        // Given — 45,00,000 at slab rates and 5,50,000 of long-term gains
        EngineInputs inputs = Fixtures.meera();

        // When
        GoldenCaseRunner.ShippedPipelineRun run = RUNNER.run(inputs);

        // Then — 9,83,125 + 98,312.50 held to 9,68,125 + 50,000 = 10,18,125, with cess 40,725
        assertThat(run.result().valueOf(NodeIds.ROUND_TOTALINCOME)).isEqualTo(money("5050000"));
        assertThat(run.result().valueOf(NodeIds.ROUND_NETPAYABLE)).isEqualTo(money("1058850"));
    }

    @Test
    void meera_whenOldRegime_hasHerSurchargeHeldByMarginalRelief() {
        // Given — 45,25,000 at slab rates and 5,50,000 of long-term gains
        EngineInputs inputs = inRegime(Fixtures.meera(), OLD_REGIME);

        // When
        GoldenCaseRunner.ShippedPipelineRun run = RUNNER.run(inputs);

        // Then — 12,23,125 × 1.10 held to 12,00,625 + 75,000 = 12,75,625, with cess 51,025
        assertThat(run.result().valueOf(NodeIds.ROUND_TOTALINCOME)).isEqualTo(money("5075000"));
        assertThat(run.result().valueOf(NodeIds.ROUND_NETPAYABLE)).isEqualTo(money("1326650"));
    }

    private static MoneyValue money(String rupees) {
        return MoneyValue.of(Money.of(rupees));
    }

    private static EngineInputs inRegime(EngineInputs persona, RegimeCode regime) {
        return new EngineInputs(persona.taxYear(), regime, persona.temporal(), persona.entered(), persona.assessee(),
                persona.family(), persona.ledgers(), persona.elections(), persona.capitalGains(), persona.filing());
    }
}
