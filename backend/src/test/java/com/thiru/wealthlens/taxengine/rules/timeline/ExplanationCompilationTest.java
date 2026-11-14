package com.thiru.wealthlens.taxengine.rules.timeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.cap.LeastOf;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code content/explanations.json} compiled into each year's content layer (W-116, D-671): one file serves every year,
 * each year keeping its own rules and the limits its versions name; a rule or limit the timeline names nowhere is refused;
 * and every rule whose cap is a least-of is explained (F37.3), the rest showing their rule id (F37.2) until W-325.
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

        // Then — every least-of row explained; the other rows show their rule id until W-325
        assertThat(leastOf).isNotEmpty();
        assertThat(pack.explanations().byRule().keySet()).containsAll(leastOf);
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
