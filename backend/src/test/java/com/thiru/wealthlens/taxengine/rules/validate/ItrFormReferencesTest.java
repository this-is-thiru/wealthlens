package com.thiru.wealthlens.taxengine.rules.validate;

import static org.assertj.core.api.Assertions.assertThat;

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
 * A year's forms make one deterministic cascade (W-090b): an exclusion names an earlier form of the year, and no two
 * forms tried for the same person type and return kind share an order. Each case replaces the synthetic 2099-00
 * pack's forms ({@code SYN-1} cascade 1 for an individual, {@code SYN-2} cascade 2 for an individual or HUF,
 * excluding {@code SYN-1}).
 */
class ItrFormReferencesTest {

    private static final String ROOT = "rulepacks-synthetic";
    private static final RulePack PACK = ShippedRulePacks.compile(ROOT, ClasspathRulePackProvider.shippedYears(ROOT))
            .load(TaxYearId.of(2099));
    private static final ItrFormCode ONE = new ItrFormCode("SYN-1");
    private static final ItrFormCode TWO = new ItrFormCode("SYN-2");
    private static final ReturnKind ORIGINAL = new ReturnKind("SYN_ORIGINAL");
    private static final Provenance SOURCE = new Provenance(URI.create("https://example.invalid/w-090b"),
            "Synthetic, tests only", LocalDate.of(2026, 11, 9), VerificationLevel.UNVERIFIED, "Nothing: synthetic");

    private static ItrFormSpec form(ItrFormCode code, int order, Set<PersonType> appliesTo, Set<ItrFormCode> excludes) {
        return new ItrFormSpec(code, "Synthetic " + code, order, appliesTo, Set.of(ORIGINAL), List.of(), List.of(),
                excludes, List.of(new FurnishingOption(new FurnishingManner("SYN_ELECTRONIC"), List.of(), SOURCE)), List.of(),
                SOURCE);
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
        ItrFormReferences.check(pack, problems);
        return problems;
    }

    @Test
    void check_whenTheSyntheticFormsAreChecked_findsNothing() {
        // Given / When
        List<String> problems = new ArrayList<>();
        ItrFormReferences.check(PACK, problems);

        // Then
        assertThat(PACK.itrForms()).containsOnlyKeys(ONE, TWO);
        assertThat(problems).isEmpty();
    }

    @Test
    void check_whenAFormIsExcludedByAFormTheYearDoesNotHave_namesBoth() {
        // Given / When
        List<String> problems = problems(form(TWO, 2, Set.of(PersonType.INDIVIDUAL), Set.of(ONE)));

        // Then
        assertThat(problems).containsExactly("form SYN-2 is excluded by SYN-1, which is not a form of the year");
    }

    @Test
    void check_whenAFormIsExcludedByALaterForm_refuses() {
        // Given / When
        List<String> problems = problems(
                form(ONE, 1, Set.of(PersonType.INDIVIDUAL), Set.of(TWO)),
                form(TWO, 2, Set.of(PersonType.INDIVIDUAL), Set.of()));

        // Then
        assertThat(problems).containsExactly("form SYN-1 (cascade 1) is excluded by SYN-2 (cascade 2);"
                + " a form is excluded only by an earlier one");
    }

    @Test
    void check_whenTwoFormsForTheSamePersonAndKindShareAnOrder_refuses() {
        // Given / When
        List<String> problems = problems(
                form(ONE, 1, Set.of(PersonType.INDIVIDUAL), Set.of()),
                form(TWO, 1, Set.of(PersonType.INDIVIDUAL, PersonType.HUF), Set.of()));

        // Then
        assertThat(problems).containsExactly("forms SYN-1 and SYN-2 share cascade order 1 for [INDIVIDUAL] and"
                + " [SYN_ORIGINAL]; the cascade would not say which is tried first");
    }

    @Test
    void check_whenTwoFormsShareAnOrderButServeDifferentPersons_findsNothing() {
        // Given / When
        List<String> problems = problems(
                form(ONE, 1, Set.of(PersonType.INDIVIDUAL), Set.of()),
                form(TWO, 1, Set.of(PersonType.HUF), Set.of()));

        // Then
        assertThat(problems).isEmpty();
    }
}
