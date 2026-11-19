package com.thiru.wealthlens.taxengine.rules.pack;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.cap.SlabLadder;
import com.thiru.wealthlens.taxengine.rules.cap.SlabLadder.Band;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The surcharge rates for an individual as FA 2026 states them, in both years (D-084): two ladders per
 * regime, because the Act tests the 10% and 15% clauses on total income including dividends and the
 * special gains, and the 25% and 37% clauses on total income excluding them. Every band below is typed
 * from the Act's clauses — s.2(4)(b) Table Sl. 10 (p.7), Part I-A Para F Table 1 Sl. 1 (pp.82–83),
 * s.3(4)(b) Table Sl. 10 (pp.13–14), Part I-B Para F Table 1 Sl. 1 (pp.86–87) — not read from the files.
 */
class SurchargeLadderTest {

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");
    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final BaseId TOTAL_INCOME = new BaseId("ROUNDED_TOTAL_INCOME");
    private static final BaseId EXCLUDING = new BaseId("TOTAL_INCOME_EXCLUDING_DIVIDENDS_AND_GAINS");

    private static Band band(String lowerExclusive, String upperInclusive, String ratePercent) {
        return new Band(Money.of(lowerExclusive), Optional.ofNullable(upperInclusive).map(Money::of),
                Rate.percent(ratePercent));
    }

    /** Clauses (i) 10% above 50L, (ii) 15% above 1Cr, and "not covered" 15% above 2Cr, as one ladder. */
    private static final List<Band> ON_TOTAL_INCOME = List.of(
            band("0", "5000000", "0"),
            band("5000000", "10000000", "10"),
            band("10000000", null, "15"));

    private static RuleVersion inForce(int year, String rule) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider("rulepacks", Set.of(taxYear),
                        new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        return pack.rule(new RuleId(rule)).versionAt(TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)));
    }

    private static SlabLadder ladder(RuleVersion version) {
        assertThat(version.cap()).containsInstanceOf(SlabLadder.class);
        return (SlabLadder) version.cap().orElseThrow();
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void newTaxRegime_whenReadForTheYear_isTenAndFifteenOnTotalIncome(int year) {
        // When
        RuleVersion version = inForce(year, "RATE-SUR-NEW-TAX-REGIME");

        // Then
        assertThat(ladder(version).bands()).isEqualTo(ON_TOTAL_INCOME);
        assertThat(ladder(version).base()).isEqualTo(TOTAL_INCOME);
        assertThat(version.regimes().appliesIn(NEW_TAX_REGIME)).isTrue();
        assertThat(version.regimes().appliesIn(FIRST_SCHEDULE_RATES)).isFalse();
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void newTaxRegimeExcluding_whenReadForTheYear_isTwentyFiveAboveTwoCroreWithNoThirtySevenBand(int year) {
        // Given — clause (iii): "(excluding dividend income or capital gains …) exceeds Rs. 2,00,00,000,
        // at the rate of twenty-five per cent."; there is no further clause
        List<Band> expected = List.of(band("0", "20000000", "0"), band("20000000", null, "25"));

        // When
        RuleVersion version = inForce(year, "RATE-SUR-NEW-TAX-REGIME-EXCLUDING");

        // Then
        assertThat(ladder(version).bands()).isEqualTo(expected);
        assertThat(ladder(version).base()).isEqualTo(EXCLUDING);
        assertThat(version.regimes().appliesIn(NEW_TAX_REGIME)).isTrue();
        assertThat(version.regimes().appliesIn(FIRST_SCHEDULE_RATES)).isFalse();
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void firstSchedule_whenReadForTheYear_isTenAndFifteenOnTotalIncome(int year) {
        // When
        RuleVersion version = inForce(year, "RATE-SUR-FIRST-SCHEDULE");

        // Then
        assertThat(ladder(version).bands()).isEqualTo(ON_TOTAL_INCOME);
        assertThat(ladder(version).base()).isEqualTo(TOTAL_INCOME);
        assertThat(version.regimes().appliesIn(FIRST_SCHEDULE_RATES)).isTrue();
        assertThat(version.regimes().appliesIn(NEW_TAX_REGIME)).isFalse();
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void firstScheduleExcluding_whenReadForTheYear_isTwentyFiveThenThirtySeven(int year) {
        // Given — clauses (iii) "exceeds Rs. 2,00,00,000 but does not exceed Rs. 5,00,00,000, at the rate
        // of 25 per cent." and (iv) "exceeds Rs. 5,00,00,000, at the rate of 37 per cent."
        List<Band> expected = List.of(
                band("0", "20000000", "0"), band("20000000", "50000000", "25"), band("50000000", null, "37"));

        // When
        RuleVersion version = inForce(year, "RATE-SUR-FIRST-SCHEDULE-EXCLUDING");

        // Then
        assertThat(ladder(version).bands()).isEqualTo(expected);
        assertThat(ladder(version).base()).isEqualTo(EXCLUDING);
        assertThat(version.regimes().appliesIn(FIRST_SCHEDULE_RATES)).isTrue();
        assertThat(version.regimes().appliesIn(NEW_TAX_REGIME)).isFalse();
    }
}
