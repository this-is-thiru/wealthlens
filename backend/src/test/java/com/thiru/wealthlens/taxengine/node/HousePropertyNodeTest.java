package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.HouseOccupancy;
import com.thiru.wealthlens.taxengine.input.HouseProperty;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Income from house property before interest (S2 ss.20–24; 1961 ss.22–26; D-155). Figures by hand, the first from
 * {@code DR 01} R16's worked example: rent ₹45,000 a month, local taxes ₹18,000.
 */
class HousePropertyNodeTest {

    private static final RegimeCode OLD = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final Rate WHOLE = Rate.percent("100");

    private static HouseProperty let(String expected, String rent, boolean vacant, String taxes) {
        return new HouseProperty(HouseOccupancy.LET, Money.of(expected), Money.of(rent), vacant, Money.of(taxes),
                WHOLE, Money.ZERO);
    }

    private static HouseProperty unlet(HouseOccupancy occupancy, String expected) {
        return new HouseProperty(occupancy, Money.of(expected), Money.ZERO, false, Money.ZERO, WHOLE, Money.ZERO);
    }

    /** R16's let property: 45,000 × 12 = 5,40,000 above an expected 4,80,000; taxes 18,000. */
    private static final HouseProperty R16_LET = let("480000", "540000", false, "18000");

    private static NodeResult run(int year, List<HouseProperty> properties) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, OLD, TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                new EnteredFacts(List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(),
                        List.of(), properties),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
        return NodeHarness.with(pack).regime(OLD).inputs(inputs).run(new HousePropertyNode());
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenR16sLetPropertyIsEntered_isTheAnnualValueLessThirtyPerCent(int year) {
        // When
        NodeResult result = run(year, List.of(R16_LET));

        // Then — annual value 5,40,000 − 18,000 = 5,22,000; 30% = 1,56,600; 5,22,000 − 1,56,600
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("365400")));
        assertThat(result.trace()).extracting(TraceLine::rule)
                .containsExactly(RuleIds.HP_ANNUAL_VALUE, RuleIds.HP_STANDARD_DEDUCTION);
        assertThat(result.trace().getFirst().out()).isEqualTo(MoneyValue.of(Money.of("522000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheOwnerResidesInIt_isNil(int year) {
        // When
        NodeResult result = run(year, List.of(unlet(HouseOccupancy.OWN_RESIDENCE, "480000")));

        // Then — S2 s.21(6); 1961 s.23(2)
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).first()
                .satisfies(line -> assertThat(line.rule()).isEqualTo(RuleIds.HP_OWN_RESIDENCE));
    }

    @Test
    void evaluate_whenAHouseIsNeitherLetNorAnOwnResidence_valuesItAtWhatItMightLetFor() {
        // When — expected 3,00,000
        NodeResult result = run(2026, List.of(unlet(HouseOccupancy.NOT_LET, "300000")));

        // Then — 3,00,000 − 90,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("210000")));
    }

    @Test
    void evaluate_whenAVacancyBroughtTheRentBelowTheExpectedValue_takesTheRent() {
        // When — expected 4,80,000, rent 3,60,000, vacant part of the year
        NodeResult vacant = run(2026, List.of(let("480000", "360000", true, "0")));
        NodeResult notVacant = run(2026, List.of(let("480000", "360000", false, "0")));

        // Then — s.21(2): 3,60,000 − 1,08,000 = 2,52,000; without the vacancy the higher, 4,80,000 − 1,44,000
        assertThat(vacant.value()).isEqualTo(MoneyValue.of(Money.of("252000")));
        assertThat(notVacant.value()).isEqualTo(MoneyValue.of(Money.of("336000")));
    }

    @Test
    void evaluate_whenTheAssesseeOwnsAShare_takesThatShareOfTheIncome() {
        // When — half of R16's let property
        HouseProperty half = new HouseProperty(HouseOccupancy.LET, Money.of("480000"), Money.of("540000"), false,
                Money.of("18000"), Rate.percent("50"), Money.ZERO);
        NodeResult result = run(2026, List.of(half));

        // Then — S2 s.24(1): half of 3,65,400
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("182700")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenArrearsOfRentAreReceived_addsThemLessThirtyPerCent(int year) {
        // When — 1,00,000 of arrears on a house now the owner's residence
        HouseProperty withArrears = new HouseProperty(HouseOccupancy.OWN_RESIDENCE, Money.of("480000"), Money.ZERO,
                false, Money.ZERO, WHOLE, Money.of("100000"));
        NodeResult result = run(year, List.of(withArrears));

        // Then — S2 s.23; 1961 s.25A: 1,00,000 − 30,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("70000")));
    }

    @Test
    void evaluate_whenThreeHousesAreOwnResidences_isRefused() {
        // When / Then — S2 s.21(7)(a): two
        HouseProperty home = unlet(HouseOccupancy.OWN_RESIDENCE, "300000");
        assertThatThrownBy(() -> run(2026, List.of(home, home, home)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only 2");
    }

    @Test
    void evaluate_whenTwoHousesAreOwnResidences_valuesBothAtNil() {
        // When
        HouseProperty home = unlet(HouseOccupancy.OWN_RESIDENCE, "300000");
        NodeResult result = run(2026, List.of(home, home));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void evaluate_whenLocalTaxesExceedTheValue_isRefused() {
        // When / Then — a loss waits on interest and set-off
        assertThatThrownBy(() -> run(2026, List.of(let("10000", "10000", false, "20000"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceed its value");
    }

    @Test
    void evaluate_whenNoPropertyIsEntered_isNilAndUntraced() {
        // When
        NodeResult result = run(2026, List.of());

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).isEmpty();
    }

    @Test
    void construction_whenAHouseWithRentIsNotEnteredAsLet_isRefused() {
        // When / Then — S2 s.21(7)(b): let at any time is let
        assertThatThrownBy(() -> new HouseProperty(HouseOccupancy.OWN_RESIDENCE, Money.of("300000"),
                        Money.of("10000"), false, Money.ZERO, WHOLE, Money.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("enter it as LET");
    }
}
