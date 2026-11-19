package com.thiru.wealthlens.taxplanning.documents.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedField;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import com.thiru.wealthlens.taxplanning.documents.ingest.FieldLocation;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReadingJsonTest {

    @Test
    void fields_whenEveryKindIsWrittenAndRead_comesBackEqual() {
        // Given — one field of each value kind, one inferred (no text, no place), one read but not interpreted
        List<ExtractedField> fields = List.of(
                new ExtractedField("c.row12", Optional.of("1,00,000.50"), Optional.of(new ExtractedValue.MoneyRead(Money.of("100000.50"))),
                        0.9, Optional.of(new FieldLocation(2, 10.5, 20, 30, 4)), Optional.of(new ComponentCode("BASIC_SALARY"))),
                new ExtractedField("c.rowA", Optional.of("Yes"), Optional.of(new ExtractedValue.FlagRead(true)), 1, Optional.empty(),
                        Optional.empty()),
                new ExtractedField("a.period", Optional.of("01-04-2026 to 31-03-2027"), Optional.of(new ExtractedValue.PeriodRead(
                        LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31))), 1, Optional.empty(), Optional.empty()),
                new ExtractedField("a.tan", Optional.of("ABCD12345E"), Optional.of(new ExtractedValue.TextRead("ABCD12345E")), 1,
                        Optional.empty(), Optional.empty()),
                new ExtractedField("c.row9", Optional.empty(), Optional.of(new ExtractedValue.MoneyRead(Money.of("-0.01"))), 0.5,
                        Optional.empty(), Optional.empty()),
                new ExtractedField("c.row8", Optional.of("illegible"), Optional.empty(), 0, Optional.empty(), Optional.empty()));

        // When
        List<ExtractedField> read = ReadingJson.readFields(ReadingJson.writeFields(fields));

        // Then
        assertThat(read).isEqualTo(fields);
    }

    @Test
    void writeFields_whenAnAmountHasPaise_storesItExactlyAsText() {
        // Given — ₹1,00,000.50 must not pass through a binary fraction
        List<ExtractedField> fields = List.of(new ExtractedField("c.row12", Optional.empty(),
                Optional.of(new ExtractedValue.MoneyRead(Money.of("100000.50"))), 1, Optional.empty(), Optional.empty()));

        // When
        String stored = ReadingJson.writeFields(fields);

        // Then
        assertThat(stored).contains("\"amount\":\"100000.50\"").contains("\"kind\":\"MONEY\"");
    }

    @Test
    void values_whenAConfirmationsValuesAreWrittenAndRead_comeBackEqual() {
        // Given
        Map<String, ExtractedValue> accepted = Map.of("c.row12", new ExtractedValue.MoneyRead(Money.of("950000")), "c.rowA",
                new ExtractedValue.FlagRead(false));

        // When
        Map<String, ExtractedValue> read = ReadingJson.readValues(ReadingJson.writeValues(accepted));

        // Then
        assertThat(read).isEqualTo(accepted);
    }

    @Test
    void texts_whenWrittenAndRead_keepTheirOrder() {
        // Given
        List<String> texts = List.of("Total (Rs.)", "a line the parser could not place");

        // When / Then
        assertThat(ReadingJson.readTexts(ReadingJson.writeTexts(texts))).containsExactlyElementsOf(texts);
    }

    @Test
    void readValues_whenAStoredKindIsUnknown_refuses() {
        // Given — a kind this code never wrote
        String stored = "{\"c.row12\":{\"kind\":\"PERCENT\",\"rate\":\"10\"}}";

        // When / Then
        assertThatThrownBy(() -> ReadingJson.readValues(stored)).isInstanceOf(IllegalStateException.class)
                .hasMessage("A stored value of kind PERCENT cannot be read");
    }
}
