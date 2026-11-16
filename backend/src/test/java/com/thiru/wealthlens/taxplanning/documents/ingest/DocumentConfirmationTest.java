package com.thiru.wealthlens.taxplanning.documents.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DocumentConfirmationTest {

    private static final LocalDateTime CONFIRMED = LocalDateTime.of(2027, 6, 15, 11, 0);
    private static final ExtractedValue AMOUNT = new ExtractedValue.MoneyRead(Money.of("75000"));

    @Test
    void new_whenEachFieldIsAcceptedOrRejected_keepsBoth() {
        // When
        DocumentConfirmation confirmation = new DocumentConfirmation("attempt-1", "person", CONFIRMED,
                Map.of("row2b", AMOUNT), Set.of("row1e"));

        // Then
        assertThat(confirmation.acceptedFields()).containsEntry("row2b", AMOUNT);
        assertThat(confirmation.rejectedFields()).containsExactly("row1e");
    }

    @Test
    void new_whenTheMapChangesAfterwards_keepsItsOwnCopy() {
        // Given
        Map<String, ExtractedValue> accepted = new HashMap<>(Map.of("row2b", AMOUNT));
        DocumentConfirmation confirmation = new DocumentConfirmation("attempt-1", "person", CONFIRMED, accepted, Set.of());

        // When
        accepted.put("row3", AMOUNT);

        // Then
        assertThat(confirmation.acceptedFields()).containsOnlyKeys("row2b");
    }

    @Test
    void new_whenAFieldIsBothAcceptedAndRejected_refuses() {
        // When / Then
        assertThatThrownBy(() -> new DocumentConfirmation("attempt-1", "person", CONFIRMED, Map.of("row2b", AMOUNT),
                Set.of("row2b")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A field is accepted or rejected, not both: row2b");
    }

    @Test
    void new_whenNobodyConfirmed_refuses() {
        // When / Then
        assertThatThrownBy(() -> new DocumentConfirmation("attempt-1", " ", CONFIRMED, Map.of(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A confirmation names who confirmed");
    }
}
