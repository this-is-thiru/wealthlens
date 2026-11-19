package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.thiru.wealthlens.taxengine.input.Declaration;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.FilingFacts;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.pipeline.HeadsAfterSetOff;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SpecialRateId;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.rules.condition.ResidentialStatus;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.Severity;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceAnnotation;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Net agricultural income in the rates — partial integration, FA 2026 s.2(2) (TY 2025-26) and s.3(2) (TY 2026-27).
 * Every figure is worked by hand in {@code plans/W-258.md} from the shipped ladders: Z = X − Y, X the tax on the
 * income plus the net agricultural income, Y the tax on the net agricultural income plus column C.
 */
class AgriculturalIntegrationTest {

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");
    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final DeclarationCode NET_AGRICULTURAL_INCOME = new DeclarationCode("NET_AGRICULTURAL_INCOME");
    private static final CapitalGainsPart SHORT_TERM_LISTED_EQUITY = new CapitalGainsPart("SHORT_TERM_LISTED_EQUITY");

    private static RulePack pack(int year) {
        TaxYearId taxYear = TaxYearId.of(year);
        return new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
    }

    /** A resident aged forty, the net agricultural income answered or not, the gains at special rates as given. */
    private static NodeResult run(int year, RegimeCode regime, String totalIncome, Optional<String> netAgricultural,
            Map<CapitalGainsPart, Money> gains) {
        RulePack pack = pack(year);
        TaxYearId taxYear = pack.taxYear();
        EngineInputs plain = NodeHarness.plainTaxpayer();
        Individual person = new Individual(taxYear.endsOn().minusYears(40).minusMonths(6), ResidentialStatus.RESIDENT,
                Optional.empty(), Optional.empty());
        FilingFacts filing = new FilingFacts(plain.filing().dueDate(), Optional.empty(), java.util.List.of(), Money.ZERO,
                java.util.List.of(), netAgricultural.<Map<DeclarationCode, Declaration>>map(amount -> Map.of(
                        NET_AGRICULTURAL_INCOME, new Declaration.Amount(Money.of(amount)))).orElse(Map.of()),
                Optional.empty());
        EngineInputs inputs = new EngineInputs(taxYear, regime,
                TemporalContext.forTaxYear(taxYear, taxYear.startsOn().plusMonths(5)), plain.entered(), person,
                plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), filing);
        return NodeHarness.with(pack).regime(regime).inputs(inputs)
                .given(NodeIds.ROUND_TOTALINCOME, Money.of(totalIncome))
                .given(NodeIds.SETOFF, new HeadsAfterSetOff(Money.of(totalIncome), gains))
                .run(new TaxByBasisNode());
    }

    private static NodeResult run(int year, RegimeCode regime, String totalIncome, String netAgricultural) {
        return run(year, regime, totalIncome, Optional.of(netAgricultural), Map.of());
    }

    @Test
    void evaluate_whenNewRegimeTwelveLakhWithTenLakhAgricultural_chargesXLessY() {
        // Given — case A: X = tax(22L) = 20,000 + 40,000 + 60,000 + 80,000 + 50,000 = 2,50,000;
        // Y = tax(10L + 4L) = 20,000 + 40,000 + 30,000 = 90,000; Z = 1,60,000 (was 60,000)
        // When
        NodeResult result = run(2026, NEW_TAX_REGIME, "1200000", "1000000");

        // Then
        assertThat(result.value()).isEqualTo(TaxByBasis.atSlabRatesOnly(Money.of("160000")));
        assertThat(result.trace()).extracting(TraceLine::rule, TraceLine::kind, TraceLine::in, TraceLine::out)
                .containsExactly(
                        tuple(RuleIds.RATE_SLAB_NEW_TAX_REGIME, StepKind.TAX, MoneyValue.of(Money.of("2200000")),
                                MoneyValue.of(Money.of("250000"))),
                        tuple(RuleIds.RATE_AGRICULTURAL_NEW_TAX_REGIME, StepKind.RELIEF,
                                MoneyValue.of(Money.of("1400000")), MoneyValue.of(Money.of("90000"))));
        assertThat(result.annotations()).isEmpty();
    }

    @Test
    void evaluate_whenNewRegimeFifteenLakhWithThreeLakhAgricultural_chargesXLessY() {
        // Given — case B: X = tax(18L) = 20,000 + 40,000 + 60,000 + 40,000 = 1,60,000; Y = tax(7L) = 15,000
        // When
        NodeResult result = run(2026, NEW_TAX_REGIME, "1500000", "300000");

        // Then
        assertThat(result.value()).isEqualTo(TaxByBasis.atSlabRatesOnly(Money.of("145000")));
    }

    @Test
    void evaluate_whenOldRegimeEightLakhWithTwoLakhAgricultural_usesTwoLakhFiftyThousandAsTheMaximum() {
        // Given — case C, TY 2025-26: X = tax(10L) = 12,500 + 1,00,000 = 1,12,500; Y = tax(2L + 2.5L) = 10,000
        // When
        NodeResult result = run(2025, FIRST_SCHEDULE_RATES, "800000", "200000");

        // Then
        assertThat(result.value()).isEqualTo(TaxByBasis.atSlabRatesOnly(Money.of("102500")));
        assertThat(result.trace()).extracting(TraceLine::rule)
                .containsExactly(RuleIds.RATE_SLAB_FIRST_SCHEDULE, RuleIds.RATE_AGRICULTURAL_FIRST_SCHEDULE);
    }

    @Test
    void evaluate_whenIncomeIsExactlyTheMaximumNotChargeable_doesNotIntegrate() {
        // Given — case D: "the total income exceeds" ₹4,00,000, and ₹4,00,000 does not
        // When
        NodeResult result = run(2026, NEW_TAX_REGIME, "400000", "500000");

        // Then
        assertThat(result.value()).isEqualTo(TaxByBasis.atSlabRatesOnly(Money.ZERO));
        assertThat(result.trace()).hasSize(1);
    }

    @Test
    void evaluate_whenNetAgriculturalIncomeIsExactlyFiveThousand_doesNotIntegrate() {
        // Given — case E: "exceeding ₹ 5000"; tax(15L) = 20,000 + 40,000 + 45,000 = 1,05,000
        // When
        NodeResult result = run(2026, NEW_TAX_REGIME, "1500000", "5000");

        // Then
        assertThat(result.value()).isEqualTo(TaxByBasis.atSlabRatesOnly(Money.of("105000")));
    }

    @Test
    void evaluate_whenNetAgriculturalIncomeIsOneRupeeOverFiveThousand_integrates() {
        // Given — case F: X = tax(15,05,001) = 20,000 + 40,000 + 15% of 3,05,001 = 1,05,750.15;
        // Y = tax(4,05,001) = 5% of 5,001 = 250.05; Z = 1,05,500.10
        // When
        NodeResult result = run(2026, NEW_TAX_REGIME, "1500000", "5001");

        // Then
        assertThat(result.value()).isEqualTo(TaxByBasis.atSlabRatesOnly(Money.of("105500.10")));
    }

    @Test
    void evaluate_whenSlabIncomeIsUnderTheMaximumButTotalIncomeIsOver_doesNotIntegrate() {
        // Given — case G (C4a): total income 5,00,000 = slab income 3,00,000 + s.196 gains 2,00,000; net agricultural
        // 3,00,000. Slab income does not exceed ₹4,00,000, so no integration; the unused 1,00,000 of the nil band comes
        // off the gains (s.196(2)) and 1,00,000 is charged at 20% = 20,000
        // When
        NodeResult result = run(2026, NEW_TAX_REGIME, "500000", Optional.of("300000"),
                Map.of(SHORT_TERM_LISTED_EQUITY, Money.of("200000")));

        // Then
        assertThat(result.value()).isEqualTo(new TaxByBasis(Money.ZERO,
                Map.of(new SpecialRateId("RATE_CG_SHORT_LISTED_EQUITY"), Money.of("20000"))));
    }

    @Test
    void evaluate_whenTheQuestionIsUnanswered_chargesTheIncomeAloneAndSaysSo() {
        // Given — tax(15L) = 1,05,000; no answer, so no integration (C3a)
        // When
        NodeResult result = run(2026, NEW_TAX_REGIME, "1500000", Optional.empty(), Map.of());

        // Then
        assertThat(result.value()).isEqualTo(TaxByBasis.atSlabRatesOnly(Money.of("105000")));
        assertThat(result.annotations()).singleElement().satisfies(annotation -> {
            assertThat(annotation.sequence()).isEqualTo(1);
            assertThat(annotation.notices()).singleElement().satisfies(notice -> {
                assertThat(notice.severity()).isEqualTo(Severity.ACTION_REQUIRED);
                assertThat(notice.message()).contains("NET_AGRICULTURAL_INCOME");
            });
        });
    }

    @Test
    void evaluate_whenTheQuestionIsAnswered_hasNoNotice() {
        // When
        NodeResult result = run(2026, NEW_TAX_REGIME, "1500000", "0");

        // Then
        assertThat(result.annotations()).extracting(TraceAnnotation::notices).isEmpty();
    }
}
