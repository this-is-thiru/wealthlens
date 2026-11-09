package com.thiru.wealthlens.taxengine.rules.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@link Declarations} and how {@link ConditionContext} carries it (W-090a1). */
class DeclarationsTest {

    private static final DeclarationCode QUESTION = new DeclarationCode("SYNTHETIC_YES_NO");

    @Test
    void constructor_whenAQuestionIsAnsweredBothWays_refuses() {
        assertThatThrownBy(() -> new Declarations(Map.of(QUESTION, true), Map.of(QUESTION, Money.of("1"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SYNTHETIC_YES_NO is answered both");
    }

    @Test
    void constructor_whenAMapIsNull_refuses() {
        assertThatThrownBy(() -> new Declarations(null, Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Declarations(Map.of(), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_copiesItsMaps() {
        // Given
        Map<DeclarationCode, Boolean> mutable = new HashMap<>(Map.of(QUESTION, true));
        Declarations declarations = new Declarations(mutable, Map.of());

        // When
        mutable.put(QUESTION, false);

        // Then
        assertThat(declarations.isYes(QUESTION)).isTrue();
    }

    @Test
    void about_whenAnItemIsChosen_keepsTheAnswers() {
        // Given
        ConditionContext answered = ConditionContext.residentIndividualAged(40)
                .answering(new Declarations(Map.of(QUESTION, true), Map.of()));

        // When
        ConditionContext aboutItem = answered.about(ItemFacts.NONE);

        // Then
        assertThat(aboutItem.declarations().isYes(QUESTION)).isTrue();
    }
}
