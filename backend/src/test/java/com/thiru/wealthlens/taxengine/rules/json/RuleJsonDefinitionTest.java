package com.thiru.wealthlens.taxengine.rules.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.definition.BucketPart;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsParts;
import com.thiru.wealthlens.taxengine.rules.definition.ChargeDeduction;
import com.thiru.wealthlens.taxengine.rules.definition.ChargeDeductionLimb;
import com.thiru.wealthlens.taxengine.rules.definition.CommutedPensionShare;
import com.thiru.wealthlens.taxengine.rules.definition.DayWindow;
import com.thiru.wealthlens.taxengine.rules.definition.DearnessAllowanceTest;
import com.thiru.wealthlens.taxengine.rules.definition.DisabledTransportLimit;
import com.thiru.wealthlens.taxengine.rules.definition.ExcludedAssets;
import com.thiru.wealthlens.taxengine.rules.definition.GiftAggregation;
import com.thiru.wealthlens.taxengine.rules.definition.GiftThreshold;
import com.thiru.wealthlens.taxengine.rules.definition.HeadTotal;
import com.thiru.wealthlens.taxengine.rules.definition.IndexByYear;
import com.thiru.wealthlens.taxengine.rules.definition.LossCarryForward;
import com.thiru.wealthlens.taxengine.rules.definition.MovableAssetKind;
import com.thiru.wealthlens.taxengine.rules.definition.SalaryAveragingPeriod;
import com.thiru.wealthlens.taxengine.rules.definition.SalaryBase;
import com.thiru.wealthlens.taxengine.rules.definition.ShortTermHoldingPeriod;
import com.thiru.wealthlens.taxengine.rules.definition.SttChargeability;
import com.thiru.wealthlens.taxengine.rules.definition.TaxYearSpan;
import com.thiru.wealthlens.taxengine.rules.definition.TransportDisability;
import com.thiru.wealthlens.taxengine.rules.definition.VehicleComputation;
import com.thiru.wealthlens.taxengine.rules.definition.VehiclePath;
import com.thiru.wealthlens.taxengine.rules.definition.WearAndTear;
import com.thiru.wealthlens.taxengine.rules.definition.WearAndTearMethod;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A rule version stating a definition (D-091, D-092): read like a rounding, at most one of cap, relief, rounding
 * and definition, and a head the engine knows. The values are parse inputs checked for round-trip, not law.
 */
class RuleJsonDefinitionTest {

    private static final CapitalGainsPart SHORT_TERM_SLAB = new CapitalGainsPart("SHORT_TERM_SLAB");

    private static final CapitalGainsPart LONG_TERM_OTHER = new CapitalGainsPart("LONG_TERM_OTHER");

    private static final String PATH = "law/rules/X.json.versions[0]";

    private static final String REST = """
            "validity": { "type": "Between", "from": "2026-04-01" },
            "regimes": { "type": "AllRegimes" },
            "provision1961": { "instrument": "Income-tax Act, 1961", "reference": "s.17(1)", "presence": "PRESENT" },
            "provision2025": { "instrument": "Income-tax Act, 2025", "reference": "s.16", "presence": "PRESENT" },
            "introducedBy": {
              "instrumentCitation": "Income-tax Act, 2025 (30 of 2025), s.16",
              "gazetteRef": "CG-DL-E-22082025-265620",
              "notifiedOn": "2025-08-21", "effectiveFrom": "2026-04-01",
              "retrospective": false,
              "sourceUrl": "https://egazette.gov.in/WriteReadData/2025/265620.pdf",
              "verificationLevel": "SOURCE_CHECKED" },
            "legalStatus": "IN_FORCE", "publicationStatus": "PUBLISHED",
            "provenance": {
              "sourceUrl": "https://egazette.gov.in/WriteReadData/2025/265620.pdf",
              "sourceDescription": "Gazette CG-DL-E-22082025-265620, s.16",
              "accessedOn": "2026-09-26", "verificationLevel": "SOURCE_CHECKED" }""";

    private static final String SALARIES_TOTAL = """
            "definition": { "type": "HeadTotal", "head": "SALARIES" }""";

    private static RuleVersion versionWith(String extra) {
        return RuleJson.version(PackJsonTest.json("{ " + extra + ", " + REST + " }"), PATH);
    }

