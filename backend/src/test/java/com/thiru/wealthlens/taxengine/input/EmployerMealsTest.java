package com.thiru.wealthlens.taxengine.input;

import static com.thiru.wealthlens.taxengine.input.InputRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** What {@link EmployerMeals} refuses, one bad field at a time; each message is the record's own (W-240a). */
class EmployerMealsTest {

    private static final MealSetting AT_WORK = MealSetting.AT_WORK;
    private static final Money COST = Money.of("100");
    private static final Money PAID = Money.of("20");

    @Test
    void construct_whenPartlyPaidMealsAtWork_isAccepted() {
        // When
        EmployerMeals meals = new EmployerMeals(0, AT_WORK, 200, COST, PAID);

        // Then
        assertThat(meals.meals()).isEqualTo(200);
    }

    static Stream<Arguments> refusals() {
        String needs = "Meals need their setting, cost and payment";
        String negatives = "An employment position and a number of meals may not be negative";
        return Stream.of(
                refusal("setting null", () -> new EmployerMeals(0, null, 200, COST, PAID), needs),
                refusal("costPerMeal null", () -> new EmployerMeals(0, AT_WORK, 200, null, PAID), needs),
                refusal("paidPerMeal null", () -> new EmployerMeals(0, AT_WORK, 200, COST, null), needs),
                refusal("employment negative", () -> new EmployerMeals(-1, AT_WORK, 200, COST, PAID), negatives),
                refusal("meals negative", () -> new EmployerMeals(0, AT_WORK, -1, COST, PAID), negatives),
                refusal("payment below nil", () -> new EmployerMeals(0, AT_WORK, 200, COST, Money.of("-1")),
                        "The employee's payment per meal must lie between nil and the cost"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
