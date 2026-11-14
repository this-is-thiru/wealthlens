package com.thiru.wealthlens.taxengine.rules.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link DeclaredAmountAbove} — the law's "exceeding": strictly above (W-090a1). Synthetic figures: limit ₹1,000; ₹1,000
 * is equal so not above; ₹1,100 is above.
 */
class DeclaredAmountAboveTest {

    private static final DeclarationCode AMOUNT = new DeclarationCode("SYNTHETIC_AMOUNT");
    private static final DeclaredAmountAbove ABOVE_1000 = new DeclaredAmountAbove(AMOUNT, Money.of("1000"));

    private static ConditionContext declaring(String rupees) {
        return ConditionContext.residentIndividualAged(40)
                .answering(new Declarations(Map.of(), Map.of(AMOUNT, Money.of(rupees))));
    }

    @Test
    void holds_whenAmountEqualsTheLimit_isFalse() {
        assertThat(ABOVE_1000.holds(declaring("1000"))).isFalse();
        assertThat(new Not(ABOVE_1000).holds(declaring("1000"))).isTrue();
    }

    @Test
    void holds_whenAmountExceedsTheLimit_isTrue() {
        assertThat(ABOVE_1000.holds(declaring("1100"))).isTrue();
    }

    @Test
    void holds_whenNotAnswered_refusesNamingTheQuestion() {
        // Given
        ConditionContext unanswered = ConditionContext.residentIndividualAged(40).answering(Declarations.NONE);

        // When / Then
        assertThatThrownBy(() -> ABOVE_1000.holds(unanswered))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SYNTHETIC_AMOUNT")
                .hasMessageContaining("not nil");
    }

    @Test
    void holds_whenAnsweredYesOrNoInstead_refuses() {
        // Given
        ConditionContext yesNo = ConditionContext.residentIndividualAged(40)
                .answering(new Declarations(Map.of(AMOUNT, true), Map.of()));

        // When / Then
        assertThatThrownBy(() -> ABOVE_1000.holds(yesNo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SYNTHETIC_AMOUNT has no amount answer");
    }

    @Test
    void constructor_whenCodeOrLimitNull_refuses() {
        assertThatThrownBy(() -> new DeclaredAmountAbove(null, Money.of("1000")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeclaredAmountAbove(AMOUNT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void describe_namesTheQuestionAndTheLimit() {
        assertThat(ABOVE_1000.describe()).startsWith("SYNTHETIC_AMOUNT is above ");
    }
}
