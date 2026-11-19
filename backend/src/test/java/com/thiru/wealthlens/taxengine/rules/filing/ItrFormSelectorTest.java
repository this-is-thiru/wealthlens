package com.thiru.wealthlens.taxengine.rules.filing;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.condition.AgeAtLeast;
import com.thiru.wealthlens.taxengine.rules.condition.AgeSubject;
import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.condition.ConditionContext;
import com.thiru.wealthlens.taxengine.rules.condition.Declarations;
import com.thiru.wealthlens.taxengine.rules.condition.Declared;
import com.thiru.wealthlens.taxengine.rules.condition.IsResident;
import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.condition.ResidentialStatus;
import com.thiru.wealthlens.taxengine.rules.filing.FormVerdict.Standing;
import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.id.FurnishingManner;
import com.thiru.wealthlens.taxengine.rules.id.ItrFormCode;
import com.thiru.wealthlens.taxengine.rules.id.ReturnKind;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The cascade over synthetic forms (DR 21 I4; W-091, D-653): SYN-1 for a resident who is not a director, then SYN-2 for
 * anyone, excluding SYN-1. Each case changes one fact, so each moves one form across its line.
 */
class ItrFormSelectorTest {

    private static final Provenance SOURCE = new Provenance(URI.create("https://example.invalid/w-091"),
            "Synthetic, tests only", LocalDate.of(2026, 11, 13), VerificationLevel.UNVERIFIED, "Nothing: synthetic");
    private static final ReturnKind ORIGINAL = new ReturnKind("SYN_ORIGINAL");
    private static final DeclarationCode DIRECTOR = new DeclarationCode("SYN_DIRECTOR");
    private static final ItrFormCode SYN_1 = new ItrFormCode("SYN-1");
    private static final ItrFormCode SYN_1A = new ItrFormCode("SYN-1A");
    private static final ItrFormCode SYN_2 = new ItrFormCode("SYN-2");
    private static final FurnishingManner ELECTRONIC = new FurnishingManner("SYN_ELECTRONIC");
    private static final FurnishingManner PAPER = new FurnishingManner("SYN_PAPER");
    private static final String DIRECTOR_BAR = "Synthetic bar: is a director";

    private static ItrFormSpec form(ItrFormCode code, int order, Set<PersonType> appliesTo, List<Condition> positive,
            List<ItrFormBar> bars, Set<ItrFormCode> excludes) {
        List<FurnishingOption> furnishing = List.of(new FurnishingOption(ELECTRONIC, List.of(), SOURCE),
                new FurnishingOption(PAPER, List.of(new AgeAtLeast(80, AgeSubject.SELF)), SOURCE));
        return new ItrFormSpec(code, code.value(), order, appliesTo, Set.of(ORIGINAL), positive, bars, excludes,
                furnishing, List.of(), SOURCE);
    }

    private static ItrFormSpec syn1() {
        return form(SYN_1, 1, Set.of(PersonType.INDIVIDUAL), List.of(new IsResident()),
                List.of(new ItrFormBar(DIRECTOR_BAR, new Declared(DIRECTOR), SOURCE)), Set.of());
    }

    private static ItrFormSpec syn2(int order) {
        return form(SYN_2, order, Set.of(PersonType.INDIVIDUAL, PersonType.HUF), List.of(), List.of(), Set.of(SYN_1));
    }

    private static ConditionContext person(ResidentialStatus residence, int age, Optional<Boolean> director) {
        Declarations answers = director.map(yes -> new Declarations(Map.of(DIRECTOR, yes), Map.of()))
                .orElse(Declarations.NONE);
        return new ConditionContext(residence, PersonType.INDIVIDUAL, Map.of(AgeSubject.SELF, age), Map.of())
                .answering(answers);
    }

    private static ItrSelection select(List<ItrFormSpec> forms, ConditionContext facts) {
        return ItrFormSelector.select(forms, PersonType.INDIVIDUAL, ORIGINAL, facts);
    }

    @Test
    void select_whenTheFirstFormIsOpen_choosesItAndExcludesTheNext() {
        // When
        ItrSelection selection = select(List.of(syn2(2), syn1()),
                person(ResidentialStatus.RESIDENT, 45, Optional.of(false)));

        // Then — in cascade order, whatever the order given
        assertThat(selection.chosen()).contains(SYN_1);
        assertThat(selection.verdicts()).extracting(FormVerdict::form).containsExactly(SYN_1, SYN_2);
        assertThat(selection.verdicts().get(0).standing()).isEqualTo(Standing.ELIGIBLE);
        assertThat(selection.verdicts().get(1).standing()).isEqualTo(Standing.EXCLUDED);
        assertThat(selection.verdicts().get(1).excludedBy()).contains(SYN_1);
        assertThat(selection.furnishing()).containsExactly(ELECTRONIC);
    }

