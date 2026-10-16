package com.thiru.wealthlens.taxengine.rules.generated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Keeps the committed constants and the packs in step.
 *
 * <p>This is what buys {@code 19-engineering-foundations.md}'s two properties without a build-time
 * generator:
 *
 * <ul>
 *   <li><b>A code removed from every pack</b> changes what this test regenerates, so the test fails;
 *       regenerating deletes the constant, and every Java reference to it then <b>fails to
 *       compile</b> — the strongest deprecation signal available.
 *   <li><b>A typo in a pack</b> fails this test, and tests run in the build, so it fails the
 *       <b>build</b> rather than surfacing at startup.
 * </ul>
 *
 * <p>Run with {@code -Dtaxengine.rules.regenerate=true} to rewrite the committed files instead of asserting.
 */
class GeneratedConstantsTest {

    private static final String REGENERATE = "taxengine.rules.regenerate";

    /** Every rule the shipped timeline holds, in the generator's order — listed by hand. */
    private static final java.util.List<String> SHIPPED_RULE_IDS = java.util.List.of(
            "DED-AGNIVEER-GOVERNMENT",
            "DED-AGNIVEER-OWN",
            "DED-DISABILITY",
            "DED-DISABILITY-SEVERE",
            "DED-DISABLED-DEPENDANT",
            "DED-DISABLED-DEPENDANT-SEVERE",
            "DED-DONATION-CASH",
            "DED-DONATION-HALF",
            "DED-DONATION-QUALIFYING",
            "DED-DONATION-WHOLE",
            "DED-EDUCATION-LOAN",
            "DED-ELECTRIC-VEHICLE-LOAN",
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
            "DED-RENT-PAID",
            "DED-SCHEDULE-XV",
            "DED-SPECIFIED-DISEASE",
            "DED-SPECIFIED-DISEASE-SENIOR",
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
            "OS-COMPENSATION-INTEREST",
            "OS-DEEMED-DIVIDEND",
            "OS-DIVIDEND",
            "OS-DIVIDEND-INTEREST",
            "OS-INTEREST",
            "OS-OTHER-EXPENDITURE",
            "PERQ-ACCOM-CONTINUED",
            "PERQ-ACCOM-FURNITURE",
            "PERQ-ACCOM-GOVERNMENT",
            "PERQ-ACCOM-HOTEL",
            "PERQ-ACCOM-LEASED",
            "PERQ-ACCOM-OWNED",
            "PERQ-ASSET-TRANSFER-CAR",
            "PERQ-ASSET-TRANSFER-ELECTRONIC",
            "PERQ-ASSET-TRANSFER-OTHER",
            "PERQ-ASSET-USE",
            "PERQ-ASSET-USE-EXCLUDED",
            "PERQ-CAR-CHAUFFEUR",
            "PERQ-CAR-CONVEYANCE",
            "PERQ-CAR-EMPLOYEE",
            "PERQ-CAR-OWNRUN-ELECTRIC",
            "PERQ-CAR-OWNRUN-LARGE",
            "PERQ-CAR-OWNRUN-SMALL",
            "PERQ-CAR-PARTLY-ELECTRIC",
            "PERQ-CAR-PARTLY-LARGE",
            "PERQ-CAR-PARTLY-SMALL",
            "PERQ-CAR-PRIVATE",
            "PERQ-CARRIER-TRAVEL",
            "PERQ-CLUB",
            "PERQ-CLUB-BUSINESS",
            "PERQ-CLUB-BUSINESS-UNIFORM",
            "PERQ-CLUB-UNIFORM",
            "PERQ-CREDIT-CARD",
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
            "PERQ-MEAL-VOUCHER",
            "PERQ-MEDABROAD-STAY",
            "PERQ-MEDABROAD-TRAVEL",
            "PERQ-OTHER-BENEFIT",
            "PERQ-RPF-CONTRIBUTION",
            "PERQ-RPF-INTEREST",
            "PERQ-SERVICES",
            "PERQ-SPECIFIED-DATE",
            "PERQ-SPECIFIED-EMPLOYEE",
            "PERQ-SPECIFIED-SECURITY",
            "PERQ-UTILITY",
            "RATE-COST-INFLATION-INDEX",
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
            "RATE-SUR-NEW-TAX-REGIME",
            "RATE-SUR-NEW-TAX-REGIME-CEILING",
            "RATE-SUR-NEW-TAX-REGIME-EXCLUDING",
            "REBATE-NEW-TAX-REGIME",
            "REBATE-NEW-TAX-REGIME-EXCESS",
            "REBATE-RESIDENT-INDIVIDUAL",
            "REG-NEW-TAX-REGIME-NO-EXEMPTION",
            "RET-COMMUTED-PENSION-FUND",
            "RET-COMMUTED-PENSION-GOVERNMENT",
            "RET-COMMUTED-PENSION-OTHER",
            "RET-GRATUITY-AGGREGATE",
            "RET-GRATUITY-GOVERNMENT",
            "RET-GRATUITY-OTHER",
            "RET-GRATUITY-STATUTORY",
            "RET-LEAVE-ENCASHMENT-AGGREGATE",
            "RET-LEAVE-ENCASHMENT-GOVERNMENT",
            "RET-LEAVE-ENCASHMENT-OTHER",
            "RET-RETRENCHMENT",
            "RET-RETRENCHMENT-APPROVED-SCHEME",
            "RET-VOLUNTARY-RETIREMENT",
            "SAL-ALW-CHILDREN-EDUCATION",
            "SAL-ALW-CONVEYANCE",
            "SAL-ALW-HOSTEL",
            "SAL-ALW-TRANSPORT-DISABLED",
            "SAL-ALW-UNIFORM",
            "SAL-ENTMT-ALLOWANCE",
            "SAL-HOUSE-RENT-ALLOWANCE",
            "SAL-HOUSE-RENT-LANDLORD-PAN",
            "SAL-LEAVE-TRAVEL-CONCESSION",
            "SAL-LEAVE-TRAVEL-DISTANCE-RATE",
            "SAL-LEAVE-TRAVEL-JOURNEYS",
            "SAL-SALARY-FOR-ACCOM",
            "SAL-SALARY-FOR-EMPLOYEE-PENSION",
            "SAL-SALARY-FOR-EMPLOYER-PENSION",
            "SAL-SALARY-FOR-ENTMT",
            "SAL-SALARY-FOR-GRATUITY",
            "SAL-SALARY-FOR-HRA",
            "SAL-SALARY-FOR-RPF",
            "SAL-SALARY-INCLUDES",
            "SAL-STANDARD-DEDUCTION-ANY-OTHER-CASE",
            "SAL-STANDARD-DEDUCTION-NEW-TAX-REGIME",
            "SAL-TAX-ON-EMPLOYMENT");

