package com.thiru.wealthlens.taxplanning.documents.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxplanning.documents.parser.PrintedText.Word;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A table's heading found by its own words, however its cell wraps it (W-126c2b, D-737). Places in points, as a PDF prints. */
class HeadingMatchTest {

    private static Word word(int line, String text, float left, float right) {
        return new Word(1, line, text, left, right, line * 12f, line * 12f + 10);
    }

    private static Set<Word> none() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static List<String> texts(Optional<List<Word>> found) {
        return found.orElseThrow().stream().map(Word::text).toList();
    }

    // The probe's header (D-737): "Amount paid/credited" wraps with its slash on the second line; "Rate of deduction of tax"
    // wraps over three lines, centred, beside it
    private static final List<Word> HEADER = List.of(
            word(2, "Amount", 243, 271), word(2, "paid", 276, 295), word(2, "Rate", 317, 333), word(2, "of", 337, 345),
            word(3, "/credited", 251, 286), word(3, "deduction", 306, 343), word(3, "of", 347, 355),
            word(4, "tax", 327, 339));

    @Test
    void find_whenAHeadingWrapsBesideAnother_takesOnlyTheWordsUnderItself() {
        // When
        Optional<List<Word>> rate = HeadingMatch.find(HEADER, 0, HEADER.size(), "Rate of deduction of tax", none());
        Optional<List<Word>> paid = HeadingMatch.find(HEADER, 0, HEADER.size(), "Amount paid/credited", none());

        // Then
        assertThat(texts(rate)).containsExactly("Rate", "of", "deduction", "of", "tax");
        assertThat(texts(paid)).containsExactly("Amount", "paid", "/credited");
    }

    @Test
    void find_whenTwoHeadingsEndAlikeEightPointsApart_tellsThemApartByWhatIsAboveEach() {
        // Given — the probe's BIN row: "… of Form No. 137" and "… in Form No. 137" side by side, 8 pt apart
        List<Word> words = List.of(
                word(12, "Receipt", 252, 281), word(12, "DDO", 309, 322), word(12, "serial", 326, 353),
                word(13, "numbers", 245, 275), word(13, "of", 280, 289), word(13, "number", 311, 337), word(13, "in", 342, 350),
                word(14, "Form", 241, 260), word(14, "No.", 264, 278), word(14, "137", 283, 297),
                word(14, "Form", 305, 323), word(14, "No.", 328, 342), word(14, "137", 347, 361));

        // When
        Optional<List<Word>> receipt = HeadingMatch.find(words, 0, words.size(), "Receipt numbers of Form No. 137", none());
        Optional<List<Word>> ddo = HeadingMatch.find(words, 0, words.size(), "DDO serial number in Form No. 137", none());

        // Then — each "Form" is the one under its own heading's words
        assertThat(receipt.orElseThrow().get(3).left()).isEqualTo(241f);
        assertThat(ddo.orElseThrow().get(4).left()).isEqualTo(305f);
        assertThat(texts(ddo)).containsExactly("DDO", "serial", "number", "in", "Form", "No.", "137");
    }

    @Test
    void find_whenTheFirstPlaceIsClaimed_takesTheNext() {
        // Given — "Sl. No." printed twice; the first already claimed by an earlier table
        List<Word> words = List.of(word(1, "Sl.", 58, 70), word(1, "No.", 74, 86), word(5, "Sl.", 58, 70), word(5, "No.", 74, 86));
        Set<Word> claimed = none();
        claimed.addAll(words.subList(0, 2));

        // When / Then
        assertThat(HeadingMatch.find(words, 0, words.size(), "Sl. No.", claimed).orElseThrow().get(0).line()).isEqualTo(5);
    }

    @Test
    void find_whenAWordIsMissingOrNotUnderTheHeading_findsNothing() {
        // Given — "tax" printed, but far to the right of "Rate of deduction of"
        List<Word> words = List.of(word(2, "Rate", 317, 333), word(2, "of", 337, 345), word(3, "deduction", 306, 343),
                word(3, "of", 347, 355), word(4, "tax", 500, 512));

        // When / Then
        assertThat(HeadingMatch.find(words, 0, words.size(), "Rate of deduction of tax", none())).isEmpty();
        assertThat(HeadingMatch.find(words, 0, words.size(), "Rate of interest", none())).isEmpty();
    }

    @Test
    void find_whenOnlyBeforeTheRange_findsNothing() {
        // When / Then
        assertThat(HeadingMatch.find(HEADER, 2, HEADER.size(), "Amount paid/credited", none())).isEmpty();
    }
}
