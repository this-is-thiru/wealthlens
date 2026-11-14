package com.thiru.wealthlens.taxengine.rules.validate;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.condition.CapitalGainsOnlyInParts;
import com.thiru.wealthlens.taxengine.rules.condition.CapitalGainsPartAtMost;
import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.condition.DeductionAllowedUnder;
import com.thiru.wealthlens.taxengine.rules.condition.Not;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.ConditionSet;
import com.thiru.wealthlens.taxengine.rules.pack.Explanations;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.regime.ConditionSetId;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A condition on a computed return names what the year's pack has (W-090c1b1): a row of the pack, a part CG-PARTS
 * declares. Each case puts one condition in a synthetic condition set beside the shipped TY 2026-27 pack.
 */
class ReturnFactReferencesTest {

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);
    private static final RulePack PACK = ShippedRulePacks.compile("rulepacks", Set.of(TY_2026_27)).load(TY_2026_27);
    private static final ConditionSetId SET = new ConditionSetId("COND-SYNTHETIC");

    private static List<String> problems(Condition condition) {
        ConditionSet set = new ConditionSet(SET, "A synthetic set", List.of(condition), RuleIds.REBATE_NEW_TAX_REGIME,
                new Provenance(URI.create("https://example.invalid/w-090c1b1"), "Synthetic, tests only",
                        LocalDate.of(2026, 11, 9), VerificationLevel.UNVERIFIED, "Nothing: synthetic"));
        RulePack pack = new RulePack(PACK.taxYear(), PACK.layers(), PACK.instruments(), PACK.rules(), PACK.ruleSets(),
                PACK.components(), PACK.regimes(), PACK.disallowanceSets(), Map.of(SET, set), PACK.funds(),
                PACK.declarations(), PACK.itrForms(), Explanations.none());
        List<String> problems = new ArrayList<>();
        ReturnFactReferences.check(pack, problems);
        return problems;
    }

    @Test
    void check_whenTheRowsAndPartsAreTheYears_findsNothing() {
        // Given — the s.93 rows outside (1)(d), and the part r.164(2)(d) means
        Condition bar = new DeductionAllowedUnder(Set.of(new RuleId("OS-BANKER-COMMISSION"),
                new RuleId("OS-OTHER-EXPENDITURE"), new RuleId("OS-DIVIDEND-INTEREST")));

        // When / Then
        assertThat(problems(bar)).isEmpty();
        assertThat(problems(new CapitalGainsOnlyInParts(Set.of(new CapitalGainsPart("LONG_TERM_LISTED_EQUITY")))))
                .isEmpty();
    }

    @Test
    void check_whenARowIsNotTheYears_namesIt() {
        assertThat(problems(new Not(new DeductionAllowedUnder(Set.of(new RuleId("OS-NO-SUCH-ROW"))))))
                .containsExactly("condition set COND-SYNTHETIC reads the deduction of row OS-NO-SUCH-ROW, which is"
                        + " not a rule of 2026-27");
    }

    @Test
    void check_whenAPartIsUndeclared_namesIt() {
        assertThat(problems(new CapitalGainsPartAtMost(new CapitalGainsPart("LONG_TERM_UNHEARD_OF"), Money.ZERO)))
                .containsExactly("condition set COND-SYNTHETIC reads capital-gains part LONG_TERM_UNHEARD_OF, which"
                        + " the year's CapitalGainsParts does not declare (D-309)");
    }
}
