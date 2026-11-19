package com.thiru.wealthlens.taxplanning.documents.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExtractedFieldTest {

    private static final FieldLocation PAGE_TWO = new FieldLocation(2, 40, 300, 120, 12);

    @Test
    void new_whenReadFromThePage_keepsTheTextValueAndPlace() {
        // When
        ExtractedField field = new ExtractedField("partC.annexureI.row2b", Optional.of("75,000"),
                Optional.of(new ExtractedValue.MoneyRead(Money.of("75000"))), 0.97, Optional.of(PAGE_TWO),
                Optional.of(new ComponentCode("BASIC")));

        // Then
        assertThat(field.rawText()).contains("75,000");
        assertThat(field.location()).contains(PAGE_TWO);
        ExtractedValue.MoneyRead read = (ExtractedValue.MoneyRead) field.interpretedValue().orElseThrow();
        assertThat(read.amount()).isEqualByComparingTo(Money.of("75000"));
    }

    @Test
    void new_whenInferredFromTheFormsArithmetic_hasNoTextAndNoPlace() {
        // Given — DR 06 G5.1 L3: row 3 = 1(d) - 2(f), no cell of its own on the page

        // When
        ExtractedField field = new ExtractedField("partC.annexureI.row3", Optional.empty(),
                Optional.of(new ExtractedValue.MoneyRead(Money.of("925000"))), 0.8, Optional.empty(), Optional.empty());

        // Then
        assertThat(field.rawText()).isEmpty();
        assertThat(field.location()).isEmpty();
    }

    @Test
    void new_whenTheTextCouldNotBeInterpreted_holdsNoValue() {
        // When — D-NOSILENT: no value, never zero
        ExtractedField field = new ExtractedField("partB.tds", Optional.of("see annexure"), Optional.empty(), 0.3,
                Optional.of(PAGE_TWO), Optional.empty());

        // Then
        assertThat(field.interpretedValue()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 1.01, Double.NaN})
    void new_whenTheConfidenceIsOutsideZeroToOne_refuses(double confidence) {
        // When / Then
        assertThatThrownBy(() -> new ExtractedField("partB.tds", Optional.empty(), Optional.empty(), confidence,
                Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A confidence is from 0 to 1");
    }

    @Test
    void new_whenTheTextIsNullRatherThanEmpty_refuses() {
        // When / Then
        assertThatThrownBy(() -> new ExtractedField("partB.tds", null, Optional.empty(), 0.5, Optional.empty(),
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A field's text is present or empty, never null");
    }

    @Test
    void new_whenThePathIsBlank_refuses() {
        // When / Then
        assertThatThrownBy(() -> new ExtractedField("", Optional.empty(), Optional.empty(), 0.5, Optional.empty(),
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A field read needs its path");
    }

    @Test
    void newLocation_whenThePageIsZero_refuses() {
        // When / Then
        assertThatThrownBy(() -> new FieldLocation(0, 0, 0, 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A page number starts at 1");
    }

    @Test
    void newLocation_whenTheBoxIsNegative_refuses() {
        // When / Then
        assertThatThrownBy(() -> new FieldLocation(1, 0, 0, -1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A location's box cannot be negative");
    }

    @Test
    void newMoneyRead_whenTheAmountIsMissing_refuses() {
        // When / Then
        assertThatThrownBy(() -> new ExtractedValue.MoneyRead(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An amount read needs its amount");
    }

    @Test
    void newTextRead_whenBlank_refuses() {
        // When / Then
        assertThatThrownBy(() -> new ExtractedValue.TextRead(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Words read cannot be blank");
    }
}
