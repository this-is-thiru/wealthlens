package com.thiru.wealthlens.taxengine.rules.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.temporal.Presence;
import com.thiru.wealthlens.taxengine.rules.temporal.Provision;
import com.thiru.wealthlens.taxengine.rules.timeline.RulePackCompiler;
import com.thiru.wealthlens.taxengine.rules.timeline.Timeline;
import com.thiru.wealthlens.taxengine.rules.timeline.TimelineReader;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The claim catalogue's frame and its two pilots (W-117a, D-699): the shipped entries say what {@code DR 22} asks of them
 * (T22-1, T22-2, T22-10, T22-11 as content checks), and each invariant refuses a deliberate violation.
 *
 * <p>Expected values are the plan's adopted content and the provisions read in the Gazette, typed here by hand.
 */
class ClaimCatalogueTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Timeline SHIPPED = TimelineReader.read(Path.of("src/main/resources/rulepacks/timeline"));
    private static final RuleId HOUSE_RENT = new RuleId("SAL-HOUSE-RENT-ALLOWANCE");
    private static final RuleId DONATION = new RuleId("DED-DONATION-UNREPORTED");
    private static final int HOUSE_RENT_ENTRY = 0;
    private static final int DONATION_ENTRY = 1;

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

    private static ObjectNode step(JsonNode entries, int entry, int index) {
        return (ObjectNode) entries.path(entry).path("steps").path(index);
    }

    private static ClaimCatalogueEntry shipped(RuleId rule) {
        return ClaimCatalogue.of(SHIPPED).entries().stream().filter(entry -> entry.rule().equals(rule)).findFirst()
                .orElseThrow();
    }

    @Test
    void verify_whenTheShippedCatalogueIsChecked_passesWithItsTwoPilots() {
        // Given
        ClaimCatalogue catalogue = ClaimCatalogue.of(SHIPPED);

        // When / Then
        assertThatCode(() -> catalogue.verify(SHIPPED)).doesNotThrowAnyException();
        assertThat(catalogue.entries()).extracting(ClaimCatalogueEntry::rule).containsExactly(HOUSE_RENT, DONATION);
    }

    /** T22-1's shape for the pilot: an employer-optional claim missing from Form 16 is claimable in the return. */
    @Test
    void of_whenHouseRentAllowanceIsRead_isClaimableInTheReturnOnTheScheduleAndTheEmployersDuty() {
        // When
        ClaimCatalogueEntry entry = shipped(HOUSE_RENT);

        // Then — Sch. III Sl. 11 and s.392(5)(b), Gazette 2025/265620 pp.506, 397; the 1961 text is not read for this
        assertThat(entry.channel()).isEqualTo(ClaimChannel.EMPLOYER_OPTIONAL);
        assertThat(entry.ifMissingFromForm16()).isEqualTo(AbsenceFromForm16.CLAIMABLE_IN_RETURN);
        assertThat(entry.chapterViiiPart()).isEmpty();
        assertThat(entry.section()).isEqualTo("SEC-HOUSE-RENT-ALLOWANCE");
        assertThat(entry.basis().provision2025().reference())
                .isEqualTo("s.11(1) with Sch. III Table Sl. 11; s.392(5)(b)");
        assertThat(entry.basis().provision1961().presence()).isEqualTo(Presence.NOT_YET_VERIFIED);
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
        assertThatThrownBy(() -> new ClaimCatalogueEntry(HOUSE_RENT, " ", ClaimChannel.RETURN_ONLY, Optional.empty(),
                AbsenceFromForm16.CLAIMABLE_IN_RETURN, basis, guidance))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("needs a rule, a section");
        assertThatThrownBy(() -> new ClaimCatalogueEntry(HOUSE_RENT, "SEC-DONATIONS", ClaimChannel.RETURN_ONLY,
                Optional.empty(), AbsenceFromForm16.CLAIMABLE_IN_RETURN, basis, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("guidance");
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void compile_whenAYearHoldsBothRules_keepsBothEntriesWithoutTheirNotes(int year) {
        // When
        JsonNode compiled = MAPPER.readTree(
                RulePackCompiler.compile(SHIPPED, TaxYearId.of(year)).files().get(ClaimCatalogue.FILE));

        // Then — ordered by rule id; note fields are not law or content, so the pack drops them
        assertThat(compiled.path("entries")).extracting(entry -> entry.path("rule").asText())
                .containsExactly("DED-DONATION-UNREPORTED", "SAL-HOUSE-RENT-ALLOWANCE");
        assertThat(compiled.path("entries").path(0).has("_sources")).isFalse();
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
}
