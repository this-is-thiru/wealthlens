package com.thiru.wealthlens.taxengine.rules.pack;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.cap.SlabLadder;
import com.thiru.wealthlens.taxengine.rules.cap.SlabLadder.Band;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.relief.ReliefContext;
import com.thiru.wealthlens.taxengine.rules.relief.ReliefResult;
import com.thiru.wealthlens.taxengine.rules.relief.RuleRelief;
import com.thiru.wealthlens.taxengine.rules.relief.ThresholdLadderCeiling;
import com.thiru.wealthlens.taxengine.rules.relief.UnrelievedTax;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Surcharge marginal relief, W = U + V, as FA 2026 parameterises it for an individual in both years
 * (D-085). The thresholds are typed from the Act's column C — s.2(5) Table Sl. 6 (pp.8–9), Part I-A Para F
 * Table 2 Sl. 1 (p.84), s.3(5) Table Sl. 6 (p.15), Part I-B Para F Table 2 Sl. 1 (p.88) — not read from
 * the files; the relief amounts are worked by hand in {@code plans/W-055.md}.
 */
class SurchargeReliefTest {

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");
    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");

    private static RulePack pack(int year) {
        TaxYearId taxYear = TaxYearId.of(year);
        return new ClasspathRulePackProvider("rulepacks", Set.of(taxYear),
                        new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
    }

    private static RuleVersion inForce(RulePack pack, int year, String rule) {
        return pack.rule(new RuleId(rule))
                .versionAt(TemporalContext.forTaxYear(TaxYearId.of(year), LocalDate.of(year, 9, 21)));
    }

    private static RuleRelief relief(int year, String rule) {
        return inForce(pack(year), year, rule).relief().orElseThrow();
    }

    private static Set<Money> thresholds(RuleRelief relief) {
        assertThat(relief.shape()).isInstanceOf(ThresholdLadderCeiling.class);
        return Set.copyOf(((ThresholdLadderCeiling) relief.shape()).thresholds());
    }

    private static Set<Money> amounts(String... rupees) {
        return Stream.of(rupees).map(Money::of).collect(Collectors.toSet());
    }

    /** The lower bounds at which either ladder starts to charge; the drift guard's other side (D-085). */
    private static Set<Money> ladderEntryPoints(RulePack pack, int year, String... ladders) {
        return Stream.of(ladders)
                .map(rule -> (SlabLadder) inForce(pack, year, rule).cap().orElseThrow())
                .flatMap(ladder -> ladder.bands().stream())
                .map(Band::lowerExclusive)
                .filter(lower -> lower.isGreaterThan(Money.ZERO))
                .collect(Collectors.toSet());
    }

    /** U at exactly one income, refusing to be asked for any other — so the test also pins the threshold. */
    private static UnrelievedTax onlyAt(String threshold, String taxAndSurcharge) {
        return income -> {
            assertThat(income).isEqualTo(Money.of(threshold));
            return Money.of(taxAndSurcharge);
        };
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void firstScheduleCeiling_whenReadForTheYear_hasTheFourTableTwoThresholds(int year) {
        // When
        RuleVersion version = inForce(pack(year), year, "RATE-SUR-FIRST-SCHEDULE-CEILING");

        // Then — Table 2 Sl. 1 column C: Rs. 50,00,000; 1,00,00,000; 2,00,00,000; 5,00,00,000
        RuleRelief relief = version.relief().orElseThrow();
        assertThat(thresholds(relief)).isEqualTo(amounts("5000000", "10000000", "20000000", "50000000"));
        assertThat(relief.limitedToSlabRateTax()).isFalse();
        assertThat(version.regimes().appliesIn(FIRST_SCHEDULE_RATES)).isTrue();
        assertThat(version.regimes().appliesIn(NEW_TAX_REGIME)).isFalse();
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void newTaxRegimeCeiling_whenReadForTheYear_hasThreeThresholdsAndNoFiveCrore(int year) {
        // When
        RuleVersion version = inForce(pack(year), year, "RATE-SUR-NEW-TAX-REGIME-CEILING");

        // Then — Table Sl. 6 column C: Rs. 50,00,000; 1,00,00,000; 2,00,00,000, the last open-ended (H-1)
        RuleRelief relief = version.relief().orElseThrow();
        assertThat(thresholds(relief)).isEqualTo(amounts("5000000", "10000000", "20000000"));
        assertThat(relief.limitedToSlabRateTax()).isFalse();
        assertThat(version.regimes().appliesIn(NEW_TAX_REGIME)).isTrue();
        assertThat(version.regimes().appliesIn(FIRST_SCHEDULE_RATES)).isFalse();
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void ceilings_whenComparedWithTheSurchargeLadders_startWhereTheLaddersStartCharging(int year) {
        // Given — the Act states Table 2 apart from Table 1; they agree today, and a Finance Act that moves
        // one without the other must fail here rather than relieve at the wrong income
        RulePack pack = pack(year);

        // When
        Set<Money> firstSchedule = thresholds(
                inForce(pack, year, "RATE-SUR-FIRST-SCHEDULE-CEILING").relief().orElseThrow());
        Set<Money> newTaxRegime = thresholds(
                inForce(pack, year, "RATE-SUR-NEW-TAX-REGIME-CEILING").relief().orElseThrow());

        // Then
        assertThat(firstSchedule).isEqualTo(ladderEntryPoints(
                pack, year, "RATE-SUR-FIRST-SCHEDULE", "RATE-SUR-FIRST-SCHEDULE-EXCLUDING"));
        assertThat(newTaxRegime).isEqualTo(ladderEntryPoints(
                pack, year, "RATE-SUR-NEW-TAX-REGIME", "RATE-SUR-NEW-TAX-REGIME-EXCLUDING"));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void firstScheduleCeiling_whenIncomeIsJustAboveOneCrore_relievesAtOneCroreNotFiftyLakh(int year) {
        // Given — TI 1,00,05,000: tax 1,12,500 + 30% × 90,05,000 = 28,14,000; surcharge 15% = 4,22,100;
        // unrelieved 32,36,100. U at 1,00,00,000 = 28,12,500 + 10% = 30,93,750; V = 5,000; W = 30,98,750
        RuleRelief relief = relief(year, "RATE-SUR-FIRST-SCHEDULE-CEILING");
        ReliefContext context = new ReliefContext(
                Money.of("10005000"), Money.of("3236100"), Optional.of(onlyAt("10000000", "3093750")));

        // When
        ReliefResult result = relief.shape().apply(context);

        // Then — 32,36,100 − 30,98,750
        assertThat(result.amount()).isEqualTo(Money.of("137350"));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void newTaxRegimeCeiling_whenIncomeIsJustAboveTwoCrore_relievesTheJumpToTwentyFivePercent(int year) {
        // Given — TI 2,00,10,000: tax 3,00,000 + 30% × 1,76,10,000 = 55,83,000; surcharge 25% = 13,95,750;
        // unrelieved 69,78,750. U at 2,00,00,000 = 55,80,000 + 15% = 64,17,000; V = 10,000; W = 64,27,000
        RuleRelief relief = relief(year, "RATE-SUR-NEW-TAX-REGIME-CEILING");
        ReliefContext context = new ReliefContext(
                Money.of("20010000"), Money.of("6978750"), Optional.of(onlyAt("20000000", "6417000")));

        // When
        ReliefResult result = relief.shape().apply(context);

        // Then — 69,78,750 − 64,27,000
        assertThat(result.amount()).isEqualTo(Money.of("551750"));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void newTaxRegimeCeiling_whenIncomeIsAboveFiveCrore_pricesUAtTwoCrore(int year) {
        // Given — there is no Rs. 5,00,00,000 row for this person, so U is asked for at 2,00,00,000
        RuleRelief relief = relief(year, "RATE-SUR-NEW-TAX-REGIME-CEILING");
        List<Money> askedFor = new ArrayList<>();
        UnrelievedTax recording = income -> {
            askedFor.add(income);
            return Money.ZERO;
        };

        // When
        relief.shape().apply(new ReliefContext(Money.of("50010000"), Money.of("1"), Optional.of(recording)));

        // Then
        assertThat(askedFor).containsExactly(Money.of("20000000"));
    }
}
