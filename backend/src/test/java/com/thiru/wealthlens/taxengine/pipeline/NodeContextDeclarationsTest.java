package com.thiru.wealthlens.taxengine.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.Declaration;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.FilingFacts;
import com.thiru.wealthlens.taxengine.rules.condition.ConditionContext;
import com.thiru.wealthlens.taxengine.rules.condition.Declared;
import com.thiru.wealthlens.taxengine.rules.condition.DeclaredAmountAbove;
import com.thiru.wealthlens.taxengine.rules.declaration.AnswerKind;
import com.thiru.wealthlens.taxengine.rules.declaration.DeclarationDefinition;
import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.net.URI;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The person's declarations as a run reads them (W-090a1): each answer checked against the year's catalogue, so an
 * answer to a question the year does not ask, or in the wrong kind, is refused by name rather than ignored.
 */
class NodeContextDeclarationsTest {

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);
    private static final RulePack SHIPPED = ShippedRulePacks.compile("rulepacks", Set.of(TY_2026_27)).load(TY_2026_27);
    private static final DeclarationCode DIRECTOR = new DeclarationCode("SYNTHETIC_YES_NO");
    private static final DeclarationCode FARMING = new DeclarationCode("SYNTHETIC_AMOUNT");
    private static final RulePack PACK = withCatalogue(
            definition(DIRECTOR, AnswerKind.YES_NO), definition(FARMING, AnswerKind.AMOUNT));

    private static DeclarationDefinition definition(DeclarationCode code, AnswerKind kind) {
        return new DeclarationDefinition(code, "A synthetic question", kind, DateRange.fromOnwards(LocalDate.of(2026, 4, 1)),
                new Provenance(URI.create("https://egazette.gov.in/WriteReadData/2026/271092.pdf"), "Synthetic, tests only",
                        LocalDate.of(2026, 11, 9), VerificationLevel.UNVERIFIED, "Nothing: a synthetic row"));
    }

    private static RulePack withCatalogue(DeclarationDefinition... rows) {
        Map<DeclarationCode, DeclarationDefinition> catalogue = new LinkedHashMap<>();
        for (DeclarationDefinition row : rows) {
            catalogue.put(row.code(), row);
        }
        return new RulePack(SHIPPED.taxYear(), SHIPPED.layers(), SHIPPED.instruments(), SHIPPED.rules(),
                SHIPPED.ruleSets(), SHIPPED.components(), SHIPPED.regimes(), SHIPPED.disallowanceSets(),
                SHIPPED.conditionSets(), SHIPPED.funds(), catalogue);
    }

    private static ConditionContext conditions(Map<DeclarationCode, Declaration> answers) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        FilingFacts filed = plain.filing();
        EngineInputs inputs = new EngineInputs(plain.taxYear(), plain.regime(), plain.temporal(), plain.entered(),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                new FilingFacts(filed.dueDate(), filed.furnishedOn(), filed.advanceTaxPaid(), filed.taxAtSource(),
                        filed.selfAssessmentPaid(), answers));
        return NodeContext.forRun(PACK, PACK.regimes().get(new RegimeCode("NEW_TAX_REGIME")), inputs).conditions();
    }

    @Test
    void conditions_whenEachQuestionIsAnsweredInItsKind_carriesTheAnswers() {
        // Given
        Map<DeclarationCode, Declaration> answers = Map.of(
                DIRECTOR, new Declaration.YesNo(true), FARMING, new Declaration.Amount(Money.of("1100")));

        // When
        ConditionContext ctx = conditions(answers);

        // Then — ₹1,100 is above ₹1,000 and not above ₹1,100
        assertThat(new Declared(DIRECTOR).holds(ctx)).isTrue();
        assertThat(new DeclaredAmountAbove(FARMING, Money.of("1000")).holds(ctx)).isTrue();
        assertThat(new DeclaredAmountAbove(FARMING, Money.of("1100")).holds(ctx)).isFalse();
    }

    @Test
    void conditions_whenAnsweredNo_carriesTheNo() {
        // When
        ConditionContext ctx = conditions(Map.of(DIRECTOR, new Declaration.YesNo(false)));

        // Then
        assertThat(new Declared(DIRECTOR).holds(ctx)).isFalse();
    }

    @Test
    void conditions_whenNothingIsAnswered_leavesEveryQuestionUnanswered() {
        // When
        ConditionContext ctx = conditions(Map.of());

        // Then — not a no
        assertThatThrownBy(() -> new Declared(DIRECTOR).holds(ctx))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SYNTHETIC_YES_NO has no yes-or-no answer");
    }

    @Test
    void conditions_whenAQuestionTheYearDoesNotAskIsAnswered_refusesNamingIt() {
        // Given
        Map<DeclarationCode, Declaration> answers =
                Map.of(new DeclarationCode("NOT_ASKED"), new Declaration.YesNo(true));

        // When / Then
        assertThatThrownBy(() -> conditions(answers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The declaration NOT_ASKED is not a question 2026-27 asks");
    }

    @Test
    void conditions_whenAYesOrNoQuestionIsAnsweredWithAnAmount_refusesNamingTheKind() {
        // Given
        Map<DeclarationCode, Declaration> answers = Map.of(DIRECTOR, new Declaration.Amount(Money.of("1")));

        // When / Then
        assertThatThrownBy(() -> conditions(answers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SYNTHETIC_YES_NO")
                .hasMessageContaining("asks it as YES_NO");
    }

    @Test
    void conditions_whenAnAmountQuestionIsAnsweredYesOrNo_refusesNamingTheKind() {
        // Given
        Map<DeclarationCode, Declaration> answers = Map.of(FARMING, new Declaration.YesNo(true));

        // When / Then
        assertThatThrownBy(() -> conditions(answers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SYNTHETIC_AMOUNT")
                .hasMessageContaining("asks it as AMOUNT");
    }

    @Test
    void conditions_whenTheShippedPackIsUsed_asksNoQuestionYet() {
        // Given / When / Then — the shipped rows are W-090a2's
        assertThat(SHIPPED.declarations()).isEmpty();
    }
}
