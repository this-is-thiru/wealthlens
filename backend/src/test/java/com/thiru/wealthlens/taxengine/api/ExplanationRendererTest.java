package com.thiru.wealthlens.taxengine.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeValue;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.Explanations;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.reason.Reason;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonCodes;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonFact;
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

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");

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
        assertThat(ExplanationRenderer.explain(unexplained, Explanations.none())).isEqualTo("DED-SCHEDULE-XV");
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

    @Test
    void explain_whenADeductionIsBarredByTheRegime_saysNothingIsDeductedAndWhy() {
        // Given
        TraceLine barred = line(new RuleId("DED-EDUCATION-LOAN"), MoneyValue.of(Money.of("40000")), MoneyValue.of(Money.ZERO))
                .withLimitedBy("disallowed in the NEW_TAX_REGIME regime");

        // When / Then
        assertThat(ExplanationRenderer.explain(barred, PACK.explanations())).isEqualTo(
                "₹0 of the interest on your loan for higher education is deducted. disallowed in the NEW_TAX_REGIME regime");
    }

    @Test
    void explain_whenTheDeductionsReachGrossTotalIncome_namesBothFigures() {
        // Given — ChapterEightNode:238: in is the deductions' total, out the gross total income that limits them
        TraceLine capped = line(new RuleId("DED-WITHIN-GROSS-TOTAL"), MoneyValue.of(Money.of("250000")),
                MoneyValue.of(Money.of("180000")));

        // When / Then
        assertThat(ExplanationRenderer.explain(capped, PACK.explanations()))
                .isEqualTo("Your deductions of ₹2,50,000 together are limited to your gross total income of ₹1,80,000.");
    }

    @Test
    void explain_whenTheCapsInputIsNotMoney_showsTheRuleId() {
        // Given — the sentence names {in}, which this line does not hold in money
        TraceLine notMoney = line(new RuleId("DED-WITHIN-GROSS-TOTAL"), new ComponentSet(Map.of()),
                MoneyValue.of(Money.of("180000")));

        // When / Then
        assertThat(ExplanationRenderer.explain(notMoney, PACK.explanations())).isEqualTo("DED-WITHIN-GROSS-TOTAL");
    }

    @Test
    void explain_whenALineMeasuresAnIntermediateFigure_describesItWithoutAFigure() {
        // Given — a MEASURE line (W-325b C1a)
        TraceLine measure = line(new RuleId("HP-UNREALISED-RENT"), MoneyValue.of(Money.of("300000")),
                MoneyValue.of(Money.of("240000")));

        // When / Then
        assertThat(ExplanationRenderer.explain(measure, PACK.explanations()))
                .isEqualTo("Rent you could not realise is left out before this house's annual value is found.");
    }

    @Test
    void explain_whenCessIsCharged_namesTheTaxItIsOnAndTheCess() {
        // Given — CessNode:63: in is tax with surcharge, out the cess
        TraceLine cess = line(new RuleId("RATE-HEALTH-EDUCATION-CESS"), MoneyValue.of(Money.of("102500")),
                MoneyValue.of(Money.of("4100")));

        // When / Then
        assertThat(ExplanationRenderer.explain(cess, PACK.explanations()))
                .isEqualTo("On tax and surcharge of ₹1,02,500, the health and education cess is ₹4,100.");
    }

    @Test
    void explain_whenASlabLinesFigureIsTaxByBasis_rendersTheSentenceThatNamesNoFigure() {
        // Given — TaxByBasisNode:97: out is the tax by basis, not money (W-325c C1a)
        TraceLine slab = line(new RuleId("RATE-SLAB-NEW-TAX-REGIME"), MoneyValue.of(Money.of("1200000")),
                TaxByBasis.atSlabRatesOnly(Money.of("60000")));

        // When / Then
        assertThat(ExplanationRenderer.explain(slab, PACK.explanations()))
                .isEqualTo("This is the tax on your income at the default regime's slab rates.");
    }

    @Test
    void explain_whenTheReturnIsLate_saysWhatTheInterestIsForAndKeepsThePeriod() {
        // Given — InterestAndFeeNode:211; the reason carries the period (W-325c C2a)
        TraceLine late = line(new RuleId("INT-LATE-RETURN"), MoneyValue.of(Money.of("87700")), MoneyValue.of(Money.of("877")))
                .withLimitedBy("one month from 2027-08-01");

        // When / Then
        assertThat(ExplanationRenderer.explain(late, PACK.explanations())).isEqualTo(
                "Interest of ₹877 is charged because the return was furnished after its due date. one month from 2027-08-01");
    }

    @Test
    void explain_whenAChargeIsNotDeducted_namesTheChargeAndTheNilDeducted() {
        // Given — CapitalGainsCharges:49: in is the charge, out nil under the NOT_DEDUCTED limb (W-325d C2a)
        TraceLine stt = line(new RuleId("CG-CHARGE-STT"), MoneyValue.of(Money.of("500")), MoneyValue.of(Money.ZERO))
                .withLimitedBy("STT on the TRANSFER: NOT_DEDUCTED");

        // When / Then
        assertThat(ExplanationRenderer.explain(stt, PACK.explanations())).isEqualTo(
                "Of the ₹500 of securities transaction tax, ₹0 is deducted in working out the gain. STT on the TRANSFER:"
                        + " NOT_DEDUCTED");
    }

    @Test
    void explain_whenACostIsIndexed_namesTheGainBeforeAndAfter() {
        // Given — CapitalGainsIndexation:78: in is the gain, out the gain with the cost indexed
        TraceLine indexed = line(new RuleId("CG-INDEXED-COST-LAND-BUILDING"), MoneyValue.of(Money.of("1000000")),
                MoneyValue.of(Money.of("600000")));

        // When / Then
        assertThat(ExplanationRenderer.explain(indexed, PACK.explanations()))
                .isEqualTo("With its cost indexed for inflation, the ₹10,00,000 gain is ₹6,00,000.");
    }

    @Test
    void explain_whenSetOffWithinCapitalGainsIsOnAnIncomeLine_describesItWithoutAFigure() {
        // Given — CapitalGainsNode:142: one rule on INCOME, MEASURE and SET_OFF lines, one sentence for all (W-325c C1a)
        RuleId setOff = new RuleId("SETOFF-WITHIN-CAPITAL-GAINS");
        TraceLine income = TraceLine.of(1, new NodeId("capitalgains.income"), setOff, StepKind.INCOME,
                MoneyValue.of(Money.of("150000")), MoneyValue.of(Money.of("150000")), PACK.rule(setOff).headProvenance())
                .withLimitedBy("LONG_TERM_LISTED_EQUITY");

        // When / Then
        assertThat(ExplanationRenderer.explain(income, PACK.explanations())).isEqualTo(
                "This line totals your capital gains and losses of each kind and sets a loss against gains in the order"
                        + " shown. LONG_TERM_LISTED_EQUITY");
    }

    @Test
    void explain_whenARegimeDisallowsTheRule_saysSoInWordsAndLeavesTheProvisionToLimitedBy() {
        // Given — LoanInterestDeduction under the default regime: whyNot is the audit text, the reason its facts
        TraceLine barred = line(new RuleId("DED-EDUCATION-LOAN"), MoneyValue.of(Money.of("40000")), MoneyValue.of(Money.ZERO))
                .withLimitedBy("disallowed in the NEW_TAX_REGIME regime: Income-tax Act, 2025 s.202(2)(a) computes the new"
                        + " tax regime's total income without this deduction (REG-NEW-TAX-REGIME-NO-EXEMPTION)")
                .withReason(new Reason(ReasonCodes.REGIME_DISALLOWS, Map.of("regime", new ReasonFact.RegimeFact(NEW_TAX_REGIME))));

        // When / Then
        assertThat(ExplanationRenderer.explain(barred, PACK.explanations())).isEqualTo(
                "₹0 of the interest on your loan for higher education is deducted. It is not allowed under the default regime.");
    }

    @Test
    void explain_whenTheRowIsWrittenForAnotherRegime_saysTheRuleDoesNotApplyThere() {
        // Given
        TraceLine excluded = hra().withLimitedBy("not in the NEW_TAX_REGIME regime")
                .withReason(new Reason(ReasonCodes.REGIME_EXCLUDES_ROW, Map.of("regime", new ReasonFact.RegimeFact(NEW_TAX_REGIME))));

        // When / Then
        assertThat(ExplanationRenderer.explain(excluded, PACK.explanations())).isEqualTo(
                "Of the ₹2,40,000 house rent allowance you received for this home, ₹1,20,000 is not taxed. This rule does"
                        + " not apply under the default regime.");
    }

    @Test
    void explain_whenTheReasonsRegimeHasNoWord_showsLimitedByRatherThanAPlaceholder() {
        // Given — the year's content with no regime terms
        Explanations noTerms = new Explanations(PACK.explanations().otherLimits(), PACK.explanations().byRule(),
                PACK.explanations().reasons(), Map.of());
        TraceLine barred = hra().withLimitedBy("disallowed in the NEW_TAX_REGIME regime")
                .withReason(new Reason(ReasonCodes.REGIME_DISALLOWS, Map.of("regime", new ReasonFact.RegimeFact(NEW_TAX_REGIME))));

        // When / Then
        assertThat(ExplanationRenderer.explain(barred, noTerms)).isEqualTo(
                "Of the ₹2,40,000 house rent allowance you received for this home, ₹1,20,000 is not taxed. disallowed in the"
                        + " NEW_TAX_REGIME regime");
    }
}
