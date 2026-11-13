package com.thiru.wealthlens.taxengine.rules.condition;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The questions a condition reads, through its compositions (W-091 C3a, D-653, D-657): what lets a form reading an
 * unanswered question be reported undetermined rather than refused.
 */
class DeclarationsReadTest {

    private static final DeclarationCode DIRECTOR = new DeclarationCode("DIRECTOR_IN_COMPANY");
    private static final DeclarationCode FARMING = new DeclarationCode("AGRICULTURAL_INCOME");

    @Test
    void declarationsRead_whenNestedInCompositions_findsEveryQuestion() {
        // Given
        Condition nested = new AllOf(List.of(new IsResident(), new AnyOf(List.of(new Declared(DIRECTOR),
                new Not(new DeclaredAmountAbove(FARMING, Money.of("5000")))))));

        // When / Then
        assertThat(nested.declarationsRead()).containsExactlyInAnyOrder(DIRECTOR, FARMING);
        assertThat(new IsResident().declarationsRead()).isEmpty();
        assertThat(new HasIncomeUnderHead(HeadCode.SALARIES).declarationsRead()).isEmpty();
    }

    @Test
    void isAnswered_whenAnsweredInEitherKind_isTrue() {
        // Given
        Declarations answers = new Declarations(Map.of(DIRECTOR, false), Map.of(FARMING, Money.ZERO));

        // When / Then — "no" and nil are answers; a question not in either map is not
        assertThat(answers.isAnswered(DIRECTOR)).isTrue();
        assertThat(answers.isAnswered(FARMING)).isTrue();
        assertThat(answers.isAnswered(new DeclarationCode("ASSETS_OUTSIDE_INDIA"))).isFalse();
    }
}
