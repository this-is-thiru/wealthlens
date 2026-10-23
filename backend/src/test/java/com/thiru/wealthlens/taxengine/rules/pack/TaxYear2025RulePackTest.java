package com.thiru.wealthlens.taxengine.rules.pack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.node.CessNode;
import com.thiru.wealthlens.taxengine.node.RebateNode;
import com.thiru.wealthlens.taxengine.node.RoundNetPayableNode;
import com.thiru.wealthlens.taxengine.node.TaxByBasisNode;
import com.thiru.wealthlens.taxengine.pipeline.HeadsAfterSetOff;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.rules.condition.AgeSubject;
import com.thiru.wealthlens.taxengine.rules.condition.ConditionContext;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.regime.DeadlineSpec;
import com.thiru.wealthlens.taxengine.rules.regime.ElectionProtocol;
import com.thiru.wealthlens.taxengine.rules.regime.ElectionVehicle;
import com.thiru.wealthlens.taxengine.rules.regime.FormRef;
import com.thiru.wealthlens.taxengine.rules.regime.VehicleRoute;
import com.thiru.wealthlens.taxengine.rules.registry.GoverningInstruments;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.temporal.Presence;
import com.thiru.wealthlens.taxengine.rules.temporal.Provision;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidator;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * TY 2025-26 — the 1961 Act, AY 2026-27 — on the timeline for the Phase 5 rules (D-041, D-082). The
 * instruments are read from the 2025-26 lock, which is this year's registry entry; the expected
 * provisions are the ones read in {@code plans/W-052c.md}, and the figures are {@code DR 08}'s T1 cases,
 * which it runs in both years.
 */
class TaxYear2025RulePackTest {

    private static final TaxYearId TY_2025 = TaxYearId.of(2025);
    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");
    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final String ACT_2025_URL = "https://egazette.gov.in/WriteReadData/2025/265620.pdf";

    private static GoverningInstruments instruments() {
        return LockedInstruments.of(TY_2025);
    }

    private static RulePack pack() {
        return new ClasspathRulePackProvider("rulepacks", Set.of(TY_2025), new JsonPackAssembler(instruments()))
                .load(TY_2025);
    }

