package com.thiru.wealthlens.taxengine.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeValue;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Trace lines in plain language (W-116, D-671), on the shipped TY 2026-27 content: each expected sentence is the
 * template in {@code content/explanations.json} with the figures written in by hand, and the other limits in name order.
 */
class ExplanationRendererTest {

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);

    private static final RulePack PACK = ShippedRulePacks.compile("rulepacks", Set.of(TY_2026_27)).load(TY_2026_27);

    private static final RuleId HRA = new RuleId("SAL-HOUSE-RENT-ALLOWANCE");

    private static final RuleId RENT_PAID = new RuleId("DED-RENT-PAID");

    private static TraceLine line(RuleId rule, NodeValue in, NodeValue out) {
        return TraceLine.of(1, new NodeId("salary.exemptions"), rule, StepKind.EXEMPTION, in, out,
                PACK.rule(rule).headProvenance());
    }

    private static TraceLine hra() {
        return line(HRA, MoneyValue.of(Money.of("240000")), MoneyValue.of(Money.of("120000")));
    }

    @Test
    void explain_whenRentBindsHra_saysWhatItIsWhyAndThatTheOtherLimitsWouldNotHelp() {
        // Given
        TraceLine bound = hra().withLimitedBy("rent less one-tenth of salary");

        // When / Then
        assertThat(ExplanationRenderer.explain(bound, PACK.explanations())).isEqualTo(
                "Of the ₹2,40,000 house rent allowance you received for this home, ₹1,20,000 is not taxed. It is limited by"
                        + " how far your rent exceeds a share of your salary; paying more rent, with evidence, would raise"
                        + " it, up to the next limit. Raising any of the other limits alone would not change it: allowance"
                        + " received, percentage of salary.");
    }

    @Test
    void explain_whenTheLocationBindsHra_namesThatLimitAndTheOthers() {
        // Given
        TraceLine bound = hra().withLimitedBy("percentage of salary");

        // When / Then
        assertThat(ExplanationRenderer.explain(bound, PACK.explanations())).isEqualTo(
                "Of the ₹2,40,000 house rent allowance you received for this home, ₹1,20,000 is not taxed. It is limited by"
                        + " the share of your salary the home's location allows; paying more rent would not raise it."
                        + " Raising any of the other limits alone would not change it: allowance received, rent less"
                        + " one-tenth of salary.");
    }

    @Test
    void explain_whenANodeAddsAClauseAfterTheLimit_stillFindsTheLimit() {
        // Given s.134's line, which names the total income after the limit (RentPaidDeduction)
        TraceLine bound = line(RENT_PAID, MoneyValue.of(Money.of("10000000")), MoneyValue.of(Money.of("60000")))
                .withLimitedBy("25% of total income, on total income before this deduction 240000.00");

        // When / Then
        assertThat(ExplanationRenderer.explain(bound, PACK.explanations())).isEqualTo(
                "Of the ₹1,00,00,000 rent you paid, ₹60,000 is deducted. It is limited by a share of your total income;"
                        + " paying more rent would not raise it. Raising any of the other limits alone would not change it:"
                        + " 5000 per month, rent less 10% of total income.");
    }

    @Test
    void explain_whenTheLineNamesNoLimit_saysOnlyWhatItIs() {
        // Given / When / Then
        assertThat(ExplanationRenderer.explain(hra(), PACK.explanations()))
                .isEqualTo("Of the ₹2,40,000 house rent allowance you received for this home, ₹1,20,000 is not taxed.");
    }

    @Test
    void explain_whenWhatBoundIsNotALimit_showsItAsTheLineStatesIt() {
        // Given a home not rented, which no limit of the least-of names
        TraceLine owned = line(HRA, MoneyValue.of(Money.of("240000")), MoneyValue.of(Money.ZERO))
                .withLimitedBy("not rented: the home is owned or no rent was paid");

        // When / Then
        assertThat(ExplanationRenderer.explain(owned, PACK.explanations())).isEqualTo(
                "Of the ₹2,40,000 house rent allowance you received for this home, ₹0 is not taxed. not rented: the home is"
                        + " owned or no rent was paid");
    }

    @Test
    void explain_whenTheRuleHasNoExplanation_showsTheRuleId() {
        // Given
        TraceLine unexplained = line(new RuleId("DED-SCHEDULE-XV"), MoneyValue.of(Money.ZERO), MoneyValue.of(Money.ZERO));

        // When / Then
        assertThat(ExplanationRenderer.explain(unexplained, PACK.explanations())).isEqualTo("DED-SCHEDULE-XV");
    }

    @Test
    void explain_whenAFigureTheTemplateNamesIsNotMoney_showsTheRuleIdRatherThanAPlaceholder() {
        // Given
        TraceLine notMoney = line(HRA, new ComponentSet(Map.of()), MoneyValue.of(Money.of("120000")));

        // When / Then
        assertThat(ExplanationRenderer.explain(notMoney, PACK.explanations())).isEqualTo("SAL-HOUSE-RENT-ALLOWANCE");
    }

    @Test
    void explain_whenAPerquisiteNamesNoLimit_valuesItAndShowsTheEnginesReason() {
        // Given — club expenditure, its limitedBy the engine's own text until W-327
        TraceLine club = line(new RuleId("PERQ-CLUB"), MoneyValue.of(Money.of("50000")), MoneyValue.of(Money.of("50000")))
                .withLimitedBy("CLUB, less the recovery 0.00");

        // When / Then
        assertThat(ExplanationRenderer.explain(club, PACK.explanations()))
                .isEqualTo("Club expenditure your employer paid is valued at ₹50,000 as a perquisite. CLUB, less the recovery 0.00");
    }

    @Test
    void explain_whenARowIsAnExclusion_saysItIsNotTaxedWithoutAFigure() {
        // Given
        TraceLine telephone = line(new RuleId("PERQ-TELEPHONE"), MoneyValue.of(Money.of("12000")), MoneyValue.of(Money.ZERO));

        // When / Then
        assertThat(ExplanationRenderer.explain(telephone, PACK.explanations()))
                .isEqualTo("Telephone and mobile phone expenses your employer pays are not taxed as a perquisite.");
    }

    @Test
    void explain_whenTheFigureIsNotMoneyAndTheSentenceNamesNone_rendersTheSentence() {
        // Given — a salary base, whose out is the bases by employment (W-325 C3a)
        TraceLine base = line(new RuleId("SAL-SALARY-FOR-HRA"), new ComponentSet(Map.of()), new SalaryBases(Map.of()));

        // When / Then
        assertThat(ExplanationRenderer.explain(base, PACK.explanations())).isEqualTo(
                "This is the salary your house rent allowance is measured against: the pay lines the law counts for it.");
    }
}
