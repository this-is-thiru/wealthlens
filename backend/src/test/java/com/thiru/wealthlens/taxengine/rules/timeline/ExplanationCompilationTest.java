package com.thiru.wealthlens.taxengine.rules.timeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.cap.LeastOf;
import com.thiru.wealthlens.taxengine.rules.id.ReasonCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.ExplanationJson;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonCodes;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code content/explanations.json} compiled into each year's content layer (W-116, D-671): one file serves every year,
 * each year keeping its own rules and the limits its versions name; a rule or limit the timeline names nowhere is refused;
 * every rule whose cap is a least-of is explained (F37.3); and every rule of a year's pack is either explained or listed
 * in {@code notALine} with the reason it names no trace line of its own (F37.2; W-325d, D-672 C2a).
 */
class ExplanationCompilationTest {

    private static final Path TIMELINE = Path.of("src/main/resources/rulepacks/timeline");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final Timeline SHIPPED = TimelineReader.read(TIMELINE);

    private static Timeline withExplanations(String content) {
        Timeline t = SHIPPED;
        return new Timeline(t.rules(), t.ruleSets(), t.regimes(), t.ruleSources(), t.regimeSources(),
                t.disallowanceSetSources(), t.conditionSetSources(), t.components(), t.componentSources(), t.funds(),
                t.fundSources(), t.declarations(), t.declarationSources(), t.itrFormSources(), t.itrCells(),
                t.itrCellSources(), t.pipelineNodes(), t.coverage(), Optional.of(content), t.sections(), t.acts());
    }

    private static JsonNode explanationOf(CompiledPack pack, String rule) {
        for (JsonNode entry : MAPPER.readTree(pack.files().get("content/explanations.json")).path("explanations")) {
            if (entry.path("rule").asString().equals(rule)) {
                return entry;
            }
        }
        throw new AssertionError(rule + " is not explained in " + pack.taxYear().label());
    }

    @Test
    void compile_whenALimitIsOneYearsAlone_keepsItsSentenceInThatYearOnly() {
        // Given — RET-LEAVE-ENCASHMENT-OTHER's TY 2025-26 version alone names the leave at credit valued at the
        // ten-month average salary

        // When
        JsonNode earlier = explanationOf(RulePackCompiler.compile(SHIPPED, TaxYearId.of(2025)), "RET-LEAVE-ENCASHMENT-OTHER");
        JsonNode later = explanationOf(RulePackCompiler.compile(SHIPPED, TaxYearId.of(2026)), "RET-LEAVE-ENCASHMENT-OTHER");

        // Then
        assertThat(earlier.path("byLimit").has("leave at credit valued at the ten-month average salary")).isTrue();
        assertThat(later.path("byLimit").has("leave at credit valued at the ten-month average salary")).isFalse();
        assertThat(later.path("byLimit").has("leave salary at credit")).isTrue();
    }

    @Test
    void compile_whenAnExplanationNamesNoRuleOfTheTimeline_isRefused() {
        // Given
        Timeline misspelt = withExplanations("""
                {"otherLimits": "Not these: {limits}.", "explanations": [
                  {"rule": "SAL-HOUSE-RENT-ALLOWANCES", "what": "{out} is not taxed."}]}""");

        // When / Then
        assertThatThrownBy(() -> RulePackCompiler.compile(misspelt, TaxYearId.of(2026)))
                .isInstanceOf(PackValidationException.class).hasMessageContaining("SAL-HOUSE-RENT-ALLOWANCES")
                .hasMessageContaining("in no year");
    }