    @Test
    void version_whenHeadTotal_readsTheHead() {
        // When
        RuleVersion version = versionWith(SALARIES_TOTAL);

        // Then
        assertThat(version.definition()).contains(new HeadTotal(HeadCode.SALARIES));
        assertThat(version.cap()).isEmpty();
        assertThat(version.relief()).isEmpty();
        assertThat(version.rounding()).isEmpty();
    }

    @Test
    void version_whenAGiftThresholdWithAShare_readsTheShareAndAggregation() {
        // When
        RuleVersion version = versionWith("""
                "definition": { "type": "GiftThreshold", "exceeds": "50000", "considerationSharePercent": "10",
                  "aggregation": "EACH_RECEIPT" }""");

        // Then
        assertThat(version.definition()).contains(new GiftThreshold(Money.of("50000"), Optional.of(Rate.percent("10")),
                GiftAggregation.EACH_RECEIPT));
    }

    @Test
    void version_whenAGiftThresholdHasNoShare_readsItAsAbsent() {
        // When
        RuleVersion version = versionWith("""
                "definition": { "type": "GiftThreshold", "exceeds": "50000", "aggregation": "YEAR_TOTAL" }""");

        // Then
        assertThat(version.definition()).contains(new GiftThreshold(Money.of("50000"), Optional.empty(),
                GiftAggregation.YEAR_TOTAL));
    }

    @Test
    void version_whenAGiftThresholdNamesAnUnknownAggregation_isRefusedNamingThePath() {
        // When / Then
        assertThatThrownBy(() -> versionWith("""
                "definition": { "type": "GiftThreshold", "exceeds": "50000", "aggregation": "POOLED" }"""))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("aggregation");
    }

    @Test
    void version_whenSalaryBase_readsIt() {
        // When
        RuleVersion version = versionWith("""
                "definition": { "type": "SalaryBase", "dearnessAllowance": "WHERE_TERMS_PROVIDE" }""");

        // Then
        assertThat(version.definition()).contains(new SalaryBase(DearnessAllowanceTest.WHERE_TERMS_PROVIDE));
        assertThat(version.cap()).isEmpty();
    }

    @Test
    void version_whenAFlatTransportLimit_readsItsConditionsAndAmount() {
        // When
        RuleVersion version = versionWith("""
                "definition": { "type": "DisabledTransportLimit",
                  "conditions": [ "BLIND", "DEAF_AND_DUMB", "ORTHOPAEDIC_LOWER_EXTREMITIES" ],
                  "perMonth": "3200", "plusDearnessAllowanceThereon": false }""");

        // Then
        assertThat(version.definition()).contains(new DisabledTransportLimit(
                Set.of(TransportDisability.BLIND, TransportDisability.DEAF_AND_DUMB,
                        TransportDisability.ORTHOPAEDIC_LOWER_EXTREMITIES),
                Money.of("3200"), Optional.empty(), false));
    }

    @Test
    void version_whenATransportLimitByCity_readsBothAmounts() {
        // When
        RuleVersion version = versionWith("""
                "definition": { "type": "DisabledTransportLimit",
                  "conditions": [ "BLIND", "DEAF_AND_DUMB", "ORTHOPAEDIC_LOWER_EXTREMITIES",
                                  "ORTHOPAEDIC_UPPER_EXTREMITIES" ],
                  "perMonth": "15000", "perMonthInOtherCities": "8000", "plusDearnessAllowanceThereon": true }""");

        // Then
        assertThat(version.definition()).contains(new DisabledTransportLimit(
                EnumSet.allOf(TransportDisability.class), Money.of("15000"), Optional.of(Money.of("8000")), true));
    }

    @Test
    void version_whenACommutedPensionShare_readsBothDivisors() {
        // When
        RuleVersion version = versionWith("""
                "definition": { "type": "CommutedPensionShare", "oneOverWhereGratuityReceived": 3,
                  "oneOverOtherwise": 2 }""");

        // Then
        assertThat(version.definition()).contains(new CommutedPensionShare(3, 2));
    }

    @Test
    void version_whenAnIndexByYear_readsEachFinancialYear() {
        // When
        RuleVersion version = versionWith("""
                "definition": { "type": "IndexByYear", "years": [
                  { "financialYear": "2023-24", "index": 348 }, { "financialYear": "2024-25", "index": 363 } ] }""");

        // Then
        assertThat(version.definition())
                .contains(new IndexByYear(Map.of(TaxYearId.of(2023), 348, TaxYearId.of(2024), 363)));
    }

