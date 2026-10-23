package com.thiru.wealthlens.taxengine.rules.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapShape;
import com.thiru.wealthlens.taxengine.rules.cap.Fixed;
import com.thiru.wealthlens.taxengine.rules.cap.PercentOfBase;
import com.thiru.wealthlens.taxengine.rules.cap.SlabLadder;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.regime.DisallowanceSetId;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.temporal.Presence;
import com.thiru.wealthlens.taxengine.rules.temporal.Validity;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parsing the pack's leaf values and the sealed families a rule version is made of.
 *
 * <p>Every failure case asserts on the <b>message</b>, not just the type. The point of hand-written
 * parsing is that a pack author can act on what it says, and a test that only checks the exception
 * class would let the message rot.
 *
 * <p>W-033b and W-033c extend this class.
 */
class PackJsonTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    static JsonNode json(String raw) {
        return MAPPER.readTree(raw);
    }

    static Provenance provenance() {
        return new Provenance(
                java.net.URI.create("https://egazette.gov.in/x.pdf"),
                "somewhere",
                LocalDate.of(2026, 9, 19),
                VerificationLevel.SOURCE_CHECKED,
                null);
    }

    @Nested
    @DisplayName("reading primitives — every failure names the field")
    class Primitives {

        @Test
        void anAbsentRequiredFieldSaysWhichOne() {
            assertThatThrownBy(() -> PackJson.text(json("{}"), "ruleId", "law/rates.json rules[0]"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessage("law/rates.json rules[0].ruleId is required and is absent");
        }

        @Test
        void aWrongTypeSaysWhatWasExpectedAndWhatWasFound() {
            assertThatThrownBy(() -> PackJson.text(json("{\"rate\": 30}"), "rate", "bands[1]"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("bands[1].rate must be a non-empty string");
        }

        @Test
        void aBadDateSaysTheFormat() {
            assertThatThrownBy(() ->
                            PackJson.date(json("{\"from\": \"01-04-2026\"}"), "from", "validity"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("must be a date as yyyy-MM-dd")
                    .hasMessageContaining("01-04-2026");
        }

        @Test
        void aRelativeUrlIsRejected() {
            assertThatThrownBy(() ->
                            PackJson.uri(json("{\"sourceUrl\": \"/local/file.pdf\"}"), "sourceUrl", "p"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("must be an absolute URL");
        }

        @Test
        void anUnknownEnumValueListsTheValidOnes() {
            assertThatThrownBy(() ->
                            PackJson.enumValue(
                                    json("{\"verificationLevel\": \"PROBABLY\"}"), "verificationLevel", "p", VerificationLevel.class))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("SOURCE_CHECKED")
                    .hasMessageContaining("PROBABLY");
        }

        @Test
        void anUnknownTagListsThePermittedTags() {
            // The reader is a pack author choosing between "SlabLadder" and a typo of it
            assertThatThrownBy(() -> ShapeJson.cap(json("{\"type\": \"SlabLadderr\"}"), "cap"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("cap.type must be one of")
                    .hasMessageContaining("SlabLadder")
                    .hasMessageContaining("PerUnitPerPeriod")
                    .hasMessageContaining("SlabLadderr");
        }

        @Test
        void anArrayWhereAnObjectIsExpectedSaysSo() {
            assertThatThrownBy(() -> PackJson.object(json("{\"cap\": []}"), "cap", "versions[0]"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("versions[0].cap must be an object");
        }
    }

    @Nested
    @DisplayName("leaf values")
    class Values {

        @Test
        void provenanceIsReadWhole() {
            Provenance read = ValueJson.provenance(
                    json("""
                    { "sourceUrl": "https://egazette.gov.in/WriteReadData/2025/265620.pdf",
                      "sourceDescription": "Gazette CG-DL-E-22082025-265620, s.202(1) Table",
                      "accessedOn": "2026-09-18", "verificationLevel": "SOURCE_CHECKED" }"""),
                    "p");

            assertThat(read.verificationLevel()).isEqualTo(VerificationLevel.SOURCE_CHECKED);
            assertThat(read.sourceDescription()).contains("s.202(1) Table");
        }

        @Test
        void anUnverifiedProvenanceWithoutWhatToCheckIsRejectedByTheTypeItself() {
            // Invariant I1, enforced by Provenance's constructor. This class does not know about I1;
            // constructing the domain type is what applies it to pack data.
            assertThatThrownBy(() -> ValueJson.provenance(
                            json("""
                            { "sourceUrl": "https://egazette.gov.in/x.pdf",
                              "sourceDescription": "somewhere",
                              "accessedOn": "2026-09-18", "verificationLevel": "UNVERIFIED" }"""),
                            "law/rates.json rules[0].provenance"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageStartingWith("law/rates.json rules[0].provenance:")
                    .hasCauseInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void aProvisionsPresenceDecidesWhetherAReferenceIsRequired() {
            assertThat(ValueJson.provision(
                                    json("""
                                    { "instrument": "Income-tax Act, 2025",
                                      "reference": "s.202(1) Table", "presence": "PRESENT" }"""),
                                    "p")
                            .reference())
                    .isEqualTo("s.202(1) Table");

            assertThat(ValueJson.provision(
                                    json("""
                                    { "instrument": "Income-tax Act, 1961", "presence": "NOT_YET_VERIFIED" }"""),
                                    "p")
                            .presence())
                    .isEqualTo(Presence.NOT_YET_VERIFIED);
        }

        @Test
        void anAmendmentWhoseFlagContradictsItsDatesIsRejected() {
            assertThatThrownBy(() -> ValueJson.amendment(
                            json("""
                            { "instrumentCitation": "Finance Act, 2026",
                              "gazetteRef": "CG-DL-E-31032026-271439",
                              "notifiedOn": "2026-03-31", "effectiveFrom": "2026-04-01",
                              "retrospective": true,
                              "sourceUrl": "https://egazette.gov.in/x.pdf", "verificationLevel": "SOURCE_CHECKED" }"""),
                            "law/rates.json rules[0].versions[0].introducedBy"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageStartingWith("law/rates.json rules[0].versions[0].introducedBy:")
                    .hasMessageContaining("must agree");
        }
    }

    @Nested
    @DisplayName("Validity")
    class Validities {

        @Test
        void betweenIsOpenEndedWithoutTo() {
            Validity read = ShapeJson.validity(
                    json("{\"type\": \"Between\", \"from\": \"2026-04-01\"}"), "v", provenance());

            assertThat(read.contains(LocalDate.of(2099, 1, 1))).isTrue();
        }

        @Test
        void retrospectiveKeepsBothDates() {
            Validity read = ShapeJson.validity(
                    json("""
                    { "type": "Retrospective", "deemedEffectiveFrom": "2026-04-01",
                      "actuallyEnactedOn": "2026-08-15" }"""),
                    "v",
                    provenance());

            assertThat(read).isInstanceOf(Validity.Retrospective.class);
            assertThat(read.contains(LocalDate.of(2026, 5, 1))).isTrue();
        }

        @Test
        void pendingNotificationIsNeverInForce() {
            Validity read = ShapeJson.validity(
                    json("""
                    { "type": "PendingNotification", "interimRule": "SAL-DISPENSION-002",
                      "interimDescription": "the pre-amendment exemption continues" }"""),
                    "v",
                    provenance());

            assertThat(read.contains(LocalDate.of(2026, 6, 1))).isFalse();
        }
    }

    @Nested
    @DisplayName("RegimeApplicability")
    class Applicability {

        @Test
        void onlyInAndExceptInDisagreeAboutARegimeThatDoesNotExistYet() {
            RegimeCode future = new RegimeCode("FUTURE_REGIME");

            assertThat(ShapeJson.regimes(
                                    json("{\"type\": \"OnlyIn\", \"regimes\": [\"NEW_TAX_REGIME\"]}"), "r")
                            .appliesIn(future))
                    .isFalse();
            assertThat(ShapeJson.regimes(
                                    json("{\"type\": \"ExceptIn\", \"regimes\": [\"NEW_TAX_REGIME\"]}"), "r")
                            .appliesIn(future))
                    .isTrue();
            assertThat(ShapeJson.regimes(json("{\"type\": \"AllRegimes\"}"), "r").appliesIn(future))
                    .isTrue();
        }

        @Test
        void anEmptyOnlyInIsRejectedByTheTypeItself() {
            assertThatThrownBy(() ->
                            ShapeJson.regimes(
                                    json("{\"type\": \"OnlyIn\", \"regimes\": []}"),
                                    "law/rates.json rules[0].versions[0].regimes"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageStartingWith("law/rates.json rules[0].versions[0].regimes:")
                    .hasMessageContaining("applies nowhere");
        }
    }

    @Nested
    @DisplayName("CapShape — all six")
    class Caps {

        @Test
        void fixedAndPercentOfBase() {
            assertThat(ShapeJson.cap(json("{\"type\": \"Fixed\", \"amount\": \"75000\"}"), "c"))
                    .isEqualTo(new Fixed(Money.of("75000")));
            assertThat(ShapeJson.cap(
                            json("{\"type\": \"PercentOfBase\", \"percent\": \"30\", \"base\": \"ANNUAL_VALUE\"}"),
                            "c"))
                    .isEqualTo(new PercentOfBase(Rate.percent("30"), new BaseId("ANNUAL_VALUE")));
        }

        /** The real ladder from law/rates.json. */
        private static final String REAL_LADDER = """
                { "type": "SlabLadder", "base": "ROUNDED_TOTAL_INCOME", "bands": [
                  { "lowerExclusive": "0",       "upperInclusive": "400000",  "rate": "0"  },
                  { "lowerExclusive": "400000",  "upperInclusive": "800000",  "rate": "5"  },
                  { "lowerExclusive": "800000",  "upperInclusive": "1200000", "rate": "10" },
                  { "lowerExclusive": "1200000", "upperInclusive": "1600000", "rate": "15" },
                  { "lowerExclusive": "1600000", "upperInclusive": "2000000", "rate": "20" },
                  { "lowerExclusive": "2000000", "upperInclusive": "2400000", "rate": "25" },
                  { "lowerExclusive": "2400000", "upperInclusive": null,      "rate": "30" } ] }""";

        @Test
        void theRealLadderParsesAndComputesTheRulebookFigures() {
            // The first proof that W-032's JSON and Phase 2's types agree
            CapShape ladder = ShapeJson.cap(json(REAL_LADDER), "c");
            CapContext ctx = CapContext.of(new BaseId("ROUNDED_TOTAL_INCOME"), Money.of("5000000"));

            assertThat(ladder).isInstanceOf(SlabLadder.class);
            assertThat(ladder.apply(ctx).value()).isEqualTo(Money.of("1080000"));
        }

        @Test
        void aLadderWithTheFourHundredThousandAndOneTrapIsRejectedAtParseTime() {
            // validate() runs during parsing, so the pack never loads rather than failing later
            String trapped = REAL_LADDER.replace("\"lowerExclusive\": \"400000\"", "\"lowerExclusive\": \"400001\"");

            assertThatThrownBy(() -> ShapeJson.cap(json(trapped), "c"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("RATE-SLAB-BAND-002");
        }

        @Test
        void leastOfKeepsItsLimbNames() {
            CapShape cap = ShapeJson.cap(
                    json("""
                    { "type": "LeastOf", "limbs": [
                      { "name": "received", "shape": { "type": "Fixed", "amount": "600000" } },
                      { "name": "rent less 10% of salary", "shape": { "type": "Fixed", "amount": "228000" } } ] }"""),
                    "c");

            assertThat(cap.apply(new CapContext(Map.of())).limitedBy())
                    .isEqualTo("rent less 10% of salary");
        }

        @Test
        void aggregateWithCeilingReadsItsSubCaps() {
            CapShape cap = ShapeJson.cap(
                    json("""
                    { "type": "AggregateWithCeiling",
                      "contributors": ["EMPLOYEE_NPS"],
                      "ceiling": "150000",
                      "subCaps": { "EMPLOYEE_NPS": { "type": "PercentOfBase", "percent": "10", "base": "SALARY_BASE" } } }"""),
                    "c");

            CapContext ctx = new CapContext(
                    Map.of(new BaseId("SALARY_BASE"), Money.of("400000")),
                    Map.of(new ComponentCode("EMPLOYEE_NPS"), Money.of("150000")),
                    Map.of());

            // T5-3: sub-cap first — 10% of 4,00,000
            assertThat(cap.apply(ctx).value()).isEqualTo(Money.of("40000"));
        }

        @Test
        void perUnitPerPeriodWithAndWithoutAUnit() {
            CapShape cea = ShapeJson.cap(
                    json("""
                    { "type": "PerUnitPerPeriod", "amount": "3000",
                      "unit": "CHILD", "maxUnits": 2, "period": "MONTH" }"""),
                    "c");
            CapShape rent = ShapeJson.cap(
                    json("{\"type\": \"PerUnitPerPeriod\", \"amount\": \"5000\", \"period\": \"MONTH\"}"),
                    "c");

            CapContext ctx = new CapContext(
                    Map.of(),
                    Map.of(),
                    Map.of(new BaseId("CHILD"), 2, new BaseId("MONTH"), 12));

            assertThat(cea.apply(ctx).value()).isEqualTo(Money.of("72000"));
            assertThat(rent.apply(ctx).value()).isEqualTo(Money.of("60000"));
        }

        @Test
        void aNonNumericAmountSaysWhatItShouldBe() {
            assertThatThrownBy(() -> ShapeJson.cap(json("{\"type\": \"Fixed\", \"amount\": \"75,000\"}"), "c"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("must be a rupee amount as a string")
                    .hasMessageContaining("75,000");
        }

        @Test
        void aRateWithAPercentSignSaysWhatItShouldBe() {
            assertThatThrownBy(() -> ShapeJson.cap(
                            json("{\"type\": \"PercentOfBase\", \"percent\": \"30%\", \"base\": \"B\"}"), "c"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("as the statute states it");
        }
    }

    // -------------------------------------------------------------- W-033b

    @Nested
    @DisplayName("Condition — all sixteen")
    class Conditions {

        static com.thiru.wealthlens.taxengine.rules.condition.ConditionContext taxpayer() {
            return new com.thiru.wealthlens.taxengine.rules.condition.ConditionContext(
                    true,
                    com.thiru.wealthlens.taxengine.rules.condition.PersonType.INDIVIDUAL,
                    Map.of(
                            com.thiru.wealthlens.taxengine.rules.condition.AgeSubject.SELF, 45,
                            com.thiru.wealthlens.taxengine.rules.condition.AgeSubject.INSURED_PERSON, 70),
                    Map.of(
                            com.thiru.wealthlens.taxengine.rules.condition.AgeSubject.SELF,
                            com.thiru.wealthlens.taxengine.rules.condition.DisabilitySeverity.SEVERE_DISABILITY),
                    Map.of(new com.thiru.wealthlens.taxengine.rules.id.NodeId("round.totalincome"), Money.of("1200000")),
                    java.util.Set.of(com.thiru.wealthlens.taxengine.rules.condition.HeadCode.SALARIES),
                    java.util.Set.of(new ComponentCode("HRA")),
                    java.util.Set.of(com.thiru.wealthlens.taxengine.rules.condition.EvidenceType.LANDLORD_PAN),
                    java.util.Optional.of(true),
                    com.thiru.wealthlens.taxengine.rules.condition.ItemFacts.of(
                            new com.thiru.wealthlens.taxengine.rules.id.ItemFactId("vehicle.engine"), "ELECTRIC"));
        }

        private static boolean holds(String raw) {
            return ConditionJson.condition(json(raw), "c").holds(taxpayer());
        }

        @Test
        void everyVariantParsesAndEvaluates() {
            assertThat(holds("{\"type\": \"IsResident\"}")).isTrue();
            assertThat(holds("{\"type\": \"PaidOtherThanInCash\"}")).isTrue();
            assertThat(holds("{\"type\": \"AgeAtLeast\", \"years\": 60, \"subject\": \"SELF\"}")).isFalse();
            assertThat(holds("{\"type\": \"AgeAtLeast\", \"years\": 60, \"subject\": \"INSURED_PERSON\"}"))
                    .isTrue();
            assertThat(holds("{\"type\": \"HasPersonType\", \"types\": [\"INDIVIDUAL\"]}")).isTrue();
            assertThat(holds("{\"type\": \"IsCertifiedDisabled\", \"minimum\": \"DISABILITY\"}")).isTrue();
            assertThat(holds(
                            "{\"type\": \"IncomeAtMost\", \"node\": \"round.totalincome\", \"limit\": \"1200000\"}"))
                    .isTrue();
            assertThat(holds(
                            "{\"type\": \"IncomeAtLeast\", \"node\": \"round.totalincome\", \"threshold\": \"5000000\"}"))
                    .isFalse();
            assertThat(holds("{\"type\": \"HasIncomeUnderHead\", \"head\": \"SALARIES\"}")).isTrue();
            assertThat(holds("{\"type\": \"HasIncomeUnderHead\", \"head\": \"BUSINESS_OR_PROFESSION\"}"))
                    .isFalse();
            assertThat(holds("{\"type\": \"ComponentPresent\", \"code\": \"HRA\"}")).isTrue();
            assertThat(holds("{\"type\": \"EvidenceRecorded\", \"evidenceType\": \"LANDLORD_PAN\"}")).isTrue();
            assertThat(holds("{\"type\": \"Not\", \"condition\": {\"type\": \"IsResident\"}}")).isFalse();
            assertThat(holds(
                            "{\"type\": \"AllOf\", \"all\": [{\"type\": \"IsResident\"}, {\"type\": \"ComponentPresent\", \"code\": \"HRA\"}]}"))
                    .isTrue();
            assertThat(holds(
                            "{\"type\": \"AnyOf\", \"any\": [{\"type\": \"AgeAtLeast\", \"years\": 60, \"subject\": \"SELF\"}, {\"type\": \"IsResident\"}]}"))
                    .isTrue();
        }

        @Test
        void evidenceRecordedIsReadFromEvidenceTypeNotType() {
            // Its record component is called `type`, which is also the discriminator. Naming both
            // `type` would make the object mean two things at once.
            assertThatThrownBy(() ->
                            ConditionJson.condition(
                                    json("{\"type\": \"EvidenceRecorded\"}"), "c"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("c.evidenceType is required and is absent");
        }

        @Test
        void theSOneTwoZeroTwoFourRevivalConditionParses() {
            // From law/regimes.json: Not(HasIncomeUnderHead(BUSINESS_OR_PROFESSION))
            var revival = ConditionJson.condition(
                    json("""
                    { "type": "Not", "condition":
                      { "type": "HasIncomeUnderHead", "head": "BUSINESS_OR_PROFESSION" } }"""),
                    "c");

            assertThat(revival.holds(taxpayer())).isTrue();
            assertThat(revival.describe()).contains("BUSINESS_OR_PROFESSION");
        }

        @Test
        void anUnknownPersonTypeListsTheKnownOnes() {
            assertThatThrownBy(() ->
                            ConditionJson.condition(
                                    json("{\"type\": \"HasPersonType\", \"types\": [\"FIRM\"]}"), "c"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("\"FIRM\"")
                    .hasMessageContaining("INDIVIDUAL")
                    .hasMessageContaining("HUF");
        }

        @Test
        void itemFactIs_whenReadFromJson_testsTheItemsCode() {
            // Given
            var electric = ConditionJson.condition(
                    json("{\"type\": \"ItemFactIs\", \"fact\": \"vehicle.engine\", \"code\": \"ELECTRIC\"}"), "c");
            var small = ConditionJson.condition(
                    json("{\"type\": \"ItemFactIs\", \"fact\": \"vehicle.engine\", \"code\": \"UP_TO_1600_CC\"}"), "c");

            // When / Then
            assertThat(electric.holds(taxpayer())).isTrue();
            assertThat(small.holds(taxpayer())).isFalse();
            assertThat(electric.describe()).isEqualTo("vehicle.engine is ELECTRIC");
        }

        @Test
        void itemFactAtMost_whenReadFromJson_testsTheItemsQuantityInclusively() {
            // Given
            var upTo1600 = ConditionJson.condition(
                    json("{\"type\": \"ItemFactAtMost\", \"fact\": \"vehicle.engine.cc\", \"limit\": 1600}"), "c");
            var cc = new com.thiru.wealthlens.taxengine.rules.id.ItemFactId("vehicle.engine.cc");

            // When / Then
            assertThat(upTo1600.holds(taxpayer().about(
                    new com.thiru.wealthlens.taxengine.rules.condition.ItemFacts(Map.of(), Map.of(cc, 1600L), Map.of()))))
                    .isTrue();
            assertThat(upTo1600.holds(taxpayer().about(
                    new com.thiru.wealthlens.taxengine.rules.condition.ItemFacts(Map.of(), Map.of(cc, 1601L), Map.of()))))
                    .isFalse();
            assertThat(upTo1600.describe()).isEqualTo("vehicle.engine.cc is at most 1600");
        }

        @Test
        void itemDateBefore_whenReadFromJson_testsTheItemsDateStrictly() {
            // Given — S.O. 5054(E) clause (I): "before the 1st day of October, 2004"
            var before = ConditionJson.condition(json("{\"type\": \"ItemDateBefore\", \"fact\":"
                    + " \"capitalgains.acquired.on\", \"date\": \"2004-10-01\"}"), "c");
            var acquiredOn = new com.thiru.wealthlens.taxengine.rules.id.ItemFactId("capitalgains.acquired.on");

            // When / Then
            assertThat(before.holds(taxpayer().about(new com.thiru.wealthlens.taxengine.rules.condition.ItemFacts(
                    Map.of(), Map.of(), Map.of(acquiredOn, java.time.LocalDate.of(2004, 9, 30)))))).isTrue();
            assertThat(before.holds(taxpayer().about(new com.thiru.wealthlens.taxengine.rules.condition.ItemFacts(
                    Map.of(), Map.of(), Map.of(acquiredOn, java.time.LocalDate.of(2004, 10, 1)))))).isFalse();
            assertThat(before.describe()).isEqualTo("capitalgains.acquired.on is before 2004-10-01");
        }

        @Test
        void itemDateBefore_whenTheDateIsNotADate_isRefusedNamingThePath() {
            assertThatThrownBy(() -> ConditionJson.condition(json("{\"type\": \"ItemDateBefore\", \"fact\":"
                            + " \"capitalgains.acquired.on\", \"date\": \"1-10-2004\"}"), "c"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("c.date");
        }

        @Test
        void itemFactIs_whenCodeMissing_isRefused() {
            assertThatThrownBy(() -> ConditionJson.condition(
                            json("{\"type\": \"ItemFactIs\", \"fact\": \"vehicle.engine\"}"), "c"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("code");
        }

        @Test
        void anUnknownTagListsAllSixteen() {
            assertThatThrownBy(() -> ConditionJson.condition(json("{\"type\": \"IsRich\"}"), "c"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("IsResident")
                    .hasMessageContaining("PaidOtherThanInCash")
                    .hasMessageContaining("ItemFactIs")
                    .hasMessageContaining("ItemFactAtMost")
                    .hasMessageContaining("ItemDateBefore")
                    .hasMessageContaining("IsRich");
        }
    }

    @Nested
    @DisplayName("RuleRow — the real law/rates.json")
    class Rules {

        private static final String REAL_RULE = """
                { "rules": [
                  { "ruleId": "RATE-SLAB-NEW-TAX-REGIME",
                    "axis": "TAX_YEAR",
                    "midYearCombination": "LATEST_VERSION_GOVERNS",
                    "versions": [
                      { "validity": { "type": "Between", "from": "2026-04-01" },
                        "regimes": { "type": "OnlyIn", "regimes": ["NEW_TAX_REGIME"] },
                        "provision1961": { "instrument": "Income-tax Act, 1961", "reference": "s.115BAC(1A)(iii)", "presence": "PRESENT" },
                        "provision2025": { "instrument": "Income-tax Act, 2025", "reference": "s.202(1) Table", "presence": "PRESENT" },
                        "cap": { "type": "Fixed", "amount": "0" },
                        "conditions": [],
                        "introducedBy": {
                          "instrumentCitation": "Income-tax Act, 2025 (30 of 2025), s.202(1)",
                          "gazetteRef": "CG-DL-E-22082025-265620",
                          "notifiedOn": "2025-08-21", "effectiveFrom": "2026-04-01",
                          "retrospective": false,
                          "sourceUrl": "https://egazette.gov.in/WriteReadData/2025/265620.pdf",
                          "verificationLevel": "SOURCE_CHECKED" },
                        "legalStatus": "IN_FORCE", "publicationStatus": "PUBLISHED",
                        "provenance": {
                          "sourceUrl": "https://egazette.gov.in/WriteReadData/2025/265620.pdf",
                          "sourceDescription": "Gazette CG-DL-E-22082025-265620, s.202(1) Table",
                          "accessedOn": "2026-09-18", "verificationLevel": "SOURCE_CHECKED" } } ],
                    "headProvenance": {
                      "sourceUrl": "https://egazette.gov.in/WriteReadData/2025/265620.pdf",
                      "sourceDescription": "Gazette CG-DL-E-22082025-265620, s.202(1) Table",
                      "accessedOn": "2026-09-18", "verificationLevel": "SOURCE_CHECKED" } } ] }""";

        @Test
        void aRuleIsReadWithItsAxisVersionsAndAmendment() {
            var rules = RuleJson.rules(json(REAL_RULE), "law/rates.json");
            var slab = rules.get(new com.thiru.wealthlens.taxengine.rules.id.RuleId("RATE-SLAB-NEW-TAX-REGIME"));

            assertThat(slab.axis())
                    .isEqualTo(com.thiru.wealthlens.taxengine.rules.temporal.TemporalAxis.TAX_YEAR);
            assertThat(slab.versions()).hasSize(1);
            assertThat(slab.versions().getFirst().introducedBy().gazetteRef())
                    .isEqualTo("CG-DL-E-22082025-265620");
            assertThat(slab.midYearCombination())
                    .isEqualTo(com.thiru.wealthlens.taxengine.rules.pack.MidYearCombination.LATEST_VERSION_GOVERNS);
        }

        @Test
        void aRuleDefinedTwiceIsRejected() {
            String twice = REAL_RULE.replace("\"rules\": [", "\"rules\": [")
                    .replaceFirst("\\] \\}$", "] }");
            var doubled = json(REAL_RULE.substring(0, REAL_RULE.lastIndexOf("] }"))
                    + ", " + REAL_RULE.substring(REAL_RULE.indexOf("{ \"ruleId\""),
                            REAL_RULE.lastIndexOf("] }")) + "] }");

            assertThatThrownBy(() -> RuleJson.rules(doubled, "law/rates.json"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("twice")
                    .hasMessageContaining("silently win");
        }

        @Test
        void aVersionMissingItsAmendmentSaysSo() {
            // Invariant I15, reached at parse time rather than at validation
            String stripped = REAL_RULE.replaceAll(
                    "(?s)\"introducedBy\": \\{.*?\\},\\n", "");

            assertThatThrownBy(() -> RuleJson.rules(json(stripped), "law/rates.json"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("introducedBy is required and is absent");
        }

        @Test
        void aRuleWithNeitherACapNorAConditionIsRejected() {
            String empty = REAL_RULE
                    .replace("\"cap\": { \"type\": \"Fixed\", \"amount\": \"0\" },\n", "");

            assertThatThrownBy(() -> RuleJson.rules(json(empty), "law/rates.json"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("would load, evaluate, and mean nothing");
        }

        @Test
        void aRuleMayHaveConditionsAndNoCap() {
            // SAL-HRA-002's "the accommodation is not owned by the assessee" limits nothing by itself
            String conditionOnly = REAL_RULE
                    .replace("\"cap\": { \"type\": \"Fixed\", \"amount\": \"0\" },\n", "")
                    .replace("\"conditions\": []", "\"conditions\": [{ \"type\": \"IsResident\" }]");

            var rules = RuleJson.rules(json(conditionOnly), "law/rates.json");

            assertThat(rules).hasSize(1);
            assertThat(rules.values().iterator().next().versions().getFirst().conditions()).hasSize(1);
        }

        @Test
        void anUnknownAxisListsTheSix() {
            String badAxis = REAL_RULE.replace("\"axis\": \"TAX_YEAR\"", "\"axis\": \"CALENDAR_YEAR\"");

            assertThatThrownBy(() -> RuleJson.rules(json(badAxis), "law/rates.json"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("ACQUISITION_DATE")
                    .hasMessageContaining("CALENDAR_YEAR");
        }

        @Test
        void theFailurePathNamesTheRule() {
            // "law/rates.json rules[0] (RATE-SLAB-NEW-TAX-REGIME).versions[0]..." — a pack with forty
            // rules needs the id, not just the index
            String badRate = REAL_RULE.replace("\"amount\": \"0\"", "\"amount\": \"nought\"");

            assertThatThrownBy(() -> RuleJson.rules(json(badRate), "law/rates.json"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("RATE-SLAB-NEW-TAX-REGIME");
        }
    }

    // -------------------------------------------------------------- W-033c

    @Nested
    @DisplayName("regimes — the real law/regimes.json")
    class Regimes {

        private static String realRegimes() {
            return compiled2026("law/regimes.json");
        }

        @Test
        void bothRegimesParseFromTheShippedPack() {
            var regimes = RegimeJson.regimes(json(realRegimes()), "law/regimes.json");

            assertThat(regimes.keySet())
                    .extracting(RegimeCode::value)
                    .containsExactlyInAnyOrder("NEW_TAX_REGIME", "FIRST_SCHEDULE_RATES");
        }

        @Test
        void theDefaultRegimeNeedsNoActionAndHasNoDeadline() {
            var regimes = RegimeJson.regimes(json(realRegimes()), "law/regimes.json");
            var def = regimes.get(new RegimeCode("NEW_TAX_REGIME"));

            assertThat(def.isDefault()).isTrue();
            assertThat(def.election().kind())
                    .isEqualTo(com.thiru.wealthlens.taxengine.rules.regime.ElectionKind.DEFAULT_NO_ACTION);
            assertThat(def.election().deadline())
                    .isInstanceOf(com.thiru.wealthlens.taxengine.rules.regime.DeadlineSpec.NoDeadline.class);
            assertThat(def.election().form()).isEmpty();
        }

        @Test
        void theOptOutCarriesTheSTwoZeroTwoFourBindingAndItsRevival() {
            var regimes = RegimeJson.regimes(json(realRegimes()), "law/regimes.json");
            var optedOut = regimes.get(new RegimeCode("FIRST_SCHEDULE_RATES"));
            var binding = optedOut.election().binding();

            assertThat(binding.maxWithdrawals()).isEqualTo(1);
            assertThat(binding.bindsSubsequentYears()).isTrue();
            assertThat(binding.afterWithdrawal())
                    .isInstanceOf(
                            com.thiru.wealthlens.taxengine.rules.regime.AfterWithdrawal.IneligibleUnless.class);

            // The binding applies to a person WITH business income and not to a salaried one
            assertThat(binding.appliesTo(Conditions.taxpayer())).isFalse();
        }

        @Test
        void bothRegimesAreGrandfatherableAndInForce() {
            var regimes = RegimeJson.regimes(json(realRegimes()), "law/regimes.json");

            regimes.values().forEach(regime -> {
                assertThat(regime.lifecycle().applicable(new com.thiru.wealthlens.taxengine.rules.id.TaxYearId(2026)))
                        .isTrue();
                assertThat(regime.lifecycle().onSunset())
                        .isInstanceOf(
                                com.thiru.wealthlens.taxengine.rules.regime.ContinuationPolicy.ContinueIndefinitely.class);
            });
        }

        @Test
        void theStaleLockInFieldFailsWithTheCurrentTags() {
            // A pack copied from 16-getting-started.md's pre-L3 example
            assertThatThrownBy(() ->
                            RegimeJson.binding(json("{\"lockIn\": \"NONE\"}"), "election.binding"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("bindsSubsequentYears is required and is absent");
        }

        @Test
        void aBareStringDeadlineFailsWithTheCurrentTags() {
            assertThatThrownBy(() ->
                            RegimeJson.deadline(json("{\"type\": \"s.263(1) due date\"}"), "d"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("DueDateUnder")
                    .hasMessageContaining("NoDeadline");
        }

        @Test
        void disallowanceSets_whenTheShippedPackIsRead_holdTheNewRegimesSetsWithTheirBases() {
            // When — D-165: the s.202(2)(a) set; D-217: the s.202(2)(b)(ii) set, compiled from law/disallowances/
            var sets = RegistryJson.disallowanceSets(json(realRegimes()), "law/regimes.json");

            // Then
            var set = sets.get(new DisallowanceSetId("DIS-NEW-TAX-REGIME-NO-EXEMPTION"));
            var setOff = sets.get(new DisallowanceSetId("DIS-NEW-TAX-REGIME-NO-SETOFF"));
            assertThat(sets).hasSize(2);
            assertThat(set.basis().value()).isEqualTo("REG-NEW-TAX-REGIME-NO-EXEMPTION");
            assertThat(set.disallows(new RuleId("SAL-TAX-ON-EMPLOYMENT"))).isTrue();
            assertThat(setOff.basis().value()).isEqualTo("REG-NEW-TAX-REGIME-NO-SETOFF");
            assertThat(setOff.disallows(new RuleId("SETOFF-HOUSE-PROPERTY"))).isTrue();
        }

        @Test
        void aDisallowanceSetIsReadWithItsBasisWhenOneExists() {
            var sets = RegistryJson.disallowanceSets(
                    json("""
                    { "disallowanceSets": [
                      { "id": "DIS-S202-2A", "displayName": "Disallowed under s.202(2)(a)",
                        "basis": "REG-NEW-DISALLOW-001",
                        "disallowedRules": ["SAL-HRA-005"],
                        "provenance": {
                          "sourceUrl": "https://egazette.gov.in/x.pdf",
                          "sourceDescription": "s.202(2)(a)",
                          "accessedOn": "2026-09-18", "verificationLevel": "SOURCE_CHECKED" } } ] }"""),
                    "law/regimes.json");

            assertThat(sets.values().iterator().next().basis().value())
                    .isEqualTo("REG-NEW-DISALLOW-001");
            assertThat(sets.values().iterator().next()
                            .disallows(new com.thiru.wealthlens.taxengine.rules.id.RuleId("SAL-HRA-005")))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("JsonPackAssembler — the whole pack")
    class Assembly {

        private static String resource(String path) {
            return compiled2026(path);
        }

        private static com.thiru.wealthlens.taxengine.rules.registry.GoverningInstruments instruments() {
            var act = com.thiru.wealthlens.taxengine.rules.registry.Instrument.assented(
                    "Income-tax Act, 2025 (30 of 2025)",
                    LocalDate.of(2025, 8, 21),
                    LocalDate.of(2026, 4, 1),
                    "CG-DL-E-22082025-265620",
                    java.net.URI.create("https://egazette.gov.in/WriteReadData/2025/265620.pdf"),
                    VerificationLevel.SOURCE_CHECKED);
            var rules = com.thiru.wealthlens.taxengine.rules.registry.Instrument.notified(
                    "Income-tax Rules, 2026",
                    LocalDate.of(2026, 4, 1),
                    "CG-DL-E-20032026-271092",
                    java.net.URI.create("https://egazette.gov.in/WriteReadData/2026/271092.pdf"),
                    VerificationLevel.SOURCE_CHECKED);
            return new com.thiru.wealthlens.taxengine.rules.registry.GoverningInstruments(act, act, rules);
        }

        private static java.util.Map<
                        com.thiru.wealthlens.taxengine.rules.pack.RulePackLayer,
                        com.thiru.wealthlens.taxengine.rules.pack.RulePackRef>
                layers() {
            var map = new java.util.EnumMap<
                    com.thiru.wealthlens.taxengine.rules.pack.RulePackLayer,
                    com.thiru.wealthlens.taxengine.rules.pack.RulePackRef>(
                    com.thiru.wealthlens.taxengine.rules.pack.RulePackLayer.class);
            for (var layer : com.thiru.wealthlens.taxengine.rules.pack.RulePackLayer.values()) {
                String name = layer.name().toLowerCase(java.util.Locale.ROOT);
                map.put(
                        layer,
                        new com.thiru.wealthlens.taxengine.rules.pack.RulePackRef(
                                layer, "2026-27." + name + ".1", "sha256:" + "a".repeat(64)));
            }
            return map;
        }

        @Test
        void theShippedPackAssemblesWhole() {
            var pack = new JsonPackAssembler(instruments())
                    .assemble(
                            new com.thiru.wealthlens.taxengine.rules.id.TaxYearId(2026),
                            layers(),
                            Map.of(
                                    "law/rates.json", resource("law/rates.json"),
                                    "law/regimes.json", resource("law/regimes.json"),
                                    "exec/components.json", resource("exec/components.json")));

            assertThat(pack.rules()).hasSize(203);
            assertThat(pack.regimes()).hasSize(2);
            assertThat(pack.disallowanceSets()).containsOnlyKeys(new DisallowanceSetId("DIS-NEW-TAX-REGIME-NO-EXEMPTION"),
                    new DisallowanceSetId("DIS-NEW-TAX-REGIME-NO-SETOFF"));
            assertThat(pack.components()).containsOnlyKeys(new ComponentCode("BASIC"), new ComponentCode("BONUS"), new ComponentCode("CHILDREN_EDUCATION_ALLOWANCE"), new ComponentCode("COMMISSION"), new ComponentCode("CONVEYANCE_ALLOWANCE"), new ComponentCode("DEARNESS_ALLOWANCE"), new ComponentCode("ENTERTAINMENT_ALLOWANCE"), new ComponentCode("HOSTEL_ALLOWANCE"), new ComponentCode("HRA"), new ComponentCode("LTA"), new ComponentCode("PROFESSIONAL_TAX"), new ComponentCode("SPECIAL_ALLOWANCE"), new ComponentCode("TRANSPORT_ALLOWANCE_DISABLED"), new ComponentCode("UNIFORM_ALLOWANCE"), new ComponentCode("GRATUITY"), new ComponentCode("LEAVE_ENCASHMENT"), new ComponentCode("COMMUTED_PENSION"), new ComponentCode("VRS_COMPENSATION"), new ComponentCode("RETRENCHMENT_COMPENSATION"), new ComponentCode("NOTICE_PAY"), new ComponentCode("DEFERRED_ANNUITY"), new ComponentCode("PROVIDENT_FUNDS_ACT_FUND"), new ComponentCode("NOTIFIED_PROVIDENT_FUND"), new ComponentCode("EMPLOYEE_PF"), new ComponentCode("EMPLOYEE_SUPERANNUATION"), new ComponentCode("NOTIFIED_DEPOSIT_SCHEME"), new ComponentCode("SAVINGS_CERTIFICATE"), new ComponentCode("MUTUAL_FUND_UNITS"), new ComponentCode("MUTUAL_FUND_PENSION_FUND"), new ComponentCode("NATIONAL_HOUSING_BANK_SCHEME"), new ComponentCode("HOUSING_FINANCE_DEPOSIT"), new ComponentCode("TUITION_FEES"), new ComponentCode("HOUSE_PURCHASE_PAYMENT"), new ComponentCode("BANK_TERM_DEPOSIT"), new ComponentCode("NABARD_BONDS"), new ComponentCode("SENIOR_CITIZEN_SAVINGS"), new ComponentCode("POST_OFFICE_TIME_DEPOSIT"), new ComponentCode("GOVERNMENT_PENSION_ADDITIONAL"), new ComponentCode("PENSION_FUND_ANNUITY"), new ComponentCode("EMPLOYEE_NPS"), new ComponentCode("ELIGIBLE_ISSUE_SUBSCRIPTION"), new ComponentCode("NPS_MINOR_DEPOSIT"), new ComponentCode("LIFE_INSURANCE_PREMIUM"), new ComponentCode("HEALTH_INSURANCE_FAMILY"), new ComponentCode("HEALTH_INSURANCE_PARENTS"), new ComponentCode("MEDICAL_EXPENDITURE_FAMILY"), new ComponentCode("MEDICAL_EXPENDITURE_PARENTS"), new ComponentCode("GOVERNMENT_DEFERRED_ANNUITY"), new ComponentCode("UNIT_LINKED_INSURANCE_PLAN"), new ComponentCode("MUTUAL_FUND_UNIT_LINKED_PLAN"), new ComponentCode("ANNUITY_PLAN"));
        }

        @Test
        void theAssembledPackComputesTheRulebookFigure() {
            // The full chain: JSON on disk to a tax figure. RATE-SLAB-NEW-TAX-REGIME on ₹50,00,000.
            var pack = new JsonPackAssembler(instruments())
                    .assemble(
                            new com.thiru.wealthlens.taxengine.rules.id.TaxYearId(2026),
                            layers(),
                            Map.of(
                                    "law/rates.json", resource("law/rates.json"),
                                    "law/regimes.json", resource("law/regimes.json"),
                                    "exec/components.json", resource("exec/components.json")));

            var slab = pack.rule(new com.thiru.wealthlens.taxengine.rules.id.RuleId("RATE-SLAB-NEW-TAX-REGIME"));
            var cap = slab.versions().getFirst().cap().orElseThrow();
            var ctx = CapContext.of(new BaseId("ROUNDED_TOTAL_INCOME"), Money.of("5000000"));

            assertThat(cap.apply(ctx).value()).isEqualTo(Money.of("1080000"));
        }

        @Test
        void aMissingLayerFileNamesWhatWasRead() {
            assertThatThrownBy(() ->
                            new JsonPackAssembler(instruments())
                                    .assemble(
                                            new com.thiru.wealthlens.taxengine.rules.id.TaxYearId(2026),
                                            layers(),
                                            Map.of("law/rates.json", resource("law/rates.json"))))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("law/regimes.json")
                    .hasMessageContaining("the provider read");
        }
    }

    // -------------------------------------------------------------- W-034b

    @Nested
    @DisplayName("E4 — the loader throws nothing but PackValidationException")
    class LoaderThrowsOnlyPackErrors {

        private static String realRates() {
            return compiled2026("law/rates.json");
        }

        /**
         * Mutations of the shipped pack, each tripping a different <em>domain constructor</em>
         * rather than a field-level check — which is the class of failure that used to escape as
         * IllegalArgumentException.
         */
        static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> mutations() {
            return java.util.stream.Stream.of(
                    org.junit.jupiter.params.provider.Arguments.of(
                            "Provenance: blank whatToCheck",
                            // Targeted at a provenance block, the only object with accessedOn: AmendmentRef
                            // also has a `verificationLevel` and no `whatToCheck`. [^}]* keeps the match
                            // inside that one object, whose keys the canonical form sorts
                            (java.util.function.UnaryOperator<String>) s -> s.replaceFirst(
                                    "(\"accessedOn\":\"2026-09-18\",[^}]*\"verificationLevel\":)\"SOURCE_CHECKED\"",
                                    "$1\"UNVERIFIED\",\"whatToCheck\":\"  \"")),
                    org.junit.jupiter.params.provider.Arguments.of(
                            "AmendmentRef: flag contradicts its dates",
                            (java.util.function.UnaryOperator<String>) s ->
                                    s.replaceFirst("\"retrospective\":false", "\"retrospective\":true")),
                    org.junit.jupiter.params.provider.Arguments.of(
                            "RegimeApplicability.OnlyIn: empty set",
                            (java.util.function.UnaryOperator<String>) s -> s.replaceFirst(
                                    "\"regimes\":\\[\"NEW_TAX_REGIME\"\\]", "\"regimes\":[]")),
                    org.junit.jupiter.params.provider.Arguments.of(
                            "SlabLadder: a band that starts where none ended",
                            (java.util.function.UnaryOperator<String>) s ->
                                    s.replaceFirst("\"lowerExclusive\":\"800000\"", "\"lowerExclusive\":\"900000\"")),
                    org.junit.jupiter.params.provider.Arguments.of(
                            "RuleRow: a combination rule with a non-STATUTORY combination",
                            (java.util.function.UnaryOperator<String>) s -> s.replaceFirst(
                                    "\"midYearCombination\":\"LATEST_VERSION_GOVERNS\"",
                                    "\"midYearCombination\":\"LATEST_VERSION_GOVERNS\","
                                            + "\"midYearCombinationRule\":\"SAL-HRA-001\"")));
        }

        @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
        @org.junit.jupiter.params.provider.MethodSource("mutations")
        void everyDomainRejectionBecomesAPackErrorCarryingAPath(
                String description, java.util.function.UnaryOperator<String> mutate) {

            String broken = mutate.apply(realRates());
            assertThat(broken).as("the mutation changed something").isNotEqualTo(realRates());

            assertThatThrownBy(() -> RuleJson.rules(json(broken), "law/rates.json"))
                    .as(description)
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("law/rates.json");
        }

        @Test
        void theShippedPackStillParses() {
            // So a mutation test that passed because everything fails would be caught
            assertThat(RuleJson.rules(json(realRates()), "law/rates.json")).hasSize(203);
        }

        @Test
        void aNestedReadersPreciseMessageIsNotOverwritten() {
            // A PackValidationException passes through construct() untouched: the inner path is
            // more precise than the outer one, and replacing it would lose the field name.
            assertThatThrownBy(() ->
                            ValueJson.provenance(json("{}"), "law/rates.json rules[0].provenance"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessage("law/rates.json rules[0].provenance.sourceUrl is required and is absent");
        }

        @Test
        void theCauseIsChainedSoARealBugStaysDiagnosable() {
            assertThatThrownBy(() -> ValueJson.amendment(
                            json("""
                            { "instrumentCitation": "Finance Act, 2026",
                              "gazetteRef": "CG-DL-E-31032026-271439",
                              "notifiedOn": "2026-03-31", "effectiveFrom": "2026-04-01",
                              "retrospective": true,
                              "sourceUrl": "https://egazette.gov.in/x.pdf", "verificationLevel": "SOURCE_CHECKED" }"""),
                            "p"))
                    .hasCauseInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * A file of the real 2026-27 pack as the loader receives it: compiled from the timeline, in
     * canonical form (D-056).
     */
    private static String compiled2026(String file) {
        return com.thiru.wealthlens.taxengine.rules.timeline.RulePackCompiler.compile(
                        com.thiru.wealthlens.taxengine.rules.timeline.TimelineReader.read(
                                java.nio.file.Path.of("src/main/resources/rulepacks/timeline")),
                        new com.thiru.wealthlens.taxengine.rules.id.TaxYearId(2026))
                .files()
                .get(file);
    }
}
