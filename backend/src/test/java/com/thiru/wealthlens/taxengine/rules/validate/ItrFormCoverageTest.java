package com.thiru.wealthlens.taxengine.rules.validate;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.condition.IsResident;
import com.thiru.wealthlens.taxengine.rules.condition.Not;
import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.filing.FurnishingOption;
import com.thiru.wealthlens.taxengine.rules.filing.ItrFormSpec;
import com.thiru.wealthlens.taxengine.rules.id.FurnishingManner;
import com.thiru.wealthlens.taxengine.rules.id.ItrFormCode;
import com.thiru.wealthlens.taxengine.rules.id.ReturnKind;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * I19 and I20 (W-090c2, D-529) on the synthetic 2099-00 pack, whose regimes serve INDIVIDUAL only: a form for every
 * person type served and every return kind the forms name, and no two forms that could both be offered. Each case
 * replaces the pack's forms ({@code SYN-1} for an individual, {@code SYN-2} for an individual or HUF, excluding
 * {@code SYN-1}).
 */
class ItrFormCoverageTest {

    private static final String ROOT = "rulepacks-synthetic";
    private static final RulePack PACK = ShippedRulePacks.compile(ROOT, ClasspathRulePackProvider.shippedYears(ROOT))
            .load(TaxYearId.of(2099));
    private static final ItrFormCode ONE = new ItrFormCode("SYN-1");
    private static final ItrFormCode TWO = new ItrFormCode("SYN-2");
    private static final ReturnKind ORIGINAL = new ReturnKind("SYN_ORIGINAL");
    private static final ReturnKind ON_NOTICE = new ReturnKind("SYN_ON_NOTICE");
    private static final Provenance SOURCE = new Provenance(URI.create("https://example.invalid/w-090c2"),
            "Synthetic, tests only", LocalDate.of(2026, 11, 9), VerificationLevel.UNVERIFIED, "Nothing: synthetic");

    private static ItrFormSpec form(ItrFormCode code, int order, Set<PersonType> appliesTo, Set<ReturnKind> kinds,
            List<Condition> positive, Set<ItrFormCode> excludes) {
        return new ItrFormSpec(code, "Synthetic " + code, order, appliesTo, kinds, positive, List.of(), excludes,
                List.of(new FurnishingOption(new FurnishingManner("SYN_ELECTRONIC"), List.of(), SOURCE)), List.of(), SOURCE);
    }

    private static List<String> problems(ItrFormSpec... forms) {
        Map<ItrFormCode, ItrFormSpec> byCode = new LinkedHashMap<>();
        for (ItrFormSpec form : forms) {
            byCode.put(form.code(), form);
        }
        RulePack pack = new RulePack(PACK.taxYear(), PACK.layers(), PACK.instruments(), PACK.rules(), PACK.ruleSets(),
                PACK.components(), PACK.regimes(), PACK.disallowanceSets(), PACK.conditionSets(), PACK.funds(),
                PACK.declarations(), byCode);
        List<String> problems = new ArrayList<>();
        ItrFormCoverage.check(pack, problems);
        return problems;
    }

    @Test
    void check_whenTheSyntheticFormsAreChecked_findsNothing() {
        // Given / When — SYN-2 excludes SYN-1, and each kind has a form for an individual
        List<String> problems = new ArrayList<>();
        ItrFormCoverage.check(PACK, problems);

        // Then
        assertThat(problems).isEmpty();
    }

    @Test
    void check_whenTwoFormsForOnePersonAndKindAreNotSeparated_refusesUnderI20() {
        // Given / When — SYN-2 without its exclusion: both could be offered to an individual
        List<String> problems = problems(
                form(ONE, 1, Set.of(PersonType.INDIVIDUAL), Set.of(ORIGINAL), List.of(new IsResident()), Set.of()),
                form(TWO, 2, Set.of(PersonType.INDIVIDUAL, PersonType.HUF), Set.of(ORIGINAL), List.of(), Set.of()));

        // Then
        assertThat(problems).containsExactly("I20: forms SYN-1 and SYN-2 serve one person type and return kind, and"
                + " neither excludes the other nor holds the negation of the other's positive condition; both could"
                + " be offered");
    }

    @Test
    void check_whenOneFormHoldsTheNegationOfTheOthersCondition_findsNothing() {
        // Given / When — residents to one, everyone else to the other
        List<String> problems = problems(
                form(ONE, 1, Set.of(PersonType.INDIVIDUAL), Set.of(ORIGINAL), List.of(new IsResident()), Set.of()),
                form(TWO, 2, Set.of(PersonType.INDIVIDUAL), Set.of(ORIGINAL), List.of(new Not(new IsResident())),
                        Set.of()));

        // Then
        assertThat(problems).isEmpty();
    }

    @Test
    void check_whenAReturnKindHasNoFormForAServedPersonType_refusesUnderI19() {
        // Given / When — the on-notice return is served to an HUF only, but the year's regimes serve individuals
        List<String> problems = problems(
                form(ONE, 1, Set.of(PersonType.INDIVIDUAL), Set.of(ORIGINAL), List.of(), Set.of()),
                form(TWO, 2, Set.of(PersonType.HUF), Set.of(ON_NOTICE), List.of(), Set.of()));

        // Then
        assertThat(problems).containsExactly("I19: no form of 2099-00 serves INDIVIDUAL for a SYN_ON_NOTICE return");
    }

    @Test
    void check_whenTheYearHasNoForms_findsNothing() {
        // Given / When — no form names a return kind, so I19 asks for none (D-529 C1a)
        List<String> problems = problems();

        // Then
        assertThat(problems).isEmpty();
    }

    @Test
    void check_whenTwoFormsServeOnePersonButDifferentKinds_findsNothing() {
        // Given / When — an individual is offered SYN-1 for one kind and SYN-2 for the other, never both for one return
        List<String> problems = problems(
                form(ONE, 1, Set.of(PersonType.INDIVIDUAL), Set.of(ORIGINAL), List.of(), Set.of()),
                form(TWO, 2, Set.of(PersonType.INDIVIDUAL), Set.of(ON_NOTICE), List.of(), Set.of()));

        // Then
        assertThat(problems).isEmpty();
    }

    @Test
    void check_whenTheEarlierCodeExcludesTheLater_findsNothing() {
        // Given / When — the exclusion read from either form of the pair separates them; its order is ItrFormReferences'
        List<String> problems = problems(
                form(ONE, 1, Set.of(PersonType.INDIVIDUAL), Set.of(ORIGINAL), List.of(), Set.of(TWO)),
                form(TWO, 2, Set.of(PersonType.INDIVIDUAL), Set.of(ORIGINAL), List.of(), Set.of()));

        // Then
        assertThat(problems).isEmpty();
    }

    @Test
    void check_whenTheNegationIsOnTheEarlierCode_findsNothing() {
        // Given / When — non-residents to SYN-1, residents to SYN-2: the pair is separated whichever form holds the Not
        List<String> problems = problems(
                form(ONE, 1, Set.of(PersonType.INDIVIDUAL), Set.of(ORIGINAL), List.of(new Not(new IsResident())), Set.of()),
                form(TWO, 2, Set.of(PersonType.INDIVIDUAL), Set.of(ORIGINAL), List.of(new IsResident()), Set.of()));

        // Then
        assertThat(problems).isEmpty();
    }
}
