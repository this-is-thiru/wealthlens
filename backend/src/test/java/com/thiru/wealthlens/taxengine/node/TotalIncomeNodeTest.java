package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Gross total income and total income (S2 s.122(1), (10); D-132). Fixture amounts; no figure is tax. */
class TotalIncomeNodeTest {

    private static final RegimeCode OLD = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final TaxYearId TY_2026 = TaxYearId.of(2026);
    private static final RulePack PACK = new ClasspathRulePackProvider(
                    "rulepacks", Set.of(TY_2026), new JsonPackAssembler(LockedInstruments.of(TY_2026)))
            .load(TY_2026);

    @Test
    void grossTotalIncome_whenSalariesIsTheOnlyHead_isIncomeUnderSalaries() {
        // When
        NodeResult result = NodeHarness.with(PACK).regime(OLD)
                .given(NodeIds.SALARY_INCOME, Money.of("925000"))
                .given(NodeIds.HOUSEPROPERTY_INCOME, Money.ZERO)
                .given(NodeIds.OTHERSOURCES_INCOME, Money.ZERO)
                .run(new GrossTotalIncomeNode());

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("925000")));
        assertThat(result.trace()).isEmpty();
    }

    @Test
    void grossTotalIncome_whenHousePropertyHasIncome_addsTheHeadToSalaries() {
        // When — 9,25,000 under Salaries and 3,65,400 from a let property (D-155)
        NodeResult result = NodeHarness.with(PACK).regime(OLD)
                .given(NodeIds.SALARY_INCOME, Money.of("925000"))
                .given(NodeIds.HOUSEPROPERTY_INCOME, Money.of("365400"))
                .given(NodeIds.OTHERSOURCES_INCOME, Money.ZERO)
                .run(new GrossTotalIncomeNode());

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("1290400")));
    }

    @Test
    void grossTotalIncome_whenOtherSourcesHasIncome_addsTheHeadToTheOthers() {
        // When — 9,25,000 under Salaries, 3,65,400 from house property and 95,000 from other sources (D-194)
        NodeResult result = NodeHarness.with(PACK).regime(OLD)
                .given(NodeIds.SALARY_INCOME, Money.of("925000"))
                .given(NodeIds.HOUSEPROPERTY_INCOME, Money.of("365400"))
                .given(NodeIds.OTHERSOURCES_INCOME, Money.of("95000"))
                .run(new GrossTotalIncomeNode());

        // Then — 9,25,000 + 3,65,400 + 95,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("1385400")));
    }

    @Test
    void totalIncome_whenChapterEightAllowsADeduction_isGrossTotalIncomeLessIt() {
        // When — 9,25,000 − 2,00,000
        NodeResult result = NodeHarness.with(PACK).regime(OLD)
                .given(NodeIds.GTI, Money.of("925000"))
                .given(NodeIds.CHAPTERVIII, Money.of("200000"))
                .run(new TotalIncomeNode());

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("725000")));
    }
}