    @Test
    void select_whenABarHolds_namesItAndChoosesTheNextForm() {
        // When
        ItrSelection selection = select(List.of(syn1(), syn2(2)),
                person(ResidentialStatus.RESIDENT, 45, Optional.of(true)));

        // Then
        assertThat(selection.chosen()).contains(SYN_2);
        FormVerdict first = selection.verdicts().get(0);
        assertThat(first.standing()).isEqualTo(Standing.INELIGIBLE);
        assertThat(first.barsHeld()).extracting(ItrFormBar::reason).containsExactly(DIRECTOR_BAR);
        assertThat(first.conditionsFailed()).isEmpty();
        assertThat(selection.verdicts().get(1).standing()).isEqualTo(Standing.ELIGIBLE);
    }

    @Test
    void select_whenAPositiveConditionFails_describesIt() {
        // When
        ItrSelection selection = select(List.of(syn1(), syn2(2)),
                person(ResidentialStatus.NON_RESIDENT, 45, Optional.of(false)));

        // Then
        assertThat(selection.chosen()).contains(SYN_2);
        assertThat(selection.verdicts().get(0).conditionsFailed()).containsExactly("the assessee is resident");
    }

    @Test
    void select_whenTheFirstFormReadsAnUnansweredQuestion_choosesNoneAndNamesIt() {
        // When — C3a: the later form is open, but the cascade cannot pass the first
        ItrSelection selection = select(List.of(syn1(), syn2(2)),
                person(ResidentialStatus.RESIDENT, 45, Optional.empty()));

        // Then
        assertThat(selection.chosen()).isEmpty();
        assertThat(selection.furnishing()).isEmpty();
        FormVerdict first = selection.verdicts().get(0);
        assertThat(first.standing()).isEqualTo(Standing.UNDETERMINED);
        assertThat(first.unanswered()).containsExactly(DIRECTOR);
        assertThat(selection.verdicts().get(1).standing()).isEqualTo(Standing.ELIGIBLE);
    }

    @Test
    void select_whenAFailedConditionSettlesAFormWithAnUnansweredBar_movesOn() {
        // When — non-resident, so SYN-1 is closed whatever the answer
        ItrSelection selection = select(List.of(syn1(), syn2(2)),
                person(ResidentialStatus.NON_RESIDENT, 45, Optional.empty()));

        // Then
        assertThat(selection.chosen()).contains(SYN_2);
        assertThat(selection.verdicts().get(0).standing()).isEqualTo(Standing.INELIGIBLE);
        assertThat(selection.verdicts().get(0).unanswered()).containsExactly(DIRECTOR);
    }

    @Test
    void select_whenThePersonOrKindIsNotServed_leavesTheFormOut() {
        // When
        ItrSelection huf = ItrFormSelector.select(List.of(syn1(), syn2(2)), PersonType.HUF, ORIGINAL,
                person(ResidentialStatus.RESIDENT, 45, Optional.of(false)));
        ItrSelection otherKind = ItrFormSelector.select(List.of(syn1(), syn2(2)), PersonType.INDIVIDUAL,
                new ReturnKind("SYN_UPDATED"), person(ResidentialStatus.RESIDENT, 45, Optional.of(false)));

        // Then
        assertThat(huf.verdicts()).extracting(FormVerdict::form).containsExactly(SYN_2);
        assertThat(huf.chosen()).contains(SYN_2);
        assertThat(otherKind.verdicts()).isEmpty();
        assertThat(otherKind.chosen()).isEmpty();
    }

    @Test
    void select_whenTheFilerIsEighty_opensTheConditionalManner() {
        // When
        ItrSelection eighty = select(List.of(syn1()), person(ResidentialStatus.RESIDENT, 80, Optional.of(false)));
        ItrSelection seventyNine = select(List.of(syn1()), person(ResidentialStatus.RESIDENT, 79, Optional.of(false)));

        // Then
        assertThat(eighty.furnishing()).containsExactly(ELECTRONIC, PAPER);
        assertThat(seventyNine.furnishing()).containsExactly(ELECTRONIC);
    }

    @Test
    void select_whenAFormIsInsertedIntoTheCascade_choosesItForItsFactsAndLeavesTheOthers() {
        // Given — T21-16: SYN-1A at order 2, for directors only, excluding SYN-1; SYN-2 moves to order 3
        List<ItrFormSpec> inserted = List.of(syn1(),
                form(SYN_1A, 2, Set.of(PersonType.INDIVIDUAL), List.of(new Declared(DIRECTOR)), List.of(), Set.of(SYN_1)),
                syn2(3));

        // When
        ItrSelection director = select(inserted, person(ResidentialStatus.RESIDENT, 45, Optional.of(true)));
        ItrSelection notDirector = select(inserted, person(ResidentialStatus.RESIDENT, 45, Optional.of(false)));

        // Then
        assertThat(director.chosen()).contains(SYN_1A);
        assertThat(notDirector.chosen()).contains(SYN_1);
        assertThat(notDirector.verdicts()).extracting(FormVerdict::standing)
                .containsExactly(Standing.ELIGIBLE, Standing.EXCLUDED, Standing.EXCLUDED);
    }
}
