package com.thiru.wealthlens.taxengine.rules.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@link Declared} — three states, never a silent no (W-090a1). */
class DeclaredTest {

    private static final DeclarationCode DIRECTOR = new DeclarationCode("SYNTHETIC_YES_NO");

    private static ConditionContext answering(Declarations declarations) {
        return ConditionContext.residentIndividualAged(40).answering(declarations);
    }

    @Test
    void holds_whenAnsweredYes_isTrue() {
        // Given
        Declarations yes = new Declarations(Map.of(DIRECTOR, true), Map.of());

        // When / Then
        assertThat(new Declared(DIRECTOR).holds(answering(yes))).isTrue();
    }

    @Test
    void holds_whenAnsweredNo_isFalse() {
        // Given
        Declarations no = new Declarations(Map.of(DIRECTOR, false), Map.of());

        // When / Then
        assertThat(new Declared(DIRECTOR).holds(answering(no))).isFalse();
        assertThat(new Not(new Declared(DIRECTOR)).holds(answering(no))).isTrue();
    }

    @Test
    void holds_whenNotAnswered_refusesNamingTheQuestion() {
        // Given
        Declared declared = new Declared(DIRECTOR);

        // When / Then
        assertThatThrownBy(() -> declared.holds(answering(Declarations.NONE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SYNTHETIC_YES_NO")
                .hasMessageContaining("not a no");
        assertThatThrownBy(() -> new Not(declared).holds(answering(Declarations.NONE)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void holds_whenNoContextAnswersWereGiven_refuses() {
        // Given: the person-only context carries no declarations
        ConditionContext person = ConditionContext.residentIndividualAged(40);

        // When / Then
        assertThatThrownBy(() -> new Declared(DIRECTOR).holds(person))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SYNTHETIC_YES_NO");
    }

    @Test
    void constructor_whenCodeNull_refuses() {
        assertThatThrownBy(() -> new Declared(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Declared must name a declaration");
    }

    @Test
    void describe_namesTheQuestion() {
        assertThat(new Declared(DIRECTOR).describe()).isEqualTo("SYNTHETIC_YES_NO is declared");
    }
}
