package com.thiru.wealthlens.taxengine.rules.pack;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** What an explanation may say, and that a pack refuses one that does not fit its rule (W-116, D-671). */
class ExplanationsTest {

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);

    private static final RulePack SHIPPED = ShippedRulePacks.compile("rulepacks", Set.of(TY_2026_27)).load(TY_2026_27);

    private static final RuleId STANDARD = new RuleId("SAL-STANDARD-DEDUCTION-NEW-TAX-REGIME");

    private static final Map<String, String> STANDARD_LIMITS = Map.of("statutory amount", "It is the amount the law sets.",
            "salary", "It is limited by your salary.");

    private static RulePack with(Explanation explanation) {
        return new RulePack(SHIPPED.taxYear(), SHIPPED.layers(), SHIPPED.instruments(), SHIPPED.rules(), SHIPPED.ruleSets(),
                SHIPPED.components(), SHIPPED.regimes(), SHIPPED.disallowanceSets(), SHIPPED.conditionSets(), SHIPPED.funds(),
                SHIPPED.declarations(), SHIPPED.itrForms(), new Explanations(Optional.of("Not these: {limits}."),
                        Map.of(explanation.rule(), explanation), Map.of(), Map.of(), Map.of(), Map.of()));
    }

    @Test
    void explanation_whenTheTextHoldsAFigure_isRefused() {
        // Given / When / Then — content states no number (SC-2)
        assertThatThrownBy(() -> new Explanation(STANDARD, "{out} is up to 75000.", Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("holds a figure");
    }

    @Test
    void explanation_whenAPlaceholderIsMisspelt_isRefused() {
        // Given / When / Then
        assertThatThrownBy(() -> new Explanation(STANDARD, "{output} is deducted.", Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("{output}");
    }

    @Test
    void explanation_whenALimitsSentenceHoldsAPlaceholder_isRefused() {
        // Given / When / Then — a limit's sentence has no figure to fill
        assertThatThrownBy(() -> new Explanation(STANDARD, "{out} is deducted.", Map.of("salary", "Capped at {in}.")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("{in}");
    }

    @Test
    void explanation_whenATextIsBlank_isRefused() {
        // Given / When / Then
        assertThatThrownBy(() -> new Explanation(STANDARD, " ", Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("blank");
    }

    @Test
    void explanations_whenALimitIsExplainedButNoSentenceNamesTheOthers_isRefused() {
        // Given / When / Then — F37.3 needs the sentence for the limits that did not bind
        Explanation explanation = new Explanation(STANDARD, "{out} is deducted.", STANDARD_LIMITS);
        assertThatThrownBy(() -> new Explanations(Optional.empty(), Map.of(STANDARD, explanation), Map.of(), Map.of(), Map.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("otherLimits");
    }

    @Test
    void explanations_whenTheSharedSentenceLacksItsPlaceholder_isRefused() {
        // Given / When / Then
        assertThatThrownBy(() -> new Explanations(Optional.of("Not these: {limit}."), Map.of(), Map.of(), Map.of(), Map.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("{limit}");
    }

    @Test
    void rulePack_whenAnExplanationNamesTheRulesOwnLimits_isAccepted() {
        // Given / When / Then
        assertThatCode(() -> with(new Explanation(STANDARD, "{out} is deducted.", STANDARD_LIMITS)))
                .doesNotThrowAnyException();
    }

    @Test
    void rulePack_whenAnExplanationMissesOneOfItsRulesLimits_isRefusedNamingBoth() {
        // Given / When / Then — a renamed or added limit must not lose its sentence silently
        assertThatThrownBy(() -> with(new Explanation(STANDARD, "{out} is deducted.",
                Map.of("statutory amount", "It is the amount the law sets."))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SAL-STANDARD-DEDUCTION-NEW-TAX-REGIME").hasMessageContaining("[salary, statutory amount]");
    }

    @Test
    void rulePack_whenAnExplanationIsOfNoRuleOfTheYear_isRefused() {
        // Given / When / Then
        assertThatThrownBy(() -> with(new Explanation(new RuleId("SAL-NOT-A-RULE"), "{out} is deducted.", Map.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not a rule of the year");
    }
}