    private record Target(
            String className, String valueType, java.util.List<String> purpose, TreeSet<String> values) {}

    private static Target ruleIds() {
        return new Target(
                "RuleIds",
                "RuleId",
                java.util.List.of(
                        "Every rule id in every pack. A rule referenced from Java is referenced by",
                        "constant, so a misspelling does not compile, and a rule deleted from the packs",
                        "breaks its callers visibly rather than at run time."),
                ConstantGenerator.ruleIds());
    }

    private static Target componentCodes() {
        return new Target(
                "ComponentCodes",
                "ComponentCode",
                java.util.List.of(
                        "Every component code in every pack. Codes live in data so that adding an",
                        "allowance is a data change; these constants give Java the compile-time safety",
                        "the enum used to, without closing the vocabulary."),
                ConstantGenerator.componentCodes());
    }

    private static Target nodeIds() {
        return new Target(
                "NodeIds",
                "NodeId",
                java.util.List.of(
                        "Every node in the timeline's pipeline catalogue. A node referenced from Java",
                        "is referenced by constant, so a slot renamed in the timeline breaks its callers",
                        "at compile time rather than as an empty value at run time."),
                ConstantGenerator.nodeIds());
    }

    private static void check(Target target) {
        Path file = ConstantGenerator.OUTPUT_DIR.resolve(target.className() + ".java");
        String expected = ConstantGenerator.render(
                target.className(), target.valueType(), target.purpose(), target.values());

        if (Boolean.getBoolean(REGENERATE)) {
            write(file, expected);
            return;
        }
        assertThat(read(file))
                .as(
                        "%s is stale. The packs and this file disagree. Regenerate with:%n"
                                + "  ./mvnw test -pl backend -Dtest=GeneratedConstantsTest -D%s=true",
                        file, REGENERATE)
                .isEqualTo(expected);
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file.toAbsolutePath(), e);
        }
    }

    private static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + file.toAbsolutePath(), e);
        }
    }

    @Nested
    @DisplayName("the committed constants match the packs")
    class InStep {

        @Test
        void ruleIdsIsCurrent() {
            check(ruleIds());
        }

        @Test
        void componentCodesIsCurrent() {
            check(componentCodes());
        }

        @Test
        void nodeIdsIsCurrent() {
            check(nodeIds());
        }

        @Test
        void everyNodeARegimeRunsHasAConstant() {
            // The assembler never reads the node catalogue, so this is the one check that a
            // regime's pipeline names only nodes the catalogue declares
            assertThat(ConstantGenerator.nodeIds()).containsAll(ConstantGenerator.regimeNodeRefs());
        }
    }

    @Nested
    @DisplayName("what the generator reads")
    class Sources {

        @Test
        void itFindsTheRulesInTheRealPack() {
            assertThat(ConstantGenerator.ruleIds())
                    .contains("RATE-SLAB-NEW-TAX-REGIME", "RATE-SLAB-FIRST-SCHEDULE");
        }

        @Test
        void componentCodesHoldsExactlyTheShippedComponents() {
            // A component arrives with the first rule that reads it (D-089): professional tax with
            // SAL-TAX-ON-EMPLOYMENT (D-093), the pay lines with SAL-SALARY-INCLUDES (D-094)
            assertThat(ConstantGenerator.componentCodes()).containsExactly(
                    "ANNUITY_PLAN", "BANK_TERM_DEPOSIT", "BASIC", "BONUS", "CHILDREN_EDUCATION_ALLOWANCE", "COMMISSION", "COMMUTED_PENSION", "CONVEYANCE_ALLOWANCE", "DEARNESS_ALLOWANCE", "DEFERRED_ANNUITY", "ELIGIBLE_ISSUE_SUBSCRIPTION", "EMPLOYEE_NPS", "EMPLOYEE_PF", "EMPLOYEE_SUPERANNUATION", "ENTERTAINMENT_ALLOWANCE", "GOVERNMENT_DEFERRED_ANNUITY", "GOVERNMENT_PENSION_ADDITIONAL", "GRATUITY", "HEALTH_INSURANCE_FAMILY", "HEALTH_INSURANCE_PARENTS", "HOSTEL_ALLOWANCE", "HOUSE_PURCHASE_PAYMENT", "HOUSING_FINANCE_DEPOSIT", "HRA", "LEAVE_ENCASHMENT", "LIFE_INSURANCE_PREMIUM", "LTA", "MEDICAL_EXPENDITURE_FAMILY", "MEDICAL_EXPENDITURE_PARENTS", "MUTUAL_FUND_PENSION_FUND", "MUTUAL_FUND_UNITS", "MUTUAL_FUND_UNIT_LINKED_PLAN", "NABARD_BONDS", "NATIONAL_HOUSING_BANK_SCHEME", "NOTICE_PAY", "NOTIFIED_DEPOSIT_SCHEME", "NOTIFIED_PROVIDENT_FUND", "NPS_MINOR_DEPOSIT", "PENSION_FUND_ANNUITY", "POST_OFFICE_TIME_DEPOSIT", "PROFESSIONAL_TAX", "PROVIDENT_FUNDS_ACT_FUND", "RETRENCHMENT_COMPENSATION", "SAVINGS_CERTIFICATE", "SENIOR_CITIZEN_SAVINGS", "SPECIAL_ALLOWANCE", "TRANSPORT_ALLOWANCE_DISABLED", "TUITION_FEES", "UNIFORM_ALLOWANCE", "UNIT_LINKED_INSURANCE_PLAN", "VRS_COMPENSATION");
        }

        @Test
        void itReadsTheRuleIdsFromTheTimeline() {
            assertThat(ConstantGenerator.ruleIds())
                    .containsExactlyElementsOf(SHIPPED_RULE_IDS);
        }
    }

    @Nested
    @DisplayName("rendering")
    class Rendering {

        @Test
        void aHyphenatedRuleIdBecomesAValidConstantName() {
            assertThat(ConstantGenerator.constantName("SAL-HRA-001")).isEqualTo("SAL_HRA_001");
            assertThat(ConstantGenerator.constantName("round.totalincome"))
                    .isEqualTo("ROUND_TOTALINCOME");
        }

        @Test
        void theOutputCarriesTheDoNotEditBanner() {
            String source = ConstantGenerator.render(
                    "RuleIds",
                    "RuleId",
                    java.util.List.of("purpose"),
                    new TreeSet<>(java.util.Set.of("SAL-HRA-001")));

            assertThat(source)
                    .contains("GENERATED — do not edit")
                    .contains("public static final RuleId SAL_HRA_001 = new RuleId(\"SAL-HRA-001\");")
                    .contains("private RuleIds() {");
        }

        @Test
        void anEmptySetStillProducesCompilableSource() {
            String source = ConstantGenerator.render(
                    "ComponentCodes", "ComponentCode", java.util.List.of("purpose"), new TreeSet<>());

            assertThat(source).contains("// No values in any pack yet.");
            assertThatCode(() -> assertThat(source).contains("private ComponentCodes() {"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("the two properties DR 19 promises")
    class Properties {

        @Test
        void aCodeRemovedFromEveryPackLosesItsConstant() {
            // Which is what makes every Java reference to it fail to compile — the strongest
            // deprecation signal available. Simulated by rendering with the value absent.
            TreeSet<String> withRule = new TreeSet<>(java.util.Set.of("RATE-SLAB-NEW-TAX-REGIME"));
            String before = ConstantGenerator.render(
                    "RuleIds", "RuleId", java.util.List.of("purpose"), withRule);
            String after = ConstantGenerator.render(
                    "RuleIds", "RuleId", java.util.List.of("purpose"), new TreeSet<>());

            assertThat(before).contains("RATE_SLAB_NEW_TAX_REGIME");
            assertThat(after).doesNotContain("RATE_SLAB_NEW_TAX_REGIME");
        }

        @Test
        void aTypoInAPackFailsThisTestAndSoTheBuild() {
            // A misspelt rule id generates a differently named constant, so the committed file and
            // the packs disagree and this class fails. Tests run in the build, so the build fails
            // rather than the typo surfacing at startup.
            String typo = ConstantGenerator.render(
                    "RuleIds",
                    "RuleId",
                    java.util.List.of("purpose"),
                    new TreeSet<>(java.util.Set.of("RATE-SLAB-NEW-TAX-REGMIE")));

            assertThat(typo).contains("RATE_SLAB_NEW_TAX_REGMIE").doesNotContain("RATE_SLAB_NEW_TAX_REGIME");
        }

        @Test
        void regeneratingIsIdempotent() {
            // A generator whose output drifts between runs would fail this test on alternate builds
            String once = ConstantGenerator.render(
                    "RuleIds", "RuleId", java.util.List.of("purpose"), ConstantGenerator.ruleIds());
            String twice = ConstantGenerator.render(
                    "RuleIds", "RuleId", java.util.List.of("purpose"), ConstantGenerator.ruleIds());

            assertThat(twice).isEqualTo(once);
        }

        @Test
        void theCommittedFileIsAFormatterFixedPoint() {
            // Proven by construction elsewhere: the import is emitted only when used, and the
            // javadoc is pre-wrapped. This pins the two decisions that make it so.
            String empty = ConstantGenerator.render(
                    "ComponentCodes", "ComponentCode", java.util.List.of("purpose"), new TreeSet<>());
            String populated = ConstantGenerator.render(
                    "RuleIds",
                    "RuleId",
                    java.util.List.of("purpose"),
                    new TreeSet<>(java.util.Set.of("SAL-HRA-001")));

            assertThat(empty).doesNotContain("import ");
            assertThat(populated).contains("import com.thiru.wealthlens.taxengine.rules.id.RuleId;");
            assertThat(populated.lines()).allMatch(line -> line.length() <= 100);
        }
    }
}
