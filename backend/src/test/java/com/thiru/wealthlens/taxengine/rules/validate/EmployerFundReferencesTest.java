package com.thiru.wealthlens.taxengine.rules.validate;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.component.EmployerFundDefinition;
import com.thiru.wealthlens.taxengine.rules.id.EmployerFundCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * The employer-fund registry against the rules (W-238e). The rules that read {@code employer.fund} in TY 2026-27, from
 * their own conditions: {@code SAL-INCLUDES-PENSION-CONTRIBUTION} and {@code DED-NPS-EMPLOYER-GOVT}, {@code -OTHER} and
 * {@code -NEW-TAX-REGIME}, each for {@code NOTIFIED_PENSION_SCHEME}; {@code PERQ-RPF-CONTRIBUTION}, for the provident
 * fund, has no version after 2026-03-31.
 */
class EmployerFundReferencesTest {

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);
    private static final RulePack PACK = ShippedRulePacks.compile("rulepacks", Set.of(TY_2026_27)).load(TY_2026_27);
    private static final EmployerFundCode NPS = new EmployerFundCode("NOTIFIED_PENSION_SCHEME");
    private static final EmployerFundCode RPF = new EmployerFundCode("RECOGNISED_PROVIDENT_FUND");

    private static List<String> problems(RulePack pack) {
        List<String> problems = new ArrayList<>();
        EmployerFundReferences.check(pack, problems);
        return problems;
    }

    /** The shipped pack with {@code fund}'s row changed by {@code change}, or dropped where it returns none. */
    private static RulePack withFund(EmployerFundCode fund, UnaryOperator<EmployerFundDefinition> change) {
        Map<EmployerFundCode, EmployerFundDefinition> funds = new LinkedHashMap<>(PACK.funds());
        EmployerFundDefinition changed = change.apply(funds.remove(fund));
        if (changed != null) {
            funds.put(fund, changed);
        }
        return new RulePack(PACK.taxYear(), PACK.layers(), PACK.instruments(), PACK.rules(), PACK.ruleSets(),
                PACK.components(), PACK.regimes(), PACK.disallowanceSets(), PACK.conditionSets(), funds, Map.of());
    }

    private static EmployerFundDefinition feeding(EmployerFundDefinition row, Set<RuleId> feedsInto) {
        return new EmployerFundDefinition(row.code(), row.displayName(), feedsInto, row.validity(), row.provenance(),
                row.liquidity());
    }

    @Test
    void check_whenTheShippedPackIsChecked_findsNothing() {
        // Given / When / Then
        assertThat(problems(PACK)).isEmpty();
    }

    @Test
    void check_whenAFundTheRulesReadHasNoRow_namesEachRuleThatReadsIt() {
        // Given
        RulePack withoutNps = withFund(NPS, row -> null);

        // When / Then
        assertThat(problems(withoutNps)).containsExactlyInAnyOrder(
                "rule SAL-INCLUDES-PENSION-CONTRIBUTION reads employer fund NOTIFIED_PENSION_SCHEME, which has no row"
                        + " in the fund registry",
                "rule DED-NPS-EMPLOYER-GOVT reads employer fund NOTIFIED_PENSION_SCHEME, which has no row in the fund"
                        + " registry",
                "rule DED-NPS-EMPLOYER-OTHER reads employer fund NOTIFIED_PENSION_SCHEME, which has no row in the fund"
                        + " registry",
                "rule DED-NPS-EMPLOYER-NEW-TAX-REGIME reads employer fund NOTIFIED_PENSION_SCHEME, which has no row in"
                        + " the fund registry");
    }

    @Test
    void check_whenTheRowLeavesOutARuleThatReadsIt_namesTheRule() {
        // Given
        RulePack missingOne = withFund(NPS, row -> {
            Set<RuleId> feeds = new HashSet<>(row.feedsInto());
            feeds.remove(new RuleId("DED-NPS-EMPLOYER-GOVT"));
            return feeding(row, feeds);
        });

        // When / Then
        assertThat(problems(missingOne)).containsExactly("rule DED-NPS-EMPLOYER-GOVT reads employer fund"
                + " NOTIFIED_PENSION_SCHEME, whose row does not list it in feedsInto");
    }

    @Test
    void check_whenTheRowNamesARuleTheYearLacks_namesIt() {
        // Given — the provident fund's TY 2025-26 rule written back into the TY 2026-27 row
        RulePack stale = withFund(RPF, row -> {
            Set<RuleId> feeds = new HashSet<>(row.feedsInto());
            feeds.add(new RuleId("PERQ-RPF-CONTRIBUTION"));
            return feeding(row, feeds);
        });

        // When / Then
        assertThat(problems(stale)).containsExactly("employer fund RECOGNISED_PROVIDENT_FUND feeds PERQ-RPF-CONTRIBUTION,"
                + " which is not a rule of this pack");
    }
}