    @Test
    void version_whenExcludedAssets_readsTheKinds() {
        // When
        RuleVersion version = versionWith("""
                "definition": { "type": "ExcludedAssets", "kinds": [ "LAPTOP_OR_COMPUTER", "TABLET" ] }""");

        // Then
        assertThat(version.definition())
                .contains(new ExcludedAssets(Set.of(MovableAssetKind.LAPTOP_OR_COMPUTER, MovableAssetKind.TABLET)));
    }

    @Test
    void version_whenAnExcludedAssetKindIsUnknown_isRefusedListingTheKnown() {
        // When / Then
        assertThatThrownBy(() -> versionWith("""
                        "definition": { "type": "ExcludedAssets", "kinds": [ "TELEVISION" ] }"""))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("MOBILE_PHONE");
    }

    @Test
    void version_whenALossCarryForward_readsItsHeadSpanAndCondition() {
        // When
        RuleVersion version = versionWith("""
                "definition": { "type": "LossCarryForward", "absorbedBy": "CAPITAL_GAINS", "succeedingYears": 8,
                  "requiresReturnFiledInTime": true }""");

        // Then
        assertThat(version.definition())
                .contains(new LossCarryForward(HeadCode.CAPITAL_GAINS, new TaxYearSpan(8), true));
    }

    @Test
    void version_whenALossCarryForwardNamesAnUnknownHead_isRefusedListingTheKnown() {
        // When / Then
        assertThatThrownBy(() -> versionWith("""
                        "definition": { "type": "LossCarryForward", "absorbedBy": "BUSINESS_INCOME",
                          "succeedingYears": 8, "requiresReturnFiledInTime": false }"""))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("HOUSE_PROPERTY");
    }

    @Test
    void version_whenABucketPart_readsThePart() {
        // When
        RuleVersion version = versionWith("""
                "definition": { "type": "BucketPart", "part": "LONG_TERM_OTHER" }""");

        // Then
        assertThat(version.definition()).contains(new BucketPart(LONG_TERM_OTHER));
    }

    @Test
    void version_whenWearAndTear_readsTheRateAndMethod() {
        // When
        RuleVersion version = versionWith("""
                "definition": { "type": "WearAndTear", "percent": "20", "method": "REDUCING_BALANCE" }""");

        // Then
        assertThat(version.definition())
                .contains(new WearAndTear(Rate.percent("20"), WearAndTearMethod.REDUCING_BALANCE));
    }

    @Test
    void version_whenWearAndTearNamesNoMethod_isRefusedNamingThePath() {
        // When / Then
        assertThatThrownBy(() -> versionWith("""
                        "definition": { "type": "WearAndTear", "percent": "20", "method": "STRAIGHT" }"""))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("definition.method")
                .hasMessageContaining("STRAIGHT");
    }

    @Test
    void version_whenAVehiclePath_readsTheComputation() {
        // When
        RuleVersion version = versionWith("""
                "definition": { "type": "VehiclePath", "computation": "RUNNING_AND_WEAR_LESS_CHARGE" }""");

        // Then
        assertThat(version.definition())
                .contains(new VehiclePath(VehicleComputation.RUNNING_AND_WEAR_LESS_CHARGE));
    }

    @Test
    void version_whenAVehiclePathNamesNoComputation_isRefusedNamingThePath() {
        // When / Then — the computations are closed: a new one is new arithmetic (D-330 C1a)
        assertThatThrownBy(() -> versionWith("""
                        "definition": { "type": "VehiclePath", "computation": "SL_1C_I" }"""))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("definition.computation")
                .hasMessageContaining("SL_1C_I");
    }

    @Test
    void version_whenAChargeDeduction_readsTheLimb() {
        // When
        RuleVersion version = versionWith("""
                "definition": { "type": "ChargeDeduction", "limb": "TRANSFER_EXPENDITURE" }""");

        // Then
        assertThat(version.definition())
                .contains(new ChargeDeduction(ChargeDeductionLimb.TRANSFER_EXPENDITURE));
    }

