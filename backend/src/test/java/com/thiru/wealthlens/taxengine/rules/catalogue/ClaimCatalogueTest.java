package com.thiru.wealthlens.taxengine.rules.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.temporal.Presence;
import com.thiru.wealthlens.taxengine.rules.temporal.Provision;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.rules.timeline.RulePackCompiler;
import com.thiru.wealthlens.taxengine.rules.timeline.Timeline;
import com.thiru.wealthlens.taxengine.rules.timeline.TimelineReader;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The claim catalogue's frame, its two pilots (W-117a, D-699) and its employer-optional entries (W-117b): the shipped
 * entries say what {@code DR 22} asks of them (T22-1, T22-2, T22-10, T22-11 as content checks), and each invariant
 * refuses a deliberate violation.
 *
 * <p>Expected values are the plans' adopted content and the provisions read in the Gazette (and, for the 1961 Act, the
 * thc.nic.in copy the rules cite), typed here by hand.
 */
class ClaimCatalogueTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Timeline SHIPPED = TimelineReader.read(Path.of("src/main/resources/rulepacks/timeline"));
    private static final RuleId HOUSE_RENT = new RuleId("SAL-HOUSE-RENT-ALLOWANCE");
    private static final RuleId DONATION = new RuleId("DED-DONATION-UNREPORTED");
    private static final RuleId INVESTMENTS = new RuleId("DED-SCHEDULE-XV");
    private static final RuleId OWN_PENSION = new RuleId("DED-NPS-OWN");
    private static final RuleId HEALTH = new RuleId("DED-HEALTH-FAMILY");
    private static final RuleId HOME_LOAN_INTEREST = new RuleId("HP-OWN-RESIDENCE-INTEREST-CAP");
    private static final RuleId LEAVE_TRAVEL = new RuleId("SAL-LEAVE-TRAVEL-CONCESSION");
    private static final RuleId TAX_ON_EMPLOYMENT = new RuleId("SAL-TAX-ON-EMPLOYMENT");
    private static final RuleId SAVINGS_INTEREST = new RuleId("DED-SAVINGS-INTEREST");
    private static final RuleId SENIOR_DEPOSIT_INTEREST = new RuleId("DED-DEPOSIT-INTEREST-SENIOR");
    private static final RuleId DISABILITY = new RuleId("DED-DISABILITY");
    private static final RuleId DISABLED_DEPENDANT = new RuleId("DED-DISABLED-DEPENDANT");
    private static final RuleId EMPLOYER_PENSION = new RuleId("DED-NPS-EMPLOYER-OTHER");
    private static final RuleId DONATIONS = new RuleId("DED-DONATION-WHOLE");
    private static final int HOUSE_RENT_ENTRY = 0;
    private static final int DONATION_ENTRY = 1;
    private static final int HEALTH_ENTRY = 4;
    private static final int LEAVE_TRAVEL_ENTRY = 6;

    /** The shipped timeline with its claim catalogue edited. */
    private static Timeline edited(Consumer<JsonNode> edit) {
        JsonNode file = MAPPER.readTree(SHIPPED.claimCatalogue().orElseThrow());
        edit.accept(file.path("entries"));
        Timeline t = SHIPPED;
        return new Timeline(t.rules(), t.ruleSets(), t.regimes(), t.ruleSources(), t.regimeSources(),
                t.disallowanceSetSources(), t.conditionSetSources(), t.components(), t.componentSources(), t.funds(),
                t.fundSources(), t.declarations(), t.declarationSources(), t.itrFormSources(), t.itrCells(),
                t.itrCellSources(), t.pipelineNodes(), t.coverage(), t.explanations(),
                Optional.of(MAPPER.writeValueAsString(file)), t.sections(), t.acts());
    }

    /** The shipped timeline with its claim catalogue file edited as a whole, its lists included (W-117d). */
    private static Timeline editedFile(Consumer<ObjectNode> edit) {
        ObjectNode file = (ObjectNode) MAPPER.readTree(SHIPPED.claimCatalogue().orElseThrow());
        edit.accept(file);
        Timeline t = SHIPPED;
        return new Timeline(t.rules(), t.ruleSets(), t.regimes(), t.ruleSources(), t.regimeSources(),
                t.disallowanceSetSources(), t.conditionSetSources(), t.components(), t.componentSources(), t.funds(),
                t.fundSources(), t.declarations(), t.declarationSources(), t.itrFormSources(), t.itrCells(),
                t.itrCellSources(), t.pipelineNodes(), t.coverage(), t.explanations(),
                Optional.of(MAPPER.writeValueAsString(file)), t.sections(), t.acts());
    }

    /** T22-12, I23: a rule the catalogue places nowhere fails the build, naming it. */
    @Test
    void verify_whenARuleIsPlacedNowhere_isRefusedByI23NamingIt() {
        // Given — the rounding rule taken off the not-a-claim list
        Timeline timeline = editedFile(file -> {
            ArrayNode notAClaim = (ArrayNode) file.path("notAClaim");
            for (int i = 0; i < notAClaim.size(); i++) {
                if (notAClaim.get(i).path("rule").asText().equals("RATE-ROUND-PAYABLE")) {
                    notAClaim.remove(i);
                    return;
                }
            }
        });

        // When / Then
        assertThatThrownBy(() -> ClaimCatalogue.of(timeline).verify(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("I23 — RATE-ROUND-PAYABLE is not catalogued");
    }

    @Test
    void of_whenACataloguedRuleIsAlsoListed_isRefusedAsPlacedTwice() {
        // Given
        Timeline timeline = editedFile(file -> ((ArrayNode) file.path("notYetCatalogued")).add("DED-SCHEDULE-XV"));

        // When / Then
        assertThatThrownBy(() -> ClaimCatalogue.of(timeline)).hasMessageContaining("DED-SCHEDULE-XV is placed twice");
    }

    @Test
    void verify_whenAListedRuleIsNotInTheTimeline_isRefused() {
        // Given
        Timeline timeline = editedFile(file -> ((ArrayNode) file.path("notYetCatalogued")).add("DED-NO-SUCH-RULE"));

        // When / Then
        assertThatThrownBy(() -> ClaimCatalogue.of(timeline).verify(timeline))
                .hasMessageContaining("lists DED-NO-SUCH-RULE, which is not in the timeline");
    }

    @Test
    void of_whenANotAClaimReasonIsUnknown_isRefused() {
        // Given
        Timeline timeline = editedFile(file -> ((ObjectNode) file.path("notAClaim").path(0)).put("because", "OBVIOUS"));

        // When / Then
        assertThatThrownBy(() -> ClaimCatalogue.of(timeline)).isInstanceOf(PackValidationException.class)
                .hasMessageContaining("because");
    }

    @Test
    void construction_whenARuleOutsideTheCatalogueLacksItsRuleOrReason_isRefused() {
        // When / Then
        assertThatThrownBy(() -> new NotAClaim(null, NotAClaimReason.APPLIES_WITHOUT_A_CLAIM))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("its rule and why");
        assertThatThrownBy(() -> new NotAClaim(HOUSE_RENT, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("its rule and why");
    }

    /** A timeline with no catalogue file (a test fixture's) has nothing to cover, so I23 does not apply. */
    @Test
    void verify_whenTheTimelineHasNoCatalogue_checksNoCoverage() {
        // Given
        Timeline t = SHIPPED;
        Timeline bare = new Timeline(t.rules(), t.ruleSets(), t.regimes(), t.ruleSources(), t.regimeSources(),
                t.disallowanceSetSources(), t.conditionSetSources(), t.components(), t.componentSources(), t.funds(),
                t.fundSources(), t.declarations(), t.declarationSources(), t.itrFormSources(), t.itrCells(),
                t.itrCellSources(), t.pipelineNodes(), t.coverage(), t.explanations(), Optional.empty(), t.sections(),
                t.acts());

        // When / Then
        assertThatCode(() -> ClaimCatalogue.of(bare).verify(bare)).doesNotThrowAnyException();
    }

    /** W-117d's hand: every one of the timeline's rules is placed once — 38 catalogued, 38 not yet, 168 not a claim (W-117e moved three). */
    @Test
    void of_whenTheShippedCatalogueIsRead_placesEveryRuleOnce() {
        // When
        ClaimCatalogue catalogue = ClaimCatalogue.of(SHIPPED);

        // Then
        assertThat(catalogue.notYetCatalogued()).hasSize(38);
        assertThat(catalogue.notAClaim()).hasSize(168);
        assertThat(SHIPPED.rules()).hasSize(244);
    }

    private static ObjectNode step(JsonNode entries, int entry, int index) {
        return (ObjectNode) entries.path(entry).path("steps").path(index);
    }

    private static ClaimCatalogueEntry shipped(RuleId rule) {
        return ClaimCatalogue.of(SHIPPED).entries().stream().filter(entry -> entry.rule().equals(rule)).findFirst()
                .orElseThrow();
    }

    @Test
    void verify_whenTheShippedCatalogueIsChecked_passesWithItsFourteenEntries() {
        // Given
        ClaimCatalogue catalogue = ClaimCatalogue.of(SHIPPED);

        // When / Then
        assertThatCode(() -> catalogue.verify(SHIPPED)).doesNotThrowAnyException();
        assertThat(catalogue.entries()).extracting(ClaimCatalogueEntry::rule).containsExactly(HOUSE_RENT, DONATION,
                INVESTMENTS, OWN_PENSION, HEALTH, HOME_LOAN_INTEREST, LEAVE_TRAVEL, TAX_ON_EMPLOYMENT, SAVINGS_INTEREST,
                SENIOR_DEPOSIT_INTEREST, DISABILITY, DISABLED_DEPENDANT, EMPLOYER_PENSION, DONATIONS);
    }

    /**
     * W-117e: the rules that deduct a donation are one claim, priced together, since s.133(2)'s qualifying limit caps
     * both rates (C1a); no employer condition in s.133 or the 1961 s.80G (sources/2026-11-14-claim-channel.md C24, C25).
     */
    @Test
    void of_whenTheDonationsEntryIsRead_isEmployerOptionalAndCoversBothRatesAndTheLimit() {
        // When
        ClaimCatalogueEntry entry = shipped(DONATIONS);

        // Then
        assertThat(entry.channel()).isEqualTo(ClaimChannel.EMPLOYER_OPTIONAL);
        assertThat(entry.ifMissingFromForm16()).isEqualTo(AbsenceFromForm16.CLAIMABLE_IN_RETURN);
        assertThat(entry.section()).isEqualTo("SEC-DONATIONS");
        assertThat(entry.chapterViiiPart()).contains(ChapterViiiPart.B);
        assertThat(entry.covers()).extracting(RuleId::value).containsExactly("DED-DONATION-HALF", "DED-DONATION-QUALIFYING");
        assertThat(entry.basis().provision1961().reference()).isEqualTo("s.80G(1), (2), (4)");
        assertThat(entry.basis().provision2025().reference()).isEqualTo("s.133(1), (2); s.392(5)(b)");
        assertThat(entry.guidance().steps().get(0).instruction()).contains("{{DED-DONATION-CASH.atMost}}");
        assertThat(entry.guidance().steps()).last().satisfies(last -> assertThat(last.by()).isEqualTo(ClaimDeadline.RETURN_DUE));
    }

    /**
     * W-117c's five entries, each on the channel its provisions set (sources/2026-11-14-claim-channel.md C17 – C23):
     * savings interest may go through the employer (s.392(4)(a)(iv), r.205(2) Sl. 4; C1a); a disability claim needs the
     * certificate furnished with the return (ss.154(2), 127(6); C2a); the employer's contribution is the employer's to
     * make (s.124(1); C3a). Section, Part and both provisions as read.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            DED-SAVINGS-INTEREST        | SEC-SAVINGS-INTEREST        | D | EMPLOYER_OPTIONAL    | CLAIMABLE_IN_RETURN | s.80TTA     | s.153(1)(a), (c), (2)(a); s.392(4)(a)(iv), (5)(b)
            DED-DEPOSIT-INTEREST-SENIOR | SEC-DEPOSIT-INTEREST-SENIOR | D | EMPLOYER_OPTIONAL    | CLAIMABLE_IN_RETURN | s.80TTB     | s.153(1)(b), (2)(b); s.392(4)(a)(iv), (5)(b)
            DED-DISABILITY              | SEC-DISABILITY              | E | RETURN_WITH_DOCUMENT | CLAIMABLE_IN_RETURN | s.80U       | s.154
            DED-DISABLED-DEPENDANT      | SEC-DISABLED-DEPENDANT      | B | RETURN_WITH_DOCUMENT | CLAIMABLE_IN_RETURN | s.80DD      | s.127
            DED-NPS-EMPLOYER-OTHER      | SEC-PENSION-SCHEME          | B | EMPLOYER_PROVIDED    | NOTHING_TO_CLAIM    | s.80CCD(2)  | s.124(1), (2)
            """)
    void of_whenAReturnSideOrEmployerProvidedEntryIsRead_isOnTheChannelItsProvisionsSet(String rule, String section,
            String part, ClaimChannel channel, AbsenceFromForm16 verdict, String provision1961, String provision2025) {
        // When
        ClaimCatalogueEntry entry = shipped(new RuleId(rule));

        // Then
        assertThat(entry.channel()).isEqualTo(channel);
        assertThat(entry.ifMissingFromForm16()).isEqualTo(verdict);
        assertThat(entry.section()).isEqualTo(section);
        assertThat(entry.chapterViiiPart()).contains(ChapterViiiPart.valueOf(part));
        assertThat(entry.basis().provision1961().reference()).isEqualTo(provision1961);
        assertThat(entry.basis().provision1961().presence()).isEqualTo(Presence.PRESENT);
        assertThat(entry.basis().provision2025().reference()).isEqualTo(provision2025);
        assertThat(entry.basis().provision2025().presence()).isEqualTo(Presence.PRESENT);
        assertThat(entry.guidance().steps()).noneMatch(ClaimStep::warnsOfLateReturnBar).last()
                .satisfies(last -> assertThat(last.by()).isEqualTo(ClaimDeadline.RETURN_DUE));
    }

    /** C2a: ss.154(2)(a), (c) and 127(6) — a copy of the certificate goes with the return, and a lapsing one is renewed. */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"DED-DISABILITY", "DED-DISABLED-DEPENDANT"})
    void of_whenADisabilityEntryIsRead_furnishesTheCertificateWithTheReturnAndRenewsIt(String rule) {
        // When
        ClaimCatalogueEntry entry = shipped(new RuleId(rule));

        // Then
        assertThat(entry.guidance().steps()).anySatisfy(step -> assertThat(step.instruction())
                .contains("certificate", "with your return"));
        assertThat(entry.guidance().steps()).anySatisfy(step -> assertThat(step.instruction()).contains("reassessed"));
        assertThat(entry.guidance().evidence()).anySatisfy(evidence -> assertThat(evidence.closedBy())
                .contains("with your return"));
    }

    /** T22-1's shape for the pilot: an employer-optional claim missing from Form 16 is claimable in the return. */
    @Test
    void of_whenHouseRentAllowanceIsRead_isClaimableInTheReturnOnTheScheduleAndTheEmployersDuty() {
        // When
        ClaimCatalogueEntry entry = shipped(HOUSE_RENT);

        // Then — Sch. III Sl. 11 and s.392(5)(b), Gazette 2025/265620 pp.506, 397; 1961 s.10(13A), thc.nic.in p.77
        assertThat(entry.channel()).isEqualTo(ClaimChannel.EMPLOYER_OPTIONAL);
        assertThat(entry.ifMissingFromForm16()).isEqualTo(AbsenceFromForm16.CLAIMABLE_IN_RETURN);
        assertThat(entry.chapterViiiPart()).isEmpty();
        assertThat(entry.covers()).isEmpty();
        assertThat(entry.section()).isEqualTo("SEC-HOUSE-RENT-ALLOWANCE");
        assertThat(entry.basis().provision2025().reference())
                .isEqualTo("s.11(1) with Sch. III Table Sl. 11; s.392(5)(b)");
        assertThat(entry.basis().provision1961().reference()).isEqualTo("s.10(13A)");
        assertThat(entry.basis().provision1961().presence()).isEqualTo(Presence.PRESENT);
        assertThat(entry.guidance().steps()).hasSize(4).last()
                .satisfies(last -> assertThat(last.by()).isEqualTo(ClaimDeadline.RETURN_DUE));
    }

    /** T22-10, T22-11: the landlord step takes its threshold from the rule, and is the landlord's by the cut-off. */
    @Test
    void of_whenHouseRentAllowanceIsRead_asksTheLandlordAboveTheRulesThresholdByTheEmployersCutOff() {
        // When
        ClaimStep landlord = shipped(HOUSE_RENT).guidance().steps().get(1);

        // Then
        assertThat(landlord.order()).isEqualTo(2);
        assertThat(landlord.actor()).isEqualTo(ClaimActor.LANDLORD);
        assertThat(landlord.by()).isEqualTo(ClaimDeadline.EMPLOYER_PROOF_CUT_OFF);
        assertThat(landlord.instruction()).contains("{{SAL-HOUSE-RENT-LANDLORD-PAN.exceeds}}");
    }

    /** T22-2: a donation the donee must report is not allowable until it does — never "claim it in the return". */
    @Test
    void of_whenTheReportedDonationIsRead_isNotAllowableUnlessReported() {
        // When
        ClaimCatalogueEntry entry = shipped(DONATION);

        // Then — s.133(6), Gazette 2025/265620 p.169; s.133 sits in Part B (heading p.159)
        assertThat(entry.channel()).isEqualTo(ClaimChannel.THIRD_PARTY_REPORTED);
        assertThat(entry.ifMissingFromForm16()).isEqualTo(AbsenceFromForm16.NOT_ALLOWABLE_UNLESS_REPORTED);
        assertThat(entry.chapterViiiPart()).contains(ChapterViiiPart.B);
        assertThat(entry.section()).isEqualTo("SEC-DONATIONS");
        assertThat(entry.basis().provision2025().reference()).isEqualTo("s.133(6)");
        assertThat(entry.basis().provision1961().reference()).isEqualTo("s.80G(5D) Explanation 2A");
        assertThat(entry.guidance().appliesTo()).isPresent();
        assertThat(entry.guidance().steps()).hasSize(3).noneMatch(ClaimStep::warnsOfLateReturnBar);
    }

    /**
     * T22-1 for W-117b's six entries: none of the provisions read sets a condition about the employer, so each is
     * employer-optional and claimable in the return. Part, section and both provisions as read (sources/2026-11-14-claim-channel.md C9 – C16).
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', nullValues = "-", textBlock = """
            DED-SCHEDULE-XV               | SEC-INVESTMENT-DEDUCTION      | B | s.80C       | s.123 with Sch. XV; s.392(5)(b)
            DED-NPS-OWN                   | SEC-PENSION-SCHEME            | B | s.80CCD(1B) | s.124(3) – (5), (10); s.392(5)(b)
            DED-HEALTH-FAMILY             | SEC-HEALTH-INSURANCE          | B | s.80D       | s.126; s.392(5)(b)
            HP-OWN-RESIDENCE-INTEREST-CAP | SEC-HOUSE-PROPERTY-DEDUCTIONS | - | s.24(b)     | s.22(1)(b), (2), (4); s.392(4)(a)
            SAL-LEAVE-TRAVEL-CONCESSION   | SEC-LEAVE-TRAVEL-CONCESSION   | - | s.10(5)     | s.11(1) with Sch. III Table Sl. 8; s.392(5)(b)
            SAL-TAX-ON-EMPLOYMENT         | SEC-DEDUCTIONS-FROM-SALARY    | - | s.16(iii)   | s.19(1) Table Sl. 1; s.392(5)(b)
            """)
    void of_whenAnEmployerOptionalEntryIsRead_isClaimableInTheReturnOnItsReadBasis(String rule, String section,
            String part, String provision1961, String provision2025) {
        // When
        ClaimCatalogueEntry entry = shipped(new RuleId(rule));

        // Then
        assertThat(entry.channel()).isEqualTo(ClaimChannel.EMPLOYER_OPTIONAL);
        assertThat(entry.ifMissingFromForm16()).isEqualTo(AbsenceFromForm16.CLAIMABLE_IN_RETURN);
        assertThat(entry.section()).isEqualTo(section);
        assertThat(entry.chapterViiiPart()).isEqualTo(Optional.ofNullable(part).map(ChapterViiiPart::valueOf));
        assertThat(entry.basis().provision1961().reference()).isEqualTo(provision1961);
        assertThat(entry.basis().provision1961().presence()).isEqualTo(Presence.PRESENT);
        assertThat(entry.basis().provision2025().reference()).isEqualTo(provision2025);
        assertThat(entry.basis().provision2025().presence()).isEqualTo(Presence.PRESENT);
        assertThat(entry.guidance().steps()).noneMatch(ClaimStep::warnsOfLateReturnBar).last()
                .satisfies(last -> assertThat(last.by()).isEqualTo(ClaimDeadline.RETURN_DUE));
    }

    /** C1a: one claim, several rules — the entry speaks for the section's other rules (W-117b plan, probe). */
    @Test
    void of_whenAnEntryIsOneClaimOfSeveralRules_coversTheRest() {
        // When / Then — health has ten rules, home-loan interest five, leave travel four
        assertThat(shipped(HEALTH).covers()).extracting(RuleId::value).containsExactly("DED-HEALTH-CASH-PERMITTED",
                "DED-HEALTH-CHECKUP", "DED-HEALTH-FAMILY-SENIOR", "DED-HEALTH-INSURANCE-LIMB", "DED-HEALTH-INSURANCE-PAID",
                "DED-HEALTH-MEDICAL-LIMB", "DED-HEALTH-PARENTS", "DED-HEALTH-PARENTS-SENIOR", "DED-HEALTH-SPREAD");
        assertThat(shipped(HOME_LOAN_INTEREST).covers()).extracting(RuleId::value).containsExactly("HP-OWN-RESIDENCE",
                "HP-OWN-RESIDENCE-HIGHER-LIMIT", "HP-OWN-RESIDENCE-INTEREST", "HP-OWN-RESIDENCE-PRE-CONSTRUCTION");
        assertThat(shipped(LEAVE_TRAVEL).covers()).extracting(RuleId::value).containsExactly(
                "SAL-LEAVE-TRAVEL-CHILDREN", "SAL-LEAVE-TRAVEL-DISTANCE-RATE", "SAL-LEAVE-TRAVEL-JOURNEYS");
        assertThat(shipped(TAX_ON_EMPLOYMENT).covers()).isEmpty();
        assertThat(shipped(DISABILITY).covers()).extracting(RuleId::value).containsExactly("DED-DISABILITY-SEVERE");
        assertThat(shipped(DISABLED_DEPENDANT).covers()).extracting(RuleId::value).containsExactly("DED-DEPENDANT",
                "DED-DEPENDANT-OWN-CLAIM", "DED-DISABLED-DEPENDANT-SEVERE");
        assertThat(shipped(EMPLOYER_PENSION).covers()).extracting(RuleId::value).containsExactly(
                "DED-NPS-EMPLOYER-GOVT", "DED-NPS-EMPLOYER-NEW-TAX-REGIME");
        assertThat(shipped(SAVINGS_INTEREST).covers()).isEmpty();
    }

    /** C2a: home-loan interest names its limits from the cap's definition fields, never as typed figures. */
    @Test
    void of_whenHomeLoanInterestIsRead_namesItsLimitsByPlaceholderAndAsksTheLender() {
        // When
        ClaimGuidance guidance = shipped(HOME_LOAN_INTEREST).guidance();

        // Then — s.22(2)(a)(ii), (4): the certificate is the lender's
        assertThat(guidance.steps().get(0).actor()).isEqualTo(ClaimActor.LENDER);
        assertThat(guidance.steps().get(1).instruction()).contains("{{HP-OWN-RESIDENCE-INTEREST-CAP.conditionsMet}}",
                "{{HP-OWN-RESIDENCE-INTEREST-CAP.anyOtherCase}}", "{{HP-OWN-RESIDENCE-INTEREST-CAP.aggregate}}");
        assertThat(guidance.askWhom()).extracting(ClaimRequest::whom).contains(ClaimActor.LENDER);
    }

    /** C4a: leave travel is claimable only on assistance the employer paid, and says so before any other step. */
    @Test
    void of_whenLeaveTravelIsRead_appliesToAssistanceTheEmployerPaid() {
        // When
        ClaimGuidance guidance = shipped(LEAVE_TRAVEL).guidance();

        // Then — Sch. III Table Sl. 8(a): "received by, or due to, such individual ... from his employer"
        assertThat(guidance.appliesTo()).hasValueSatisfying(text -> assertThat(text).contains("nothing to claim"));
        assertThat(guidance.steps().get(0).instruction()).contains("nothing to claim");
    }

    @Test
    void of_whenARuleIsCoveredByTwoEntries_isRefused() {
        // Given — red check 1
        Timeline timeline = edited(entries ->
                ((ArrayNode) entries.path(LEAVE_TRAVEL_ENTRY).path("covers")).add("DED-HEALTH-SPREAD"));

        // When / Then
        assertThatThrownBy(() -> ClaimCatalogue.of(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("one entry for a rule, but DED-HEALTH-SPREAD has two");
    }

    @Test
    void of_whenAnEntryCoversItsOwnRule_isRefused() {
        // Given
        Timeline timeline = edited(entries ->
                ((ObjectNode) entries.path(HOUSE_RENT_ENTRY)).putArray("covers").add("SAL-HOUSE-RENT-ALLOWANCE"));

        // When / Then
        assertThatThrownBy(() -> ClaimCatalogue.of(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("one entry for a rule, but SAL-HOUSE-RENT-ALLOWANCE has two");
    }

    @Test
    void verify_whenAnEntryCoversARuleNotInTheTimeline_isRefused() {
        // Given
        Timeline timeline = edited(entries ->
                ((ArrayNode) entries.path(HEALTH_ENTRY).path("covers")).add("DED-NOT-A-RULE"));

        // When / Then
        assertThatThrownBy(() -> ClaimCatalogue.of(timeline).verify(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("DED-HEALTH-FAMILY covers rule DED-NOT-A-RULE, which is not in the timeline");
    }

    @Test
    void of_whenTheHealthEntryIsMarkedPartC_isRefusedByI24() {
        // Given — red check 2: s.126 sits in Part B (heading p.159), so no step warns of the late-return bar
        Timeline timeline = edited(entries -> ((ObjectNode) entries.path(HEALTH_ENTRY)).put("chapterViiiPart", "C"));

        // When / Then
        assertThatThrownBy(() -> ClaimCatalogue.of(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("I24")
                .hasMessageContaining("DED-HEALTH-FAMILY is in Part C");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Keep 12 receipts.",
        "Keep a receipt if rent is above ₹ one lakh.",
        "Give them by 31 January.",
        "Keep receipts for {{SAL-HOUSE-RENT-LANDLORD-PAN}}."
    })
    void verify_whenAStepTypesAFigure_isRefusedNamingTheStep(String instruction) {
        // Given
        Timeline timeline = edited(entries -> step(entries, HOUSE_RENT_ENTRY, 0).put("instruction", instruction));

        // When / Then
        assertThatThrownBy(() -> ClaimCatalogue.of(timeline).verify(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("SAL-HOUSE-RENT-ALLOWANCE steps[1].instruction types");
    }

    @Test
    void verify_whenAPlaceholderNamesARuleNotInTheTimeline_isRefused() {
        // Given
        Timeline timeline = edited(entries -> step(entries, HOUSE_RENT_ENTRY, 1)
                .put("instruction", "Ask if rent is more than {{SAL-NOT-A-RULE.exceeds}}."));

        // When / Then
        assertThatThrownBy(() -> ClaimCatalogue.of(timeline).verify(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("steps[2].instruction names rule SAL-NOT-A-RULE, which is not in the timeline");
    }

    @Test
    void verify_whenAPlaceholderNamesAFieldTheRuleDoesNotDefine_isRefused() {
        // Given
        Timeline timeline = edited(entries -> step(entries, HOUSE_RENT_ENTRY, 1)
                .put("instruction", "Ask if rent is more than {{SAL-HOUSE-RENT-LANDLORD-PAN.limit}}."));

        // When / Then
        assertThatThrownBy(() -> ClaimCatalogue.of(timeline).verify(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("names SAL-HOUSE-RENT-LANDLORD-PAN.limit, which not every version of the rule defines");
    }

    @Test
    void verify_whenAnEntryIsForARuleOrASectionTheTimelineLacks_isRefusedNamingBoth() {
        // Given
        Timeline timeline = edited(entries -> {
            ((ObjectNode) entries.path(HOUSE_RENT_ENTRY)).put("rule", "SAL-NOT-A-RULE");
            ((ObjectNode) entries.path(DONATION_ENTRY)).put("section", "SEC-NOT-A-SECTION");
        });

        // When / Then
        assertThatThrownBy(() -> ClaimCatalogue.of(timeline).verify(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("SAL-NOT-A-RULE is for a rule that is not in the timeline")
                .hasMessageContaining("DED-DONATION-UNREPORTED names section SEC-NOT-A-SECTION");
    }

    @Test
    void of_whenAPartCEntryHasNoLateReturnWarning_isRefusedByI24() {
        // Given
        Timeline timeline = edited(entries -> ((ObjectNode) entries.path(DONATION_ENTRY)).put("chapterViiiPart", "C"));

        // When / Then
        assertThatThrownBy(() -> ClaimCatalogue.of(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("I24")
                .hasMessageContaining("DED-DONATION-UNREPORTED is in Part C");
    }

    @Test
    void of_whenAPartCEntryWarnsOfTheLateReturnBar_isAccepted() {
        // Given
        Timeline timeline = edited(entries -> {
            ((ObjectNode) entries.path(DONATION_ENTRY)).put("chapterViiiPart", "C");
            step(entries, DONATION_ENTRY, 2).put("warnsOfLateReturnBar", true);
        });

        // When / Then
        assertThatCode(() -> ClaimCatalogue.of(timeline).verify(timeline)).doesNotThrowAnyException();
    }

    @Test
    void of_whenAnEntryOutsidePartCWarnsOfTheLateReturnBar_isRefusedByI24() {
        // Given — Part B, and an entry with no Part at all
        Timeline partB = edited(entries -> step(entries, DONATION_ENTRY, 2).put("warnsOfLateReturnBar", true));
        Timeline noPart = edited(entries -> step(entries, HOUSE_RENT_ENTRY, 3).put("warnsOfLateReturnBar", true));

        // When / Then
        assertThatThrownBy(() -> ClaimCatalogue.of(partB))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("I24")
                .hasMessageContaining("DED-DONATION-UNREPORTED is not in Part C");
        assertThatThrownBy(() -> ClaimCatalogue.of(noPart))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("SAL-HOUSE-RENT-ALLOWANCE is not in Part C");
    }

    @Test
    void of_whenEvidenceNamesNothingThatClosesIt_isRefusedByI25() {
        // Given
        Timeline timeline = edited(entries ->
                ((ObjectNode) entries.path(HOUSE_RENT_ENTRY).path("evidence").path(0)).remove("closedBy"));

        // When / Then
        assertThatThrownBy(() -> ClaimCatalogue.of(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("entries[0].evidence[0]")
                .hasMessageContaining("I25")
                .hasMessageContaining("Rent receipts");
    }

    @Test
    void of_whenAStepsDeadlineIsNotOneTheCalendarKnows_isRefusedNamingTheSet() {
        // Given
        Timeline timeline = edited(entries -> step(entries, HOUSE_RENT_ENTRY, 2).put("by", "NEXT_WEEK"));

        // When / Then — I26: the closed set of W-117a C1a
        assertThatThrownBy(() -> ClaimCatalogue.of(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("entries[0].steps[2].by")
                .hasMessageContaining("[TAX_YEAR_END, RETURN_DUE, EMPLOYER_PROOF_CUT_OFF, NONE]");
    }

    @Test
    void of_whenTheStepsSkipANumber_isRefused() {
        // Given
        Timeline timeline = edited(entries -> step(entries, DONATION_ENTRY, 2).put("order", 4));

        // When / Then
        assertThatThrownBy(() -> ClaimCatalogue.of(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("numbered from 1 without a gap, but were [1, 2, 4]");
    }

    @Test
    void of_whenARuleHasTwoEntries_isRefused() {
        // Given
        Timeline timeline = edited(entries ->
                ((ObjectNode) entries.path(DONATION_ENTRY)).put("rule", "SAL-HOUSE-RENT-ALLOWANCE"));

        // When / Then
        assertThatThrownBy(() -> ClaimCatalogue.of(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("one entry for a rule, but SAL-HOUSE-RENT-ALLOWANCE has two");
    }

    @Test
    void of_whenAStepIsNumberedBelowOneOrAnEntryHasNoSteps_isRefused() {
        // Given
        Timeline numberedNil = edited(entries -> step(entries, HOUSE_RENT_ENTRY, 0).put("order", 0));
        Timeline noSteps = edited(entries -> ((ObjectNode) entries.path(DONATION_ENTRY)).putArray("steps"));

        // When / Then
        assertThatThrownBy(() -> ClaimCatalogue.of(numberedNil))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("entries[0].steps[0]")
                .hasMessageContaining("order starts at 1, but was 0");
        assertThatThrownBy(() -> ClaimCatalogue.of(noSteps))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("entries[1]")
                .hasMessageContaining("A claim needs at least one step");
    }

    @Test
    void of_whenAMistakeIsBlankOrNotASentence_isRefused() {
        // Given
        Timeline blank = edited(entries -> ((ObjectNode) entries.path(HOUSE_RENT_ENTRY)).putArray("mistakes").add(" "));
        Timeline figure = edited(entries -> ((ObjectNode) entries.path(HOUSE_RENT_ENTRY)).putArray("mistakes").add(7));

        // When / Then
        assertThatThrownBy(() -> ClaimCatalogue.of(blank))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("A common mistake cannot be blank");
        assertThatThrownBy(() -> ClaimCatalogue.of(figure))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("entries[0].mistakes[0]: a common mistake is a sentence, but was 7");
    }

    /** The parts refuse to be built without what they are for, whoever builds them — not only the file's reader. */
    @Test
    void construction_whenAPartLacksWhatItIsFor_isRefused() {
        // Given
        Provision act = Provision.present("Income-tax Act, 2025", "s.133(6)");
        ClaimStep step = new ClaimStep(1, "Keep the receipt.", ClaimActor.YOU, ClaimDeadline.NONE, Optional.empty(), false);
        List<RuleId> none = List.of();
        ClaimGuidance guidance = new ClaimGuidance(Optional.empty(), List.of(step), List.of(), List.of(), List.of());
        ClaimBasis basis = new ClaimBasis(act, act);

        // When / Then
        assertThatThrownBy(() -> new ClaimStep(1, " ", ClaimActor.YOU, ClaimDeadline.NONE, Optional.empty(), false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("needs an instruction");
        assertThatThrownBy(() -> new ClaimStep(1, "Keep it.", null, ClaimDeadline.NONE, Optional.empty(), false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("an actor");
        assertThatThrownBy(() -> new ClaimRequest(null, "Its certificate."))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("whom to ask");
        assertThatThrownBy(() -> new ClaimRequest(ClaimActor.LENDER, " "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("what for");
        assertThatThrownBy(() -> new ClaimEvidence(" ", "A receipt."))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("needs a name");
        assertThatThrownBy(() -> new ClaimBasis(null, act))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("both Acts");
        assertThatThrownBy(() -> new ClaimBasis(act, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("both Acts");
        assertThatThrownBy(() -> new ClaimGuidance(Optional.empty(), List.of(step), null, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("empty lists for none");
        assertThatThrownBy(() -> new ClaimCatalogueEntry(HOUSE_RENT, none, " ", ClaimChannel.RETURN_ONLY,
                Optional.empty(), AbsenceFromForm16.CLAIMABLE_IN_RETURN, basis, guidance))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("needs a rule, covers");
        assertThatThrownBy(() -> new ClaimCatalogueEntry(HOUSE_RENT, null, "SEC-DONATIONS", ClaimChannel.RETURN_ONLY,
                Optional.empty(), AbsenceFromForm16.CLAIMABLE_IN_RETURN, basis, guidance))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("an empty list for none");
        assertThatThrownBy(() -> new ClaimCatalogueEntry(HOUSE_RENT, none, "SEC-DONATIONS", ClaimChannel.RETURN_ONLY,
                Optional.empty(), AbsenceFromForm16.CLAIMABLE_IN_RETURN, basis, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("guidance");
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void compile_whenAYearHoldsEveryLeadRule_keepsEveryEntryWithoutItsNotes(int year) {
        // When
        JsonNode compiled = MAPPER.readTree(
                RulePackCompiler.compile(SHIPPED, TaxYearId.of(year)).files().get(ClaimCatalogue.FILE));

        // Then — ordered by rule id; note fields are not law or content, so the pack drops them
        assertThat(compiled.path("entries")).extracting(entry -> entry.path("rule").asText())
                .containsExactly("DED-DEPOSIT-INTEREST-SENIOR", "DED-DISABILITY", "DED-DISABLED-DEPENDANT",
                        "DED-DONATION-UNREPORTED", "DED-DONATION-WHOLE", "DED-HEALTH-FAMILY", "DED-NPS-EMPLOYER-OTHER", "DED-NPS-OWN",
                        "DED-SAVINGS-INTEREST", "DED-SCHEDULE-XV", "HP-OWN-RESIDENCE-INTEREST-CAP",
                        "SAL-HOUSE-RENT-ALLOWANCE", "SAL-LEAVE-TRAVEL-CONCESSION", "SAL-TAX-ON-EMPLOYMENT");
        assertThat(compiled.path("entries").path(0).has("_sources")).isFalse();
        assertThat(compiled.path("entries").path(11).has("covers")).isFalse();
    }

    /** A pack names no rule it does not carry: r.278(1)(iii)(B)'s rate begins in TY 2026-27 (its validity). */
    @ParameterizedTest(name = "TY {0}")
    @CsvSource({
        "2025, SAL-LEAVE-TRAVEL-CHILDREN|SAL-LEAVE-TRAVEL-JOURNEYS",
        "2026, SAL-LEAVE-TRAVEL-CHILDREN|SAL-LEAVE-TRAVEL-DISTANCE-RATE|SAL-LEAVE-TRAVEL-JOURNEYS"
    })
    void compile_whenACoveredRuleIsNotInTheYear_dropsItFromThatYearsCovers(int year, String covered) {
        // When
        JsonNode compiled = MAPPER.readTree(
                RulePackCompiler.compile(SHIPPED, TaxYearId.of(year)).files().get(ClaimCatalogue.FILE));

        // Then
        JsonNode leaveTravel = compiled.path("entries").path(12);
        assertThat(leaveTravel.path("rule").asText()).isEqualTo("SAL-LEAVE-TRAVEL-CONCESSION");
        assertThat(leaveTravel.path("covers")).extracting(JsonNode::asText).containsExactly(covered.split("\\|"));
    }

    @Test
    void compile_whenTheCatalogueTypesAFigure_isRefusedBeforeAnyPackIsBuilt() {
        // Given
        Timeline timeline = edited(entries -> step(entries, DONATION_ENTRY, 0).put("instruction", "Ask within 7 days."));

        // When / Then
        assertThatThrownBy(() -> RulePackCompiler.compile(timeline, TaxYearId.of(2026)))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("DED-DONATION-UNREPORTED steps[1].instruction types \"7\"");
    }

    /** C2a: a year's entry is whole — its section's names beside it and each placeholder's figure for the year. */
    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void compile_whenAnEntryNamesAFigure_carriesItsSectionsNamesAndTheYearsFigure(int year) {
        // When
        JsonNode compiled = MAPPER.readTree(
                RulePackCompiler.compile(SHIPPED, TaxYearId.of(year)).files().get(ClaimCatalogue.FILE));

        // Then — SAL-HOUSE-RENT-LANDLORD-PAN.exceeds is 100000 in both years' versions; HP-OWN-RESIDENCE-INTEREST-CAP's
        // conditionsMet and aggregate 200000, anyOtherCase 30000 (each version's definition)
        JsonNode houseRent = compiled.path("entries").path(11);
        assertThat(houseRent.path("rule").asString()).isEqualTo("SAL-HOUSE-RENT-ALLOWANCE");
        assertThat(houseRent.path("plainName").asString()).isEqualTo("House Rent Allowance");
        assertThat(houseRent.path("summary").asString()).startsWith("If your salary includes a house rent allowance");
        assertThat(houseRent.path("figures").path("SAL-HOUSE-RENT-LANDLORD-PAN.exceeds").asString()).isEqualTo("100000");
        JsonNode homeLoan = compiled.path("entries").path(10).path("figures");
        assertThat(homeLoan.path("HP-OWN-RESIDENCE-INTEREST-CAP.conditionsMet").asString()).isEqualTo("200000");
        assertThat(homeLoan.path("HP-OWN-RESIDENCE-INTEREST-CAP.anyOtherCase").asString()).isEqualTo("30000");
        assertThat(homeLoan.path("HP-OWN-RESIDENCE-INTEREST-CAP.aggregate").asString()).isEqualTo("200000");
        assertThat(compiled.path("entries").path(0).path("figures").isEmpty()).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"completionYearsAfterBorrowingYear", "type"})
    void verify_whenAPlaceholderNamesAFieldThatIsNotAnAmount_isRefused(String field) {
        // Given — a count of years (a number), and the definition's type (text that is not a figure)
        Timeline timeline = edited(entries -> step(entries, HOUSE_RENT_ENTRY, 1).put("instruction",
                "Ask after {{HP-OWN-RESIDENCE-INTEREST-CAP." + field + "}}."));

        // When / Then
        assertThatThrownBy(() -> ClaimCatalogue.of(timeline).verify(timeline))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining(field + ", which not every version of the rule defines as an amount");
    }

    @Test
    void compile_whenAPlaceholdersRuleHasNoVersionInTheYear_isRefused() {
        // Given — the landlord threshold kept for TY 2025-26 only
        Timeline t = SHIPPED;
        RuleId landlord = new RuleId("SAL-HOUSE-RENT-LANDLORD-PAN");
        RuleRow row = t.rules().get(landlord);
        Map<RuleId, RuleRow> rules = new HashMap<>(t.rules());
        rules.put(landlord, new RuleRow(row.id(), row.axis(), row.versions().subList(0, 1), row.headProvenance()));
        Timeline timeline = new Timeline(rules, t.ruleSets(), t.regimes(), t.ruleSources(), t.regimeSources(),
                t.disallowanceSetSources(), t.conditionSetSources(), t.components(), t.componentSources(), t.funds(),
                t.fundSources(), t.declarations(), t.declarationSources(), t.itrFormSources(), t.itrCells(),
                t.itrCellSources(), t.pipelineNodes(), t.coverage(), t.explanations(), t.claimCatalogue(), t.sections(),
                t.acts());

        // When / Then
        assertThatThrownBy(() -> RulePackCompiler.compile(timeline, TaxYearId.of(2026)))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("names SAL-HOUSE-RENT-LANDLORD-PAN.exceeds, which 2026-27 gives as []");
    }

    @Test
    void construction_whenAPackHoldsAClaimForARuleItLacksOrAClaimLacksItsNames_isRefused() {
        // Given
        TaxYearId year = TaxYearId.of(2026);
        RulePack pack = new ClasspathRulePackProvider("rulepacks", Set.of(year),
                new JsonPackAssembler(LockedInstruments.of(year))).load(year);
        CompiledClaim shipped = pack.claims().getFirst();
        ClaimCatalogueEntry entry = shipped.entry();
        CompiledClaim stranger = new CompiledClaim(new ClaimCatalogueEntry(entry.rule(), List.of(new RuleId("DED-NOT-A-RULE")),
                entry.section(), entry.channel(), entry.chapterViiiPart(), entry.ifMissingFromForm16(), entry.basis(),
                entry.guidance()), shipped.plainName(), shipped.summary(), Map.of());

        // When / Then
        assertThatThrownBy(() -> new RulePack(pack.taxYear(), pack.layers(), pack.instruments(), pack.rules(),
                pack.ruleSets(), pack.components(), pack.regimes(), pack.disallowanceSets(), pack.conditionSets(),
                pack.funds(), pack.declarations(), pack.itrForms(), pack.explanations(), List.of(stranger)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has a claim for DED-NOT-A-RULE, which is not a rule of the year");
        assertThatThrownBy(() -> new RulePack(pack.taxYear(), pack.layers(), pack.instruments(), pack.rules(),
                pack.ruleSets(), pack.components(), pack.regimes(), pack.disallowanceSets(), pack.conditionSets(),
                pack.funds(), pack.declarations(), pack.itrForms(), pack.explanations(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must carry its claims");
        assertThatThrownBy(() -> new CompiledClaim(entry, " ", shipped.summary(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("plain name and summary");
        assertThatThrownBy(() -> new CompiledClaim(null, shipped.plainName(), shipped.summary(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("needs its entry");
        assertThatThrownBy(() -> new CompiledClaim(entry, null, shipped.summary(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("plain name and summary");
        assertThatThrownBy(() -> new CompiledClaim(entry, shipped.plainName(), null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("plain name and summary");
        assertThatThrownBy(() -> new CompiledClaim(entry, shipped.plainName(), " ", Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("plain name and summary");
        assertThatThrownBy(() -> new CompiledClaim(entry, shipped.plainName(), shipped.summary(), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("an empty map for none");
        assertThat(pack.claims()).extracting(claim -> claim.entry().rule().value()).contains("SAL-HOUSE-RENT-ALLOWANCE");
    }
}
