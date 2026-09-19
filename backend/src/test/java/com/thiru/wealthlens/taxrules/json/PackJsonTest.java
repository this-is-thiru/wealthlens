package com.thiru.wealthlens.taxrules.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxrules.cap.CapContext;
import com.thiru.wealthlens.taxrules.cap.CapShape;
import com.thiru.wealthlens.taxrules.cap.Fixed;
import com.thiru.wealthlens.taxrules.cap.PercentOfBase;
import com.thiru.wealthlens.taxrules.cap.SlabLadder;
import com.thiru.wealthlens.taxrules.id.BaseId;
import com.thiru.wealthlens.taxrules.id.ComponentCode;
import com.thiru.wealthlens.taxrules.id.RegimeCode;
import com.thiru.wealthlens.taxrules.money.Money;
import com.thiru.wealthlens.taxrules.money.Rate;
import com.thiru.wealthlens.taxrules.registry.DraftOrFinal;
import com.thiru.wealthlens.taxrules.registry.Provenance;
import com.thiru.wealthlens.taxrules.registry.VerificationStatus;
import com.thiru.wealthlens.taxrules.temporal.Presence;
import com.thiru.wealthlens.taxrules.temporal.Validity;
import com.thiru.wealthlens.taxrules.validate.PackValidationException;
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
                DraftOrFinal.FINAL,
                LocalDate.of(2026, 9, 19),
                VerificationStatus.VERIFIED,
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
                                    json("{\"status\": \"PROBABLY\"}"), "status", "p", VerificationStatus.class))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("VERIFIED")
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
                      "draftOrFinal": "FINAL", "accessedOn": "2026-09-18", "status": "VERIFIED" }"""),
                    "p");

            assertThat(read.status()).isEqualTo(VerificationStatus.VERIFIED);
            assertThat(read.sourceDescription()).contains("s.202(1) Table");
        }

        @Test
        void anUnverifiedProvenanceWithoutWhatToCheckIsRejectedByTheTypeItself() {
            // Invariant I1, enforced by Provenance's constructor. This class does not know about I1;
            // constructing the domain type is what applies it to pack data.
            assertThatThrownBy(() -> ValueJson.provenance(
                            json("""
                            { "sourceUrl": "https://egazette.gov.in/x.pdf",
                              "sourceDescription": "somewhere", "draftOrFinal": "FINAL",
                              "accessedOn": "2026-09-18", "status": "NEEDS_VERIFICATION" }"""),
                            "p"))
                    .isInstanceOf(IllegalArgumentException.class);
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
                              "sourceUrl": "https://egazette.gov.in/x.pdf", "status": "VERIFIED" }"""),
                            "p"))
                    .isInstanceOf(IllegalArgumentException.class)
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
            RegimeCode future = new RegimeCode("S999_FUTURE");

            assertThat(ShapeJson.regimes(
                                    json("{\"type\": \"OnlyIn\", \"regimes\": [\"S202_DEFAULT\"]}"), "r")
                            .appliesIn(future))
                    .isFalse();
            assertThat(ShapeJson.regimes(
                                    json("{\"type\": \"ExceptIn\", \"regimes\": [\"S202_DEFAULT\"]}"), "r")
                            .appliesIn(future))
                    .isTrue();
            assertThat(ShapeJson.regimes(json("{\"type\": \"AllRegimes\"}"), "r").appliesIn(future))
                    .isTrue();
        }

        @Test
        void anEmptyOnlyInIsRejectedByTheTypeItself() {
            assertThatThrownBy(() ->
                            ShapeJson.regimes(json("{\"type\": \"OnlyIn\", \"regimes\": []}"), "r"))
                    .isInstanceOf(IllegalArgumentException.class)
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
}