    @Test
    void version_whenAChargeDeductionNamesNoLimb_isRefusedNamingThePath() {
        // When / Then — the limbs are closed: a new one is new arithmetic (W-162a)
        assertThatThrownBy(() -> versionWith("""
                        "definition": { "type": "ChargeDeduction", "limb": "DEDUCTIBLE" }"""))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("definition.limb")
                .hasMessageContaining("DEDUCTIBLE");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"long_term_slab", "X", "LONG-TERM"})
    void version_whenABucketPartNamesAPartNotShapedLikeOne_isRefusedNamingThePath(String code) {
        // When / Then — whether a well-shaped part exists is CG-PARTS' to say, by the build's pack validation
        // (D-309); its shape is checked here
        assertThatThrownBy(() -> versionWith("""
                        "definition": { "type": "BucketPart", "part": "%s" }""".formatted(code)))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("definition")
                .hasMessageContaining("upper-case with underscores");
    }

    @Test
    void version_whenAnSttChargeability_readsTheCode() {
        // When
        RuleVersion version = versionWith("""
                "definition": { "type": "SttChargeability", "code": "CHARGEABLE" }""");

        // Then
        assertThat(version.definition()).contains(new SttChargeability("CHARGEABLE"));
    }

    @Test
    void version_whenAShortTermHoldingPeriod_readsTheMonths() {
        // When
        RuleVersion version = versionWith("""
                "definition": { "type": "ShortTermHoldingPeriod", "months": 12 }""");

        // Then
        assertThat(version.definition()).contains(new ShortTermHoldingPeriod(12));
    }

    @Test
    void version_whenAShortTermHoldingPeriodHasNoMonths_isRefused() {
        // When / Then — a period of none would make every holding long-term
        assertThatThrownBy(() -> versionWith("""
                        "definition": { "type": "ShortTermHoldingPeriod", "months": 0 }"""))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("positive number of months");
    }

    @Test
    void version_whenCapitalGainsParts_readsEachPartInOrder() {
        // When
        RuleVersion version = versionWith("""
                "definition": { "type": "CapitalGainsParts", "parts": [
                  { "part": "SHORT_TERM_SLAB", "term": "SHORT_TERM", "canAbsorbLongTermLoss": false },
                  { "part": "LONG_TERM_OTHER", "term": "LONG_TERM", "canAbsorbLongTermLoss": true } ] }""");

        // Then
        assertThat(version.definition()).contains(new CapitalGainsParts(List.of(
                new CapitalGainsParts.Declared(SHORT_TERM_SLAB, "SHORT_TERM", false),
                new CapitalGainsParts.Declared(LONG_TERM_OTHER, "LONG_TERM", true))));
    }

    @Test
    void version_whenCapitalGainsPartsNameAPartTwice_isRefused() {
        // When / Then — two terms for one part would make its loss's term depend on which is read
        assertThatThrownBy(() -> versionWith("""
                        "definition": { "type": "CapitalGainsParts", "parts": [
                          { "part": "LONG_TERM_OTHER", "term": "LONG_TERM", "canAbsorbLongTermLoss": true },
                          { "part": "LONG_TERM_OTHER", "term": "SHORT_TERM", "canAbsorbLongTermLoss": false } ] }"""))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("LONG_TERM_OTHER twice");
    }

    @Test
    void version_whenACapitalGainsPartHasABlankTerm_isRefusedNamingThePath() {
        // When / Then — read as text, which refuses a blank before the part is built
        assertThatThrownBy(() -> versionWith("""
                        "definition": { "type": "CapitalGainsParts", "parts": [
                          { "part": "SHORT_TERM_SLAB", "term": " ", "canAbsorbLongTermLoss": false } ] }"""))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("definition.parts[0].term must be a non-empty string");
    }

    @Test
    void version_whenASalaryAveragingPeriod_readsTheMonths() {
        // When
        RuleVersion version = versionWith("""
                "definition": { "type": "SalaryAveragingPeriod", "months": 10 }""");

        // Then
        assertThat(version.definition()).contains(new SalaryAveragingPeriod(10));
    }

    @Test
    void version_whenASalaryAveragingPeriodHasNoMonths_isRefused() {
        // When / Then
        assertThatThrownBy(() -> versionWith("""
                        "definition": { "type": "SalaryAveragingPeriod", "months": 0 }"""))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("positive number of months");
    }

    @Test
    void version_whenADayWindow_readsTheDays() {
        // When
        RuleVersion version = versionWith("""
                "definition": { "type": "DayWindow", "days": 180 }""");

        // Then
        assertThat(version.definition()).contains(new DayWindow(180));
    }

    @Test
    void version_whenAnIndexYearIsMisnamed_isRefusedNamingThePath() {
        // When / Then — "2023-25" is no financial year
        assertThatThrownBy(() -> versionWith("""
                        "definition": { "type": "IndexByYear", "years": [
                          { "financialYear": "2023-25", "index": 348 } ] }"""))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("financialYear");
    }

    @Test
    void version_whenAnIndexYearIsListedTwice_isRefused() {
        // When / Then
        assertThatThrownBy(() -> versionWith("""
                        "definition": { "type": "IndexByYear", "years": [
                          { "financialYear": "2023-24", "index": 348 }, { "financialYear": "2023-24", "index": 349 } ] }"""))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("twice");
    }

    @Test
    void version_whenATransportConditionIsUnknown_isRefusedListingTheKnown() {
        // When / Then
        assertThatThrownBy(() -> versionWith("""
                        "definition": { "type": "DisabledTransportLimit", "conditions": [ "LAME" ],
                          "perMonth": "3200", "plusDearnessAllowanceThereon": false }"""))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining(PATH + ".definition")
                .hasMessageContaining("BLIND");
    }

    @Test
    void version_whenASalaryBaseAsksTheRetirementTest_readsIt() {
        // When — D-111: the accommodation perquisite's "salary" counts DA only where it counts for retirement
        RuleVersion version = versionWith("""
                "definition": { "type": "SalaryBase", "dearnessAllowance": "WHERE_IT_COUNTS_FOR_RETIREMENT" }""");

        // Then
        assertThat(version.definition())
                .contains(new SalaryBase(DearnessAllowanceTest.WHERE_IT_COUNTS_FOR_RETIREMENT));
    }

    @Test
    void version_whenASalaryBaseOmitsItsDearnessAllowanceTest_isRefused() {
        // When / Then — two tests exist, so a base must name one
        assertThatThrownBy(() -> versionWith("""
                        "definition": { "type": "SalaryBase" }"""))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("dearnessAllowance");
    }

    @Test
    void version_whenSalaryBaseBesideACap_isRefusedNamingThePath() {
        // Given
        String both = """
                "cap": { "type": "Fixed", "amount": "0" },
                "definition": { "type": "SalaryBase", "dearnessAllowance": "WHERE_TERMS_PROVIDE" }""";

        // When / Then
        assertThatThrownBy(() -> versionWith(both))
                .isInstanceOf(PackValidationException.class)
                .hasMessageStartingWith(PATH)
                .hasMessageContaining("a cap")
                .hasMessageContaining("a definition");
    }

    @Test
    void version_whenDefinitionWithoutConditions_loads() {
        // When
        RuleVersion version = versionWith(SALARIES_TOTAL);

        // Then
        assertThat(version.conditions()).isEmpty();
    }

    @Test
    void version_whenCapOnly_hasNoDefinition() {
        // When
        RuleVersion version = versionWith("""
                "cap": { "type": "Fixed", "amount": "0" }""");

        // Then
        assertThat(version.definition()).isEmpty();
    }

    @Test
    void version_whenCapAndDefinition_isRefusedNamingThePath() {
        // Given
        String both = """
                "cap": { "type": "Fixed", "amount": "0" },
                """ + SALARIES_TOTAL;

        // When / Then
        assertThatThrownBy(() -> versionWith(both))
                .isInstanceOf(PackValidationException.class)
                .hasMessageStartingWith(PATH)
                .hasMessageContaining("a cap")
                .hasMessageContaining("a definition");
    }

    @Test
    void version_whenRoundingAndDefinition_isRefusedNamingThePath() {
        // Given
        String both = """
                "rounding": { "toNearest": "10", "roundsUpFrom": "5" },
                """ + SALARIES_TOTAL;

        // When / Then
        assertThatThrownBy(() -> versionWith(both))
                .isInstanceOf(PackValidationException.class)
                .hasMessageStartingWith(PATH)
                .hasMessageContaining("a rounding")
                .hasMessageContaining("a definition");
    }

    @Test
    void version_whenTheDefinitionTypeIsUnknown_isRefusedListingTheKnown() {
        // When / Then
        assertThatThrownBy(() -> versionWith("""
                        "definition": { "type": "HeadAverage", "head": "SALARIES" }"""))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining(PATH + ".definition")
                .hasMessageContaining("HeadTotal")
                .hasMessageContaining("SalaryBase");
    }

    @Test
    void version_whenTheHeadIsUnknown_isRefusedListingTheKnown() {
        // When / Then
        assertThatThrownBy(() -> versionWith("""
                        "definition": { "type": "HeadTotal", "head": "WAGES" }"""))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining(PATH + ".definition")
                .hasMessageContaining("SALARIES");
    }
}