    private static EngineInputs inputs2025(RegimeCode regime) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        return new EngineInputs(TY_2025, regime, TemporalContext.forTaxYear(TY_2025, LocalDate.of(2025, 9, 21)),
                plain.entered(), plain.assessee(), plain.family(), plain.ledgers(), plain.elections(),
                plain.capitalGains(), plain.filing());
    }

    private static RuleVersion inForce(RulePack pack, String rule) {
        return pack.rule(new RuleId(rule)).versionAt(TemporalContext.forTaxYear(TY_2025, LocalDate.of(2025, 9, 21)));
    }

    // -- the pack ----------------------------------------------------------------------------------

    @Test
    void regimes_whenThe2025PackIsCompiled_electUnderThe1961Act() {
        // When — D-133: TY 2025-26 is chosen under 1961 s.115BAC, not 2025 s.202
        RulePack pack = pack();
        ElectionProtocol optOut = pack.regimes().get(FIRST_SCHEDULE_RATES).election();
        ElectionProtocol byDefault = pack.regimes().get(NEW_TAX_REGIME).election();

        // Then
        assertThat(optOut.deadline()).isEqualTo(new DeadlineSpec.DueDateUnder("139(1)"));
        assertThat(optOut.vehicle()).isEqualTo(ElectionVehicle.IN_THE_RETURN);
        assertThat(optOut.provenance().sourceDescription()).contains("s.115BAC(6)");
        assertThat(optOut.provenance().verificationLevel()).isEqualTo(VerificationLevel.SOURCE_CHECKED);
        assertThat(byDefault.deadline()).isEqualTo(new DeadlineSpec.NoDeadline());
        assertThat(byDefault.provenance().sourceDescription()).contains("s.115BAC(1A)");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "BUSINESS_OR_PROFESSION, PRESCRIBED_FORM, 10-IEA",
        "SALARIES, IN_THE_RETURN, ''"
    })
    void vehicleFor_whenOptingOutOfTheNewRegimeIn2025_formOnlyWithBusinessIncome(
            HeadCode head, ElectionVehicle expectedVehicle, String expectedForm) {
        // Given — r.21AGA(1) (G.S.R. 452(E), Gazette 2023/246719 p.6): (a) Form No. 10-IEA by the s.139(1) due date
        // for a person having income from business or profession; (b) in the return for one not having it
        ElectionProtocol optOut = pack().regimes().get(FIRST_SCHEDULE_RATES).election();
        ConditionContext taxpayer = new ConditionContext(true, PersonType.INDIVIDUAL, Map.of(AgeSubject.SELF, 40),
                Map.of(), Map.of(), Set.of(head), Set.of(), Set.of(), Optional.empty(), ItemFacts.NONE);

        // When
        VehicleRoute route = optOut.vehicleFor(taxpayer);

        // Then — both limbs run to the s.139(1) due date
        assertThat(route.vehicle()).isEqualTo(expectedVehicle);
        assertThat(route.form().map(FormRef::number).orElse("")).isEqualTo(expectedForm);
        assertThat(optOut.deadline()).isEqualTo(new DeadlineSpec.DueDateUnder("139(1)"));
    }

    @Test
    void load_whenThe2025PackIsCompiled_matchesItsLockAndValidates() {
        // When
        RulePack pack = pack();

        // Then
        assertThatCode(() -> PackValidator.validate(pack)).doesNotThrowAnyException();
        assertThat(pack.rules()).hasSize(201);
        assertThat(pack.regimes()).hasSize(2);
        assertThat(pack.computationCacheKey()).startsWith("2025-26|").contains("law=2025-26.law.103");
    }

    @Test
    void instruments_whenReadFromTheLock_nameTheIncomeTaxActOf1961() {
        // Given — "ACT NO. 43 OF 1961 [13th September, 1961.]"; s.1(3) "the 1st day of April, 1962"
        // When
        GoverningInstruments instruments = instruments();

        // Then
        assertThat(instruments.act().citation()).isEqualTo("Income-tax Act, 1961 (43 of 1961)");
        assertThat(instruments.act().assentedOn()).contains(LocalDate.of(1961, 9, 13));
        assertThat(instruments.act().inForceFrom()).isEqualTo(LocalDate.of(1962, 4, 1));
        assertThat(instruments.rules().verificationLevel()).isEqualTo(VerificationLevel.SOURCE_CHECKED);
    }

    // -- each rule cites the 1961 side for TY 2025-26 --------------------------------------------

    @ParameterizedTest(name = "{0} → {1} {2}")
    @CsvSource(delimiter = '|', value = {
        "RATE-SLAB-NEW-TAX-REGIME            | Income-tax Act, 1961 | s.115BAC(1A)(iii)",
        "RATE-SLAB-FIRST-SCHEDULE            | Finance Act, 2026    | First Schedule Part I-A Paragraph A",
        "RATE-SLAB-FIRST-SCHEDULE-AGE-60TO79 | Finance Act, 2026    | First Schedule Part I-A Paragraph A item (II)",
        "RATE-SLAB-FIRST-SCHEDULE-AGE-80     | Finance Act, 2026    | First Schedule Part I-A Paragraph A item (III)",
        "REBATE-NEW-TAX-REGIME               | Income-tax Act, 1961 | s.87A first proviso (a)",
        "REBATE-NEW-TAX-REGIME-EXCESS        | Income-tax Act, 1961 | s.87A first proviso (b)",
        "REBATE-RESIDENT-INDIVIDUAL          | Income-tax Act, 1961 | s.87A",
        "RATE-ROUND-TOTAL-INCOME             | Income-tax Act, 1961 | s.288A",
        "RATE-ROUND-PAYABLE                  | Income-tax Act, 1961 | s.288B",
        "RATE-HEALTH-EDUCATION-CESS          | Finance Act, 2026    | s.2(6)"
    })
    void versionAt_whenTheTaxYearIs2025_citesThe1961SideProvision(String rule, String instrument, String reference) {
        // When
        Provision provision = inForce(pack(), rule).provision1961();

        // Then
        assertThat(provision.presence()).isEqualTo(Presence.PRESENT);
        assertThat(provision.instrumentCitation()).isEqualTo(instrument);
        assertThat(provision.reference()).isEqualTo(reference);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "RATE-SLAB-NEW-TAX-REGIME",
        "REBATE-NEW-TAX-REGIME",
        "REBATE-NEW-TAX-REGIME-EXCESS",
        "REBATE-RESIDENT-INDIVIDUAL",
        "RATE-ROUND-TOTAL-INCOME",
        "RATE-ROUND-PAYABLE"
    })
    void versionAt_whenTheInstrumentDiffersFrom2026_isReadFromA2025Source(String rule) {
        // Given — D-082 C1: these six are governed in TY 2025-26 by an instrument other than the 2025 Act
        // When
        RuleVersion version = inForce(pack(), rule);

        // Then
        assertThat(version.provenance().sourceUrl().toString()).isNotEqualTo(ACT_2025_URL);
        assertThat(version.introducedBy().instrumentCitation()).doesNotStartWith("Income-tax Act, 2025");
    }

    // -- the figures (DR 08 T1, which runs in both years) --------------------------------------------

    @ParameterizedTest(name = "{0}: TI {2} → slab {3} → after rebate {4}")
    @CsvSource({
        // DR 08 T1-N1: "tax = 60,000 … tax after rebate = 0"
        "T1-N1, NEW_TAX_REGIME, 1200000, 60000, 0",
        // DR 08 T1-N3: "tax = 67,500 … tax after rebate = 50,000"
        "T1-N3, NEW_TAX_REGIME, 1250000, 67500, 50000",
        // DR 08 T1-O2: "tax = 12,502 … tax after rebate = 12,502"
        "T1-O2, FIRST_SCHEDULE_RATES, 500010, 12502, 12502"
    })
    void evaluate_whenTheTaxYearIs2025_reproducesTheT1Case(
            String golden, String regime, String totalIncome, String slabTax, String afterRebate) {
        // Given
        RulePack pack = pack();
        RegimeCode code = new RegimeCode(regime);

        // When
        var slab = NodeHarness.with(pack).regime(code).inputs(inputs2025(code))
                .given(NodeIds.ROUND_TOTALINCOME, Money.of(totalIncome))
                .given(NodeIds.SETOFF, new HeadsAfterSetOff(Money.ZERO, Map.of()))
                .run(new TaxByBasisNode());
        var rebated = NodeHarness.with(pack).regime(code).inputs(inputs2025(code))
                .given(NodeIds.ROUND_TOTALINCOME, Money.of(totalIncome))
                .given(NodeIds.TAX_BYBASIS, slab.value())
                .run(new RebateNode());

        // Then
        assertThat(slab.value()).as(golden).isEqualTo(TaxByBasis.atSlabRatesOnly(Money.of(slabTax)));
        assertThat(rebated.value()).as(golden).isEqualTo(TaxByBasis.atSlabRatesOnly(Money.of(afterRebate)));
    }

    @Test
    void evaluate_whenTheTaxYearIs2025_chargesTheCessAndRoundsThePayable() {
        // Given — DR 08 T1-N3 "cess 4% = 2,000" (no surcharge below 50L); T1-N2 "RATE-ROUND-002: ignore paise → 10"
        RulePack pack = pack();

        // When
        var cess = NodeHarness.with(pack).regime(NEW_TAX_REGIME).inputs(inputs2025(NEW_TAX_REGIME))
                .given(NodeIds.TAX_AFTERREBATE, TaxByBasis.atSlabRatesOnly(Money.of("50000")))
                .given(NodeIds.TAX_SURCHARGE, Money.ZERO)
                .run(new CessNode());
        var rounded = NodeHarness.with(pack).regime(NEW_TAX_REGIME).inputs(inputs2025(NEW_TAX_REGIME))
                .given(NodeIds.TAX_NETPAYABLE, Money.of("10.40"))
                .run(new RoundNetPayableNode());

        // Then
        assertThat(cess.value()).isEqualTo(MoneyValue.of(Money.of("2000")));
        assertThat(rounded.value()).isEqualTo(MoneyValue.of(Money.of("10")));
    }

    @Test
    void evaluate_whenTheTaxYearIs2025_citesThe2025Version() {
        // Given — the trace carries the version in force for the year, so a 2025-26 line names its own source
        RulePack pack = pack();

        // When
        var result = NodeHarness.with(pack).regime(FIRST_SCHEDULE_RATES).inputs(inputs2025(FIRST_SCHEDULE_RATES))
                .given(NodeIds.ROUND_TOTALINCOME, Money.of("500010"))
                .given(NodeIds.TAX_BYBASIS, TaxByBasis.atSlabRatesOnly(Money.of("12502")))
                .run(new RebateNode());

        // Then
        assertThat(result.trace().getFirst().provenance())
                .isEqualTo(inForce(pack, "REBATE-RESIDENT-INDIVIDUAL").provenance());
        assertThat(result.trace().getFirst().provenance().sourceUrl().toString()).isNotEqualTo(ACT_2025_URL);
    }
}