    @Test
    void compile_whenAnExplanationNamesALimitNoVersionHas_isRefused() {
        // Given
        Timeline misspelt = withExplanations("""
                {"otherLimits": "Not these: {limits}.", "explanations": [
                  {"rule": "SAL-STANDARD-DEDUCTION-NEW-TAX-REGIME", "what": "{out} is deducted.",
                   "byLimit": {"statutory amount": "The law's amount.", "salary": "Your salary.", "wages": "Wages."}}]}""");

        // When / Then
        assertThatThrownBy(() -> RulePackCompiler.compile(misspelt, TaxYearId.of(2026)))
                .isInstanceOf(PackValidationException.class).hasMessageContaining("\"wages\"")
                .hasMessageContaining("in no year");
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void shippedPack_whenARulesCapIsALeastOf_explainsIt(int year) {
        // Given
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = ShippedRulePacks.compile("rulepacks", Set.of(taxYear)).load(taxYear);

        // When
        Set<RuleId> leastOf = new TreeSet<>(Comparator.comparing(RuleId::value));
        for (RuleRow row : pack.rules().values()) {
            for (RuleVersion version : row.versions()) {
                version.cap().filter(LeastOf.class::isInstance).ifPresent(cap -> leastOf.add(row.id()));
            }
        }

        // Then
        assertThat(leastOf).isNotEmpty();
        assertThat(pack.explanations().byRule().keySet()).containsAll(leastOf);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void shippedPack_whenCompiled_explainsOrListsEveryRule(int year) {
        // Given
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = ShippedRulePacks.compile("rulepacks", Set.of(taxYear)).load(taxYear);
        Set<RuleId> listed = ExplanationJson.notALine(MAPPER.readTree(SHIPPED.explanations().orElseThrow()),
                "content/explanations.json").keySet();

        // When
        Set<String> neither = new TreeSet<>();
        for (RuleId rule : pack.rules().keySet()) {
            if (!pack.explanations().byRule().containsKey(rule) && !listed.contains(rule)) {
                neither.add(rule.value());
            }
        }

        // Then
        assertThat(neither).isEmpty();
    }

    @Test
    void compile_whenNotALineListsARuleTheTimelineDoesNotHold_isRefused() {
        // Given
        Timeline misspelt = withExplanations("""
                {"explanations": [], "notALine": [{"rule": "CG-PART-SHORT-LISTING", "reason": "A condition only."}]}""");

        // When / Then
        assertThatThrownBy(() -> RulePackCompiler.compile(misspelt, TaxYearId.of(2026)))
                .isInstanceOf(PackValidationException.class).hasMessageContaining("CG-PART-SHORT-LISTING")
                .hasMessageContaining("in no year");
    }

    @Test
    void compile_whenARuleIsBothExplainedAndListed_isRefused() {
        // Given
        Timeline both = withExplanations("""
                {"explanations": [{"rule": "CG-DEPOSIT", "what": "This gain of {in} is left out."}],
                 "notALine": [{"rule": "CG-DEPOSIT", "reason": "Refuses the sale."}]}""");

        // When / Then
        assertThatThrownBy(() -> RulePackCompiler.compile(both, TaxYearId.of(2026)))
                .isInstanceOf(PackValidationException.class).hasMessageContaining("both explains CG-DEPOSIT");
    }

    @Test
    void compile_whenNotALineListsARuleTwice_isRefused() {
        // Given
        Timeline twice = withExplanations("""
                {"explanations": [], "notALine": [{"rule": "CG-PARTS", "reason": "A value."},
                  {"rule": "CG-PARTS", "reason": "Another value."}]}""");

        // When / Then
        assertThatThrownBy(() -> RulePackCompiler.compile(twice, TaxYearId.of(2026)))
                .isInstanceOf(PackValidationException.class).hasMessageContaining("lists CG-PARTS in notALine twice");
    }

    @Test
    void compile_whenTheContentWordsACodeTheEngineDoesNotPublish_isRefused() {
        // Given
        Timeline unknown = withExplanations("""
                {"explanations": [], "reasons": [{"code": "REGIME_FORGOTTEN", "why": "It is not allowed."}]}""");

        // When / Then
        assertThatThrownBy(() -> RulePackCompiler.compile(unknown, TaxYearId.of(2026)))
                .isInstanceOf(PackValidationException.class).hasMessageContaining("REGIME_FORGOTTEN");
    }

    /**
     * Every reason code the engine publishes worded, but {@code leftOut}: a fixture reaches the check it means however
     * many codes later items add (W-327b1).
     */
    private static String reasonsExcept(String leftOut) {
        return ReasonCodes.FACTS.keySet().stream().map(ReasonCode::value).filter(code -> !code.equals(leftOut)).sorted()
                .map(code -> "{\"code\": \"" + code + "\", \"why\": \"Words.\"}").collect(Collectors.joining(", "));
    }

    @Test
    void compile_whenAReasonTheEnginePublishesIsNotWorded_isRefused() {
        // Given — REGIME_EXCLUDES_ROW left out
        Timeline unworded = withExplanations("{\"explanations\": [], \"reasons\": [" + reasonsExcept("REGIME_EXCLUDES_ROW")
                + "], \"terms\": {\"regime\": {\"NEW_TAX_REGIME\": \"the default regime\","
                + " \"FIRST_SCHEDULE_RATES\": \"the old regime\"}}}");

        // When / Then
        assertThatThrownBy(() -> RulePackCompiler.compile(unworded, TaxYearId.of(2026)))
                .isInstanceOf(PackValidationException.class).hasMessageContaining("words no reason REGIME_EXCLUDES_ROW");
    }

    @Test
    void compile_whenARegimeOfTheTimelineHasNoTerm_isRefused() {
        // Given — FIRST_SCHEDULE_RATES left out
        Timeline unnamed = withExplanations("{\"explanations\": [], \"reasons\": [" + reasonsExcept("")
                + "], \"terms\": {\"regime\": {\"NEW_TAX_REGIME\": \"the default regime\"}}}");

        // When / Then
        assertThatThrownBy(() -> RulePackCompiler.compile(unnamed, TaxYearId.of(2026)))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("no term for the regime FIRST_SCHEDULE_RATES");
    }

    private static final String REGIME_TERMS =
            "\"regime\": {\"NEW_TAX_REGIME\": \"the default regime\", \"FIRST_SCHEDULE_RATES\": \"the old regime\"}";

    private static final String HEAD_TERMS = "\"head\": {\"SALARIES\": \"salary\", \"HOUSE_PROPERTY\": \"house property\","
            + " \"BUSINESS_OR_PROFESSION\": \"business or profession\", \"CAPITAL_GAINS\": \"capital gains\","
            + " \"OTHER_SOURCES\": \"other sources\"}";

    @Test
    void compile_whenAHeadHasNoWord_isRefused() {
        // Given — every reason and regime worded, SALARIES left out of the heads
        Timeline unnamed = withExplanations("{\"explanations\": [], \"reasons\": [" + reasonsExcept("") + "], \"terms\": {"
                + REGIME_TERMS + ", " + HEAD_TERMS.replace("\"SALARIES\": \"salary\", ", "") + "}}");

        // When / Then
        assertThatThrownBy(() -> RulePackCompiler.compile(unnamed, TaxYearId.of(2026)))
                .isInstanceOf(PackValidationException.class).hasMessageContaining("no term for the head SALARIES");
    }

    @Test
    void compile_whenADeclaredCapitalGainsPartHasNoWord_isRefused() {
        // Given — CG-PARTS declares four parts; LONG_TERM_OTHER left out
        Timeline unnamed = withExplanations("{\"explanations\": [], \"reasons\": [" + reasonsExcept("") + "], \"terms\": {"
                + REGIME_TERMS + ", " + HEAD_TERMS + ", \"capitalGainsPart\": {\"SHORT_TERM_SLAB\": \"a\","
                + " \"SHORT_TERM_LISTED_EQUITY\": \"b\", \"LONG_TERM_LISTED_EQUITY\": \"c\"}}}");

        // When / Then
        assertThatThrownBy(() -> RulePackCompiler.compile(unnamed, TaxYearId.of(2026)))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("no term for the capital-gains part LONG_TERM_OTHER");
    }

    private static final String PART_TERMS = "\"capitalGainsPart\": {\"SHORT_TERM_SLAB\": \"a\", \"SHORT_TERM_LISTED_EQUITY\":"
            + " \"b\", \"LONG_TERM_OTHER\": \"c\", \"LONG_TERM_LISTED_EQUITY\": \"d\"}";

    @Test
    void compile_whenAnSttStateARowNamesHasNoWord_isRefused() {
        // Given — the RS-STT-CHARGEABLE rows name CHARGEABLE; the content words no state
        Timeline unnamed = withExplanations("{\"explanations\": [], \"reasons\": [" + reasonsExcept("") + "], \"terms\": {"
                + REGIME_TERMS + ", " + HEAD_TERMS + ", " + PART_TERMS + ", \"sttState\": {}}}");

        // When / Then
        assertThatThrownBy(() -> RulePackCompiler.compile(unnamed, TaxYearId.of(2026)))
                .isInstanceOf(PackValidationException.class).hasMessageContaining("no term for the STT state CHARGEABLE");
    }

    @Test
    void compile_whenAnSttStateIsWordedThatNoRowNames_isRefused() {
        // Given — CHARGEABLE worded, and PAID, which no row of RS-STT-CHARGEABLE names
        Timeline extra = withExplanations("{\"explanations\": [], \"reasons\": [" + reasonsExcept("") + "], \"terms\": {"
                + REGIME_TERMS + ", " + HEAD_TERMS + ", " + PART_TERMS
                + ", \"sttState\": {\"CHARGEABLE\": \"chargeable\", \"PAID\": \"paid\"}}}");

        // When / Then
        assertThatThrownBy(() -> RulePackCompiler.compile(extra, TaxYearId.of(2026)))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("a term for PAID, an STT state no row of RS-STT-CHARGEABLE names");
    }

    @Test
    void compile_whenTheShippedContentIsCompiled_carriesEveryReasonAndTheYearsRegimeTerms() {
        // Given / When
        JsonNode content = MAPPER.readTree(RulePackCompiler.compile(SHIPPED, TaxYearId.of(2026)).files()
                .get("content/explanations.json"));

        // Then
        Set<String> codes = new TreeSet<>();
        content.path("reasons").forEach(reason -> codes.add(reason.path("code").asString()));
        assertThat(codes).containsExactly("ACCOMMODATION_CONTINUED_CAP", "ACCOMMODATION_LESS_RENT",
                "ADVANCE_TAX_LIABLE", "ADVANCE_TAX_NOT_LIABLE_BELOW", "ADVANCE_TAX_NOT_LIABLE_BY_AGE",
                "ADVANCE_TAX_STILL_OWED", "ALLOWANCE_DISABILITY_NOT_LISTED", "ALLOWANCE_MONTHLY_LIMIT", "ALL_DEDUCTED",
                "ASSET_TRANSFER_WRITTEN_DOWN", "ASSET_USE_COST_SHARE", "ASSET_USE_RENT", "BUSINESS_TRUST_SPECIFIED_SUM",
                "CAP_ACTUAL_AMOUNTS", "CAP_AGGREGATE_CEILING", "CAP_APPORTIONED", "CAP_FIXED", "CAP_IN_EXCESS",
                "CAP_SHARE", "CARRIED_LOSS_AGAINST_HEAD", "CARRIED_LOSS_AGAINST_PART", "CARRIED_LOSS_EXPIRED",
                "CARRIED_LOSS_NOT_IN_TIME", "CARRIED_LOSS_STILL_CARRIED", "CAR_PER_MONTH",
                "CAR_PER_MONTH_WITH_CHAUFFEUR", "CAR_RUNNING_AND_WEAR", "CG_CHARGE_COST_OF_ACQUISITION",
                "CG_CHARGE_NOT_DEDUCTED", "CG_CHARGE_TRANSFER_EXPENDITURE", "CG_COST_INDEXED", "CG_HELD_LONG_TERM",
                "CG_HELD_SHORT_TERM", "CG_INDEXED_MEASURE_NIL", "CG_LEFT_TO_ANOTHER_HEAD",
                "CG_LONG_TERM_LOSS_CARRIED_FORWARD", "CG_LONG_TERM_LOSS_SET_OFF_AGAINST_PART", "CG_PART_GAIN",
                "CG_PART_LOSS", "CG_REINVESTMENT_NO_GAIN", "CG_REINVESTMENT_OUTSIDE_WINDOW", "CG_REINVESTMENT_SHARE",
                "CG_REINVESTMENT_WHOLE", "CG_SHORTFALL_TAKEN_FROM_PART", "CG_SHORT_TERM_LOSS_CARRIED_FORWARD",
                "CG_SHORT_TERM_LOSS_SET_OFF_AGAINST_PART", "CG_STT_ON_BUY", "CG_STT_ON_SALE",
                "CG_TAXED_AT_SPECIAL_RATE", "COMMUTED_PENSION_LIMIT", "COMMUTED_PENSION_LIMIT_WITH_GRATUITY",
                "CONDITION_NOT_MET", "CREDIT_ADVANCE_TAX", "CREDIT_SELF_ASSESSMENT_TAX", "CREDIT_TAX_AT_SOURCE",
                "DEDUCTED_IN_FULL", "DEDUCTIONS_WITHIN_INCOME", "DEDUCTION_LIMITED", "DEFERMENT_NOT_YET_REACHED",
                "DEFERMENT_PAID_ENOUGH", "DEFERMENT_PAID_ENOUGH_NOT_RELIEVED", "DEFERMENT_PAID_ENOUGH_RELIEVED",
                "DEFERMENT_SHORT", "DEFERMENT_SHORT_NOT_RELIEVED", "DEFERMENT_SHORT_RELIEVED",
                "DEPENDANT_NOTHING_SPENT", "DEPOSIT_INTEREST_SAVINGS_ONLY", "DISABILITY_CERTIFIED",
                "DIVIDEND_INTEREST_LIMITED", "DONATION_CASH_LIMIT", "DONATION_QUALIFYING_LIMIT", "DONATION_UNREPORTED",
                "DONATION_WITHIN_QUALIFYING_LIMIT", "DONATION_WITHOUT_LIMIT", "EDUCATION_ABOVE_LIMIT",
                "EDUCATION_LOAN_OUTSIDE_YEARS", "EDUCATION_WITHIN_LIMIT", "ELECTION_AS_STATED", "ELECTION_CHOSEN",
                "ELECTION_NOT_CHOSEN", "EMPLOYER_ACCRETION_SHARE", "EXCLUDED_BY_RULE", "FEE_RETURN_FURNISHED_IN_TIME",
                "FEE_RETURN_FURNISHED_LATE", "FURNITURE_COST_SHARE", "FURNITURE_HIRE", "GIFTS_BELOW_LIMIT",
                "GIFTS_FOR_THE_YEAR", "GIFTS_REACH_LIMIT", "GIFT_COVID_DEATH_LIMIT", "GIFT_EXCLUDED", "GIFT_ON_ITS_OWN",
                "GROSS_TOTAL_LESS_SPECIAL_GAINS", "HEALTH_CHECKUP_SPLIT", "HEALTH_PAID_IN_CASH", "HP_ANNUAL_VALUE_LET",
                "HP_ANNUAL_VALUE_NOT_LET", "HP_ARREARS_LESS", "HP_FOR_THIS_PROPERTY", "HP_LESS_UNREALISED_RENT",
                "HP_OWN_RESIDENCE_INTEREST_AGGREGATE", "HP_OWN_RESIDENCE_NIL", "HRA_NOT_RENTED", "INTEREST_FOR_MONTHS",
                "INTEREST_NOT_LIABLE_TO_ADVANCE_TAX", "INTEREST_NOT_YET_DUE", "LESS_RECOVERED",
                "LIFE_POLICY_ABOVE_PREMIUMS", "LIFE_POLICY_EXCLUDED", "LOANS_WITHIN_PETTY_LIMIT",
                "LOAN_FOR_LISTED_DISEASE", "LOAN_INTEREST_AT_BANK_RATE", "LOAN_SANCTION_CONDITION_NOT_MET",
                "LOSS_CARRIED_FORWARD_HOUSE_PROPERTY", "LOSS_DEEMED_GIVEN_EFFECT", "LOSS_NOT_CARRIED_FORWARD",
                "LOSS_SET_OFF_AGAINST_HEAD", "LOSS_SET_OFF_AGAINST_PART", "LTC_ASSISTANCE_RECEIVED",
                "LTC_CHILD_BEYOND_LIMIT", "LTC_JOURNEYS_IN_BLOCK", "MEALS_IN_FULL", "MEDICAL_ABROAD_EXCLUDED",
                "MEDICAL_TRAVEL_CONDITION_NOT_MET", "NOT_RESIDENT", "NOT_SPECIFIED_EMPLOYEE", "OS_EXPENSE_NOT_DEDUCTED",
                "OS_FOR_THIS_ITEM", "PF_CONTRIBUTION_ABOVE_THRESHOLD", "PF_TAXABLE_ACCOUNT_INTEREST",
                "PREMIUM_SHARE_OF_SUM_ASSURED", "REBATE_LIMITED_TO_SLAB_TAX", "REFUND_BELOW_SHARE",
                "REFUND_INTEREST_ON_PREPAID_TAX", "REFUND_INTEREST_ON_RETURN_PAYMENT", "REGIME_DISALLOWS",
                "REGIME_EXCLUDES_ROW", "RELIEF_ABOVE_NO_THRESHOLD", "RELIEF_CONDITION_NOT_MET",
                "RELIEF_INCOME_ABOVE_CEILING", "RELIEF_INCOME_NOT_ABOVE_THRESHOLD", "RELIEF_LESSER_OF_TAX_AND_MAXIMUM",
                "RELIEF_MARGINAL_AT_THRESHOLD", "RELIEF_NONE", "RELIEF_TAX_ABOVE_EXCESS", "RENT_OWNS_HOME_ELSEWHERE",
                "RENT_OWNS_HOME_HERE", "RENT_RECEIVES_HRA", "RETIREMENT_ALLOWED_BEFORE", "RETIREMENT_BARRED",
                "RETIREMENT_CEILING_USED", "RETIREMENT_CEILING_USED_THIS_YEAR", "SALARY_INCLUDES_AGNIVEER",
                "SALARY_INCLUDES_EMPLOYER_EXCESS", "SALARY_INCLUDES_PENSION_CONTRIBUTION",
                "SALARY_INCLUDES_PERQUISITES", "SECURITY_VALUE", "SHORTFALL_ADVANCE_TAX_PAID_ENOUGH",
                "SURCHARGE_LIMITED_PART", "SURCHARGE_ON_INCOME_EXCLUDING", "SURCHARGE_ON_TOTAL_INCOME",
                "TAX_AT_SLAB_RATES_ON_REST", "UNTAXED_ABROAD", "WITHIN_LIMIT");
        assertThat(content.at("/terms/regime/NEW_TAX_REGIME").asString()).isEqualTo("the default regime");
    }

    /** The capital-gains lines, and the rules on lines no test reaches, as W-325's appendix classified them (W-325d). */
    private static final List<String> CAPITAL_GAINS_AND_UNREACHED = List.of(
            "CG-CHARGE-COST",
            "CG-CHARGE-MISC",
            "CG-CHARGE-STT",
            "CG-CHARGE-TRANSFER",
            "CG-DEPOSIT",
            "CG-EXEMPT-RESIDENTIAL-HOUSE",
            "CG-INDEXED-COST-LAND-BUILDING",
            "CG-TERM-LAND-BUILDING",
            "CG-TERM-LISTED-SECURITY",
            "PERQ-CAR-OWNRUN-ELECTRIC",
            "RET-COMMUTED-PENSION-FUND",
            "RET-COMMUTED-PENSION-GOVERNMENT",
            "RET-RETRENCHMENT-APPROVED-SCHEME",
            "SETOFF-WITHIN-CAPITAL-GAINS",
            "STT-PURCHASE-DELIVERY",
            "STT-SALE-DELIVERY",
            "STT-SALE-NONDELIVERY");

    @Test
    void timeline_whenCapitalGainsAreCompiled_explainsEveryRuleOfThemInSomeYear() {
        // Given / When / Then
        assertThat(explainedInSomeYear()).containsAll(CAPITAL_GAINS_AND_UNREACHED);
    }

    /** The salary-family rules W-325's probe saw on a trace line (W-325a, D-673): each explained in the year it is a rule of. */
    private static final List<String> SALARY_FAMILY = List.of(
            "PERQ-ACCOM-FURNITURE",
            "PERQ-ACCOM-GOVERNMENT",
            "PERQ-ACCOM-OWNED",
            "PERQ-ASSET-TRANSFER-CAR",
            "PERQ-ASSET-TRANSFER-ELECTRONIC",
            "PERQ-ASSET-TRANSFER-OTHER",
            "PERQ-ASSET-USE",
            "PERQ-ASSET-USE-EXCLUDED",
            "PERQ-CAR-OWNRUN-LARGE",
            "PERQ-CAR-OWNRUN-SMALL",
            "PERQ-CAR-PARTLY-ELECTRIC",
            "PERQ-CAR-PARTLY-LARGE",
            "PERQ-CAR-PARTLY-SMALL",
            "PERQ-CAR-PATH-OFFICIAL",
            "PERQ-CAR-PRIVATE",
            "PERQ-CARRIER-TRAVEL",
            "PERQ-CLUB",
            "PERQ-CLUB-BUSINESS",
            "PERQ-CLUB-BUSINESS-UNIFORM",
            "PERQ-CLUB-UNIFORM",
            "PERQ-CREDIT-CARD",
            "PERQ-CREDIT-CARD-OFFICIAL",
            "PERQ-EDUCATION",
            "PERQ-EDUCATION-FREE",
            "PERQ-EMPLOYER-ACCRETION",
            "PERQ-EMPLOYER-CONTRIBUTION",
            "PERQ-GIFT",
            "PERQ-HOLIDAY",
            "PERQ-LOAN",
            "PERQ-LOAN-PETTY",
            "PERQ-MEAL",
            "PERQ-MEAL-AT-WORK",
            "PERQ-MEAL-REMOTE",
            "PERQ-MEAL-TEA-SNACKS",
            "PERQ-MEAL-VOUCHER",
            "PERQ-MEDABROAD-TRAVEL",
            "PERQ-OTHER-BENEFIT",
            "PERQ-RPF-CONTRIBUTION",
            "PERQ-RPF-INTEREST",
            "PERQ-SERVICES",
            "PERQ-SPECIFIED-EMPLOYEE",
            "PERQ-SPECIFIED-SECURITY",
            "PERQ-TELEPHONE",
            "PERQ-UTILITY",
            "RET-COMMUTED-PENSION-OTHER",
            "RET-GRATUITY-AGGREGATE",
            "RET-GRATUITY-GOVERNMENT",
            "RET-LEAVE-ENCASHMENT-AGGREGATE",
            "RET-LEAVE-ENCASHMENT-GOVERNMENT",
            "SAL-ALW-TRANSPORT-DISABLED",
            "SAL-LEAVE-TRAVEL-CHILDREN",
            "SAL-SALARY-FOR-ACCOM",
            "SAL-SALARY-FOR-EMPLOYEE-PENSION",
            "SAL-SALARY-FOR-EMPLOYER-PENSION",
            "SAL-SALARY-FOR-ENTMT",
            "SAL-SALARY-FOR-GRATUITY",
            "SAL-SALARY-FOR-HRA",
            "SAL-SALARY-FOR-RPF",
            "SAL-SALARY-INCLUDES",
            "SAL-TAX-ON-EMPLOYMENT");

    /** Chapter VIII, other sources and house property, as W-325's probe saw them on a trace line (W-325b, D-674). */
    private static final List<String> DEDUCTIONS_AND_OTHER_HEADS = List.of(
            "DED-AGNIVEER-GOVERNMENT",
            "DED-AGNIVEER-OWN",
            "DED-DISABILITY",
            "DED-DISABILITY-SEVERE",
            "DED-DONATION-CASH",
            "DED-DONATION-HALF",
            "DED-DONATION-QUALIFYING",
            "DED-DONATION-WHOLE",
            "DED-EDUCATION-LOAN",
            "DED-ELECTRIC-VEHICLE-LOAN",
            "DED-HEALTH-CASH-PERMITTED",
            "DED-HEALTH-CHECKUP",
            "DED-HEALTH-FAMILY",
            "DED-HEALTH-FAMILY-SENIOR",
            "DED-HEALTH-PARENTS",
            "DED-HEALTH-PARENTS-SENIOR",
            "DED-HOUSE-LOAN",
            "DED-HOUSE-LOAN-EARLIER",
            "DED-NPS-EMPLOYER-GOVT",
            "DED-NPS-EMPLOYER-NEW-TAX-REGIME",
            "DED-NPS-EMPLOYER-OTHER",
            "DED-NPS-OWN",
            "DED-PREMIUM-SHARE",
            "DED-SCHEDULE-XV",
            "DED-WITHIN-GROSS-TOTAL",
            "HP-ANNUAL-VALUE",
            "HP-ARREARS",
            "HP-INTEREST",
            "HP-OWN-RESIDENCE",
            "HP-OWN-RESIDENCE-INTEREST",
            "HP-OWN-RESIDENCE-INTEREST-CAP",
            "HP-OWN-RESIDENCE-PRE-CONSTRUCTION",
            "HP-PRE-CONSTRUCTION",
            "HP-STANDARD-DEDUCTION",
            "HP-UNREALISED-RENT",
            "OS-BANKER-COMMISSION",
            "OS-BUSINESS-TRUST",
            "OS-COMMUTED-PENSION",
            "OS-COMPENSATION-INTEREST",
            "OS-COMPENSATION-INTEREST-INCOME",
            "OS-DEATH-GRATUITY",
            "OS-DIVIDEND",
            "OS-DIVIDEND-INTEREST",
            "OS-FAMILY-PENSION",
            "OS-FAMILY-PENSION-NEW-TAX-REGIME",
            "OS-FORFEITED-ADVANCE",
            "OS-GIFT",
            "OS-GIFT-COVID-DEATH",
            "OS-GIFT-EXCLUSION",
            "OS-GIFT-IMMOVABLE",
            "OS-GIFT-PROPERTY-BOUGHT",
            "OS-GIFT-PROPERTY-FREE",
            "OS-INTEREST",
            "OS-KEYMAN-POLICY",
            "OS-LIFE-POLICY",
            "OS-LIFE-POLICY-RATIO",
            "OS-OTHER-EXPENDITURE",
            "OS-PF-TAXABLE-INTEREST",
            "OS-RESIDUAL",
            "OS-TERMINATION-COMPENSATION");

    @Test
    void timeline_whenDeductionsAndTheOtherHeadsAreCompiled_explainsEveryRuleOfThemInSomeYear() {
        // Given / When / Then
        assertThat(explainedInSomeYear()).containsAll(DEDUCTIONS_AND_OTHER_HEADS);
    }

    /** The bill: tax by rate, surcharge, rebate, cess, rounding, interest and fees, advance tax, credits, set-off (W-325c, D-675). */
    private static final List<String> THE_BILL = List.of(
            "ADV-INSTALMENTS",
            "ADV-LIABILITY",
            "CREDIT-TAX-PAID",
            "FEE-LATE-RETURN",
            "INT-ADVANCE-SHORTFALL",
            "INT-DEFERMENT",
            "INT-LATE-RETURN",
            "INT-REFUND",
            "RATE-CG-LONG-LISTED-EQUITY",
            "RATE-CG-SHORT-LISTED-EQUITY",
            "RATE-CG-SHORTFALL-ORDER",
            "RATE-HEALTH-EDUCATION-CESS",
            "RATE-ROUND-PAYABLE",
            "RATE-ROUND-TOTAL-INCOME",
            "RATE-SLAB-FIRST-SCHEDULE",
            "RATE-SLAB-FIRST-SCHEDULE-AGE-60TO79",
            "RATE-SLAB-FIRST-SCHEDULE-AGE-80",
            "RATE-SLAB-NEW-TAX-REGIME",
            "RATE-SUR-FIRST-SCHEDULE",
            "RATE-SUR-FIRST-SCHEDULE-CEILING",
            "RATE-SUR-FIRST-SCHEDULE-EXCLUDING",
            "RATE-SUR-FIRST-SCHEDULE-LIMIT",
            "RATE-SUR-NEW-TAX-REGIME",
            "RATE-SUR-NEW-TAX-REGIME-CEILING",
            "RATE-SUR-NEW-TAX-REGIME-EXCLUDING",
            "RATE-SUR-NEW-TAX-REGIME-LIMIT",
            "REBATE-NEW-TAX-REGIME",
            "REBATE-NEW-TAX-REGIME-EXCESS",
            "REBATE-RESIDENT-INDIVIDUAL",
            "SETOFF-ACROSS-HEADS",
            "SETOFF-CARRIED-CAPITAL-GAINS",
            "SETOFF-CARRIED-DISALLOWED-PART",
            "SETOFF-CARRIED-HOUSE-PROPERTY");

    @Test
    void timeline_whenTheBillIsCompiled_explainsEveryRuleOfItInSomeYear() {
        // Given / When / Then
        assertThat(explainedInSomeYear()).containsAll(THE_BILL);
    }

    private static Set<String> explainedInSomeYear() {
        Set<String> explained = new TreeSet<>();
        for (int year : new int[] {2025, 2026}) {
            for (JsonNode entry : MAPPER.readTree(RulePackCompiler.compile(SHIPPED, TaxYearId.of(year)).files()
                    .get("content/explanations.json")).path("explanations")) {
                explained.add(entry.path("rule").asString());
            }
        }
        return explained;
    }

    @Test
    void timeline_whenTheSalaryFamilyIsCompiled_explainsEveryRuleOfItInSomeYear() {
        // Given
        Set<String> explained = new TreeSet<>();
        for (int year : new int[] {2025, 2026}) {
            for (JsonNode entry : MAPPER.readTree(RulePackCompiler.compile(SHIPPED, TaxYearId.of(year)).files()
                    .get("content/explanations.json")).path("explanations")) {
                explained.add(entry.path("rule").asString());
            }
        }

        // When / Then
        assertThat(explained).containsAll(SALARY_FAMILY);
    }
}
