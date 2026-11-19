package com.thiru.wealthlens.taxplanning.documents.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ParseAttemptTest {

    private static final LocalDateTime STARTED = LocalDateTime.of(2027, 6, 15, 10, 30);
    private static final ParserRun RUN = new ParserRun("form-130", "1", STARTED, STARTED.plusSeconds(2));

    @Test
    void new_whenPartial_keepsWhatWasReadAndWhatWasNot() {
        // When
        ParseAttempt attempt = new ParseAttempt("attempt-1", "doc-1", RUN, ParseOutcome.PARTIAL, List.of(field("row1a")),
                List.of("Perquisites as per Form 123: see annexure"), List.of());

        // Then
        assertThat(attempt.extractedFields()).extracting(ExtractedField::fieldPath).containsExactly("row1a");
        assertThat(attempt.unmappedText()).containsExactly("Perquisites as per Form 123: see annexure");
    }

    @Test
    void new_whenTheListsChangeAfterwards_keepsItsOwnCopy() {
        // Given
        List<ExtractedField> fields = new ArrayList<>(List.of(field("row1a")));
        ParseAttempt attempt = new ParseAttempt("attempt-1", "doc-1", RUN, ParseOutcome.SUCCESS, fields, List.of(),
                List.of());

        // When
        fields.add(field("row1b"));

        // Then
        assertThat(attempt.extractedFields()).hasSize(1);
        assertThatThrownBy(() -> attempt.extractedFields().add(field("row1c")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void new_whenFailedWithItsReason_isAValueNotAnError() {
        // When — DR 07 X2.5: a parser never throws on a bad document
        ParseAttempt attempt = new ParseAttempt("attempt-1", "doc-1", RUN, ParseOutcome.FAILED, List.of(), List.of(),
                List.of("no text layer"));

        // Then
        assertThat(attempt.parserNotes()).containsExactly("no text layer");
    }

    @Test
    void new_whenFailedWithoutAReason_refuses() {
        // When / Then
        assertThatThrownBy(() -> new ParseAttempt("attempt-1", "doc-1", RUN, ParseOutcome.FAILED, List.of(), List.of(),
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A failed attempt says why and reads no field");
    }

    @Test
    void new_whenFailedButAFieldWasRead_refuses() {
        // When / Then
        assertThatThrownBy(() -> new ParseAttempt("attempt-1", "doc-1", RUN, ParseOutcome.FAILED, List.of(field("row1a")),
                List.of(), List.of("timed out")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A failed attempt says why and reads no field");
    }

    @Test
    void new_whenASuccessLeftTextUnread_refuses() {
        // When / Then
        assertThatThrownBy(() -> new ParseAttempt("attempt-1", "doc-1", RUN, ParseOutcome.SUCCESS, List.of(field("row1a")),
                List.of("illegible"), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An attempt that left text unread is partial, not a success");
    }

    @Test
    void new_whenAFieldIsReadTwice_refuses() {
        // When / Then
        assertThatThrownBy(() -> new ParseAttempt("attempt-1", "doc-1", RUN, ParseOutcome.SUCCESS,
                List.of(field("row1a"), field("row1a")), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An attempt reads each field once: row1a twice");
    }

    @Test
    void newRun_whenItFinishesBeforeItStarted_refuses() {
        // When / Then
        assertThatThrownBy(() -> new ParserRun("form-130", "1", STARTED, STARTED.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A parse cannot finish before it started");
    }

    @Test
    void newRun_whenTheParserVersionIsBlank_refuses() {
        // When / Then
        assertThatThrownBy(() -> new ParserRun("form-130", "", STARTED, STARTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A parse names its parser's version");
    }

    private static ExtractedField field(String path) {
        return new ExtractedField(path, Optional.of("1"), Optional.of(new ExtractedValue.FlagRead(true)), 1,
                Optional.empty(), Optional.empty());
    }
}
