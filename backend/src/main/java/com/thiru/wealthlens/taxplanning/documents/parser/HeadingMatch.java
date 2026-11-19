package com.thiru.wealthlens.taxplanning.documents.parser;

import com.thiru.wealthlens.taxplanning.documents.parser.PrintedText.Word;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Finds a heading printed in a table's cell, however the cell wraps or centres it (W-126c2b, D-737 C3a): its words in
 * order, each the next word on the same line or the first word on a later line whose box overlaps the words found so far.
 * Two headings side by side, each wrapping in its own cell, are told apart by where they print, not by the gap between
 * them, which can be narrower than {@link LabelMatch#COLUMN_GAP}.
 */
final class HeadingMatch {

    /** How many lines below its last word a heading's next word may print: a heading wraps to three in Part B. */
    private static final int LINES_BELOW = 3;

    private HeadingMatch() {
    }

    /** The heading's words at the first place at or after {@code from}, and before {@code to}, that no one has claimed. */
    static Optional<List<Word>> find(List<Word> words, int from, int to, String heading, Set<Word> claimed) {
        List<String> tokens = Arrays.stream(heading.trim().split("\\s+")).map(LabelMatch::normal).toList();
        for (int start = from; start < to; start++) {
            Word word = words.get(start);
            if (!claimed.contains(word) && tokens.get(0).startsWith(LabelMatch.normal(word.text()))) {
                Optional<List<Word>> found = extend(words, start, to, tokens, claimed);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<List<Word>> extend(List<Word> words, int start, int to, List<String> tokens, Set<Word> claimed) {
        List<Word> found = new ArrayList<>(List.of(words.get(start)));
        String printed = LabelMatch.normal(words.get(start).text());
        int token = 0;
        while (true) {
            if (printed.equals(tokens.get(token))) {
                token++;
                if (token == tokens.size()) {
                    return Optional.of(found);
                }
                printed = "";
            }
            Optional<String> next = next(words, found, to, claimed, printed, tokens.get(token));
            if (next.isEmpty()) {
                return Optional.empty();
            }
            printed = next.get();
        }
    }

    /** The first candidate that continues {@code token} after {@code printed}, added to {@code found}; what it now prints. */
    private static Optional<String> next(List<Word> words, List<Word> found, int to, Set<Word> claimed, String printed, String token) {
        for (Word candidate : candidates(words, found, to, claimed)) {
            String text = LabelMatch.normal(candidate.text());
            String joined = printed.isEmpty() ? text : LabelMatch.joined(printed, text, token);
            if (joined != null && token.startsWith(joined)) {
                found.add(candidate);
                return Optional.of(joined);
            }
        }
        return Optional.empty();
    }

    /** The next word on the last word's line, if close to it; then, on each line below, the leftmost word under the heading. */
    private static List<Word> candidates(List<Word> words, List<Word> found, int to, Set<Word> claimed) {
        Word last = found.get(found.size() - 1);
        int at = words.indexOf(last) + 1;
        List<Word> candidates = new ArrayList<>();
        if (at < to && isBeside(last, words.get(at)) && !claimed.contains(words.get(at))) {
            candidates.add(words.get(at));
        }
        float left = (float) found.stream().mapToDouble(Word::left).min().orElseThrow();
        float right = (float) found.stream().mapToDouble(Word::right).max().orElseThrow();
        int line = last.line();
        for (; at < to && words.get(at).line() <= last.line() + LINES_BELOW; at++) {
            Word word = words.get(at);
            if (word.line() > line && !claimed.contains(word) && word.right() > left && word.left() < right) {
                candidates.add(word);
                line = word.line();
            }
        }
        return candidates;
    }

    private static boolean isBeside(Word last, Word word) {
        return word.line() == last.line() && word.left() - last.right() <= LabelMatch.COLUMN_GAP;
    }
}
