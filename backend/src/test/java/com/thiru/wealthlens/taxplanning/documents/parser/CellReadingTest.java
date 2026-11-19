package com.thiru.wealthlens.taxplanning.documents.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** A line's value text read as its kind says (L2, {@code DR 06} G5.1; W-127a): numbers only, Yes or No, words. */
class CellReadingTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "12,00,000.00|1200000.00", "1,50,000|150000.00", "1,00,00,000.5|10000000.50", "50,000|50000.00",
        "1,200,000.00|1200000.00", "1200000|1200000.00", "0.00|0.00", "-15,000.00|-15000.00"})
    void amounts_whenNumbersOnly_readsEachAsRupees(String printed, String rupees) {
        // When / Then
        assertThat(CellReading.amounts(printed, 1)).hasValueSatisfying(values -> assertThat(values)
                .singleElement().isEqualTo(new ExtractedValue.MoneyRead(Money.of(rupees))));
    }

    @Test
    void amounts_whenALineHasTwoColumns_readsOnePerColumnInOrder() {
        // When / Then
        assertThat(CellReading.amounts("1,50,000.00 46,800.00", 2)).contains(List.of(
                new ExtractedValue.MoneyRead(Money.of("150000")), new ExtractedValue.MoneyRead(Money.of("46800"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Rs. 5,000", "5,000/-", "(5,000)", "12,0,000", "1,2345", "5.123", "nil", ""})
    void amounts_whenNotNumbersOnly_readsNothing(String printed) {
        // When / Then — D-NOSILENT: nothing read is never zero
        assertThat(CellReading.amounts(printed, 1)).isEmpty();
    }

    @Test
    void amounts_whenTheNumbersAreFewerThanTheColumns_readsNothing() {
        // When / Then — which column is empty cannot be told from the text alone
        assertThat(CellReading.amounts("1,50,000.00", 2)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"Yes,true", "No,false", "YES,true", "no,false"})
    void yesNo_whenYesOrNo_readsTheFlag(String printed, boolean flag) {
        // When / Then
        assertThat(CellReading.yesNo(printed)).contains(new ExtractedValue.FlagRead(flag));
    }

    @Test
    void yesNo_whenAnythingElse_readsNothing() {
        // When / Then
        assertThat(CellReading.yesNo("Y")).isEmpty();
    }

    @Test
    void period_whenPrintedAsTheFormPrescribes_readsBothDates() {
        // When / Then — "(dd/mm/yyyy) to (dd/mm/yyyy)", Form No. 130 Part A item 8 (W-128b)
        assertThat(CellReading.period("01/04/2026 to 31/03/2027")).contains(new ExtractedValue.PeriodRead(
                java.time.LocalDate.of(2026, 4, 1), java.time.LocalDate.of(2027, 3, 31)));
        assertThat(CellReading.period(" 15/07/2026  TO  31/03/2027 ")).contains(new ExtractedValue.PeriodRead(
                java.time.LocalDate.of(2026, 7, 15), java.time.LocalDate.of(2027, 3, 31)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"01-Apr-2026 to 31-Mar-2027", "01/04/2026", "31/02/2027 to 31/03/2027", "1/4/2026 to 31/03/2027", ""})
    void period_whenNotTwoDatesAsPrescribed_readsNothing(String printed) {
        // When / Then — another format, one date, a day that does not exist, unpadded digits, nothing
        assertThat(CellReading.period(printed)).isEmpty();
    }
}
