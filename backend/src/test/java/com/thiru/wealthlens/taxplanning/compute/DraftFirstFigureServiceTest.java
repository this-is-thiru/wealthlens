package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** The first figure's refusals and step 1's patch (W-122c, D-722); the computed figures are run in DraftIntegrationTest. */
class DraftFirstFigureServiceTest {

    private static final DraftFirstFigureService SERVICE = new DraftFirstFigureService(null, null);

    private static final LocalDate DUE = LocalDate.of(2027, 7, 31);

    @Test
    void firstFigure_whenAFieldIsMissingOrPayIsNotAboveNil_isRefusedByName() {
        // When / Then — refused before any draft is made: the services it would call are absent here
        assertThatThrownBy(() -> SERVICE.firstFigure("a@b.c", "p", 2026, new DraftFirstFigureRequest(" ", Money.of("1"), DUE)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("/label");
        assertThatThrownBy(() -> SERVICE.firstFigure("a@b.c", "p", 2026, new DraftFirstFigureRequest("L", null, DUE)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("/grossPay");
        assertThatThrownBy(() -> SERVICE.firstFigure("a@b.c", "p", 2026, new DraftFirstFigureRequest("L", Money.ZERO, DUE)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("/grossPay");
        assertThatThrownBy(() -> SERVICE.firstFigure("a@b.c", "p", 2026, new DraftFirstFigureRequest("L", Money.of("1"), null)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("/dueDate");
        assertThatThrownBy(() -> SERVICE.firstFigure("a@b.c", "p", 2026, null))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("/label");
    }

    @Test
    void stepOne_whenGrossPayIsGiven_entersOneSpecialAllowanceTheDueDateAndStepOneAnswered() {
        // When
        JsonNode patch = DraftFirstFigureService.stepOne(new DraftFirstFigureRequest("L", Money.of("1200000"), DUE));

        // Then
        assertThat(patch.size()).isEqualTo(3);
        assertThat(patch.at("/0/path").asString()).isEqualTo("/entered/employments/-");
        assertThat(patch.at("/0/value/components").size()).isEqualTo(1);
        assertThat(patch.at("/0/value/components/0/code/value").asString()).isEqualTo("SPECIAL_ALLOWANCE");
        assertThat(patch.at("/0/value/components/0/annualAmount/amountInRupees").decimalValue())
                .isEqualByComparingTo("1200000");
        assertThat(patch.at("/1/path").asString()).isEqualTo("/filing/dueDate");
        assertThat(patch.at("/1/value").asString()).isEqualTo("2027-07-31");
        assertThat(patch.at("/2/value").asString()).isEqualTo("PAY");
    }
}
