package com.thiru.wealthlens.taxengine.rules.validate;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.condition.AnyOf;
import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.condition.Declared;
import com.thiru.wealthlens.taxengine.rules.condition.DeclaredAmountAbove;
import com.thiru.wealthlens.taxengine.rules.condition.IsResident;
import com.thiru.wealthlens.taxengine.rules.condition.Not;
import com.thiru.wealthlens.taxengine.rules.declaration.AnswerKind;
import com.thiru.wealthlens.taxengine.rules.declaration.DeclarationDefinition;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.ConditionSet;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.regime.ConditionSetId;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
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
 * Every condition reading a declaration names a question of the year's catalogue, in its kind (W-090a1). The shipped
 * TY 2026-27 pack asks none and reads none; each case adds a reading to {@code REBATE-NEW-TAX-REGIME}'s versions or a
 * condition set, and the catalogue as the case needs.
 */
class DeclarationReferencesTest {

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);
    private static final RulePack PACK = ShippedRulePacks.compile("rulepacks", Set.of(TY_2026_27)).load(TY_2026_27);
    private static final DeclarationCode DIRECTOR = new DeclarationCode("SYNTHETIC_YES_NO");
    private static final DeclarationCode FARMING = new DeclarationCode("SYNTHETIC_AMOUNT");
    private static final Map<DeclarationCode, DeclarationDefinition> CATALOGUE = Map.of(
            DIRECTOR, definition(DIRECTOR, AnswerKind.YES_NO), FARMING, definition(FARMING, AnswerKind.AMOUNT));

    private static DeclarationDefinition definition(DeclarationCode code, AnswerKind kind) {
        return new DeclarationDefinition(code, "A synthetic question", kind, DateRange.fromOnwards(LocalDate.of(2026, 4, 1)),
                new Provenance(URI.create("https://egazette.gov.in/WriteReadData/2026/271092.pdf"), "Synthetic, tests only",
                        LocalDate.of(2026, 11, 9), VerificationLevel.UNVERIFIED, "Nothing: a synthetic row"));
    }

    private static List<String> problems(RulePack pack) {
        List<String> problems = new ArrayList<>();
        DeclarationReferences.check(pack, problems);
        return problems;
    }

    /** The shipped pack, the rebate row's every version also reading {@code reading}, with {@code catalogue}. */
    private static RulePack readingInARule(Condition reading, Map<DeclarationCode, DeclarationDefinition> catalogue) {
        RuleRow row = PACK.rule(RuleIds.REBATE_NEW_TAX_REGIME);
        List<RuleVersion> versions = row.versions().stream().map(version -> {
            List<Condition> conditions = new ArrayList<>(version.conditions());
            conditions.add(reading);
            return new RuleVersion(version.validity(), version.regimes(), version.cap(), version.relief(),
                    version.rounding(), version.definition(), conditions, version.provision1961(),
                    version.provision2025(), version.introducedBy(), version.legalStatus(), version.publicationStatus(),
                    version.provenance());
        }).toList();
        Map<com.thiru.wealthlens.taxengine.rules.id.RuleId, RuleRow> rules = new LinkedHashMap<>(PACK.rules());
        rules.put(row.id(), new RuleRow(row.id(), row.axis(), versions, row.midYearCombination(),
                row.midYearCombinationRule(), row.prorationBasis(), row.headProvenance()));
        return new RulePack(PACK.taxYear(), PACK.layers(), PACK.instruments(), rules, PACK.ruleSets(),
                PACK.components(), PACK.regimes(), PACK.disallowanceSets(), PACK.conditionSets(), PACK.funds(),
                catalogue);
    }

    @Test
    void check_whenTheShippedPackIsChecked_findsNothing() {
        // Given / When / Then
        assertThat(PACK.declarations()).isEmpty();
        assertThat(problems(PACK)).isEmpty();
    }

    @Test
    void check_whenEachReadingNamesAQuestionInItsKind_findsNothing() {
        // Given — both kinds, one inside a composition
        Condition both = new AnyOf(List.of(new Declared(DIRECTOR),
                new Not(new DeclaredAmountAbove(FARMING, Money.of("1000")))));

        // When / Then
        assertThat(problems(readingInARule(both, CATALOGUE))).isEmpty();
    }

    @Test
    void check_whenARuleReadsAQuestionTheYearDoesNotAsk_namesTheRuleAndTheQuestion() {
        // Given
        RulePack unasked = readingInARule(new Not(new Declared(DIRECTOR)), Map.of());

        // When / Then
        assertThat(problems(unasked)).containsExactly("rule REBATE-NEW-TAX-REGIME reads declaration SYNTHETIC_YES_NO,"
                + " which is not in the year's declaration catalogue");
    }

    @Test
    void check_whenAnAmountConditionReadsAYesOrNoQuestion_namesTheKinds() {
        // Given
        RulePack mismatched = readingInARule(new DeclaredAmountAbove(DIRECTOR, Money.of("1000")), CATALOGUE);

        // When / Then
        assertThat(problems(mismatched)).containsExactly("rule REBATE-NEW-TAX-REGIME reads declaration SYNTHETIC_YES_NO"
                + " as AMOUNT, but the catalogue asks it as YES_NO");
    }

    @Test
    void check_whenAYesOrNoConditionReadsAnAmountQuestion_namesTheKinds() {
        // Given
        RulePack mismatched = readingInARule(new Declared(FARMING), CATALOGUE);

        // When / Then
        assertThat(problems(mismatched)).containsExactly("rule REBATE-NEW-TAX-REGIME reads declaration SYNTHETIC_AMOUNT"
                + " as YES_NO, but the catalogue asks it as AMOUNT");
    }

    @Test
    void check_whenAConditionSetReadsAQuestionTheYearDoesNotAsk_namesTheSet() {
        // Given
        ConditionSetId id = new ConditionSetId("COND-SYNTHETIC");
        ConditionSet set = new ConditionSet(id, "A synthetic set", List.of(new IsResident(), new Declared(DIRECTOR)),
                RuleIds.REBATE_NEW_TAX_REGIME, definition(DIRECTOR, AnswerKind.YES_NO).provenance());
        RulePack pack = new RulePack(PACK.taxYear(), PACK.layers(), PACK.instruments(), PACK.rules(), PACK.ruleSets(),
                PACK.components(), PACK.regimes(), PACK.disallowanceSets(), Map.of(id, set), PACK.funds(), Map.of());

        // When / Then
        assertThat(problems(pack)).containsExactly("condition set COND-SYNTHETIC reads declaration SYNTHETIC_YES_NO,"
                + " which is not in the year's declaration catalogue");
    }
}
