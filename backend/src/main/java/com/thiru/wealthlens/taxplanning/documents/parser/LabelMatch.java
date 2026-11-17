package com.thiru.wealthlens.taxplanning.documents.parser;

import com.thiru.wealthlens.taxplanning.documents.parser.PrintedText.Word;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds a printed label among a document's words (W-126b, {@code DR 06} G5.1 L1): the label's words in order, allowing a
 * line break anywhere, a word broken across lines with a hyphen, curly or straight quotes and any spacing. A label ends
 * where its cell does — at the end of its line or before a column's gap — so "Total" does not match "Total amount …". The
 * words printed to the right of the label, past the gap, are its value cells.
 */
final class LabelMatch {

    /** A gap wider than this, in points, between two words on one line separates two cells. */
    static final float COLUMN_GAP = 12f;
    /** A row's own marker printed before its label: "(a)", "1." or "A". */
    private static final Pattern MARKER = Pattern.compile("^\\(?[0-9A-Za-z]{1,3}\\)?\\.?$");
    /** A marker fused to the label's first word, as the Gazette prints "(e)Reported". */
    private static final Pattern FUSED_MARKER = Pattern.compile("^\\([a-z]\\)(.+)$");

    private LabelMatch() {
    }

    /**
     * One place a label was found.
     *
     * @param label the label's words
     * @param values the words in its value cells, on the label's lines
     * @param end the index after the last word of the label's last line
     */
    record Found(List<Word> label, List<Word> values, int end) {
    }

    /** The first place at or after {@code from} where {@code label} is printed. */
    static Optional<Found> find(List<Word> words, int from, String label) {
        List<String> tokens = Arrays.stream(label.trim().split("\\s+")).map(LabelMatch::normal).toList();
        for (int start = from; start < words.size(); start++) {
            if (startsLabel(words, start, tokens.get(0))) {
                Optional<Found> found = extend(words, start, tokens);
                if (found.isPresent()) {
                    return Optional.of(withMarker(words, start, found.get()));
                }
            }
        }
        return Optional.empty();
    }

    /** The row's own number, printed before the label on its line, is the label's too: it belongs to no value. */
    private static Found withMarker(List<Word> words, int start, Found found) {
        if (start == 0) {
            return found;
        }
        Word before = words.get(start - 1);
        if (before.line() != words.get(start).line() || !MARKER.matcher(before.text()).matches()) {
            return found;
        }
        List<Word> label = new ArrayList<>(List.of(before));
        label.addAll(found.label());
        return new Found(label, found.values(), found.end());
    }

    private static boolean startsLabel(List<Word> words, int start, String first) {
        Word word = words.get(start);
        String text = normal(word.text());
        Matcher fused = FUSED_MARKER.matcher(text);
        if (!text.equals(first) && !(fused.matches() && fused.group(1).equals(first))) {
            return false;
        }
        if (start == 0 || words.get(start - 1).line() != word.line()) {
            return true;
        }
        Word before = words.get(start - 1);
        return word.left() - before.right() > COLUMN_GAP || MARKER.matcher(before.text()).matches();
    }

    private static Optional<Found> extend(List<Word> words, int start, List<String> tokens) {
        Word first = words.get(start);
        List<Word> label = new ArrayList<>(List.of(first));
        List<Word> values = new ArrayList<>();
        int at = start + 1;
        int matched = 1;
        while (matched < tokens.size()) {
            if (at >= words.size()) {
                return Optional.empty();
            }
            Word word = words.get(at);
            Word last = label.get(label.size() - 1);
            if (word.line() == last.line() && word.left() - last.right() > COLUMN_GAP) {
                values.add(word);
                at++;
                continue;
            }
            at = take(words, at, tokens.get(matched), first, label, values);
            if (at < 0) {
                return Optional.empty();
            }
            matched++;
        }
        return ended(words, at, label, values);
    }

    /**
     * Takes the words that print {@code token} at {@code at} into {@code label} — one, or more for a word the layout broke
     * across lines, with a hyphen that is not the word's own or with none, as a long formula breaks; the value cells
     * printed after a broken part, on its line, go to {@code values}. Answers the index after them, or -1 when they do
     * not print the token.
     */
    private static int take(List<Word> words, int at, String token, Word first, List<Word> label, List<Word> values) {
        Word word = words.get(at);
        Word last = label.get(label.size() - 1);
        if (word.line() != last.line() && word.left() >= first.left() + COLUMN_GAP) {
            return -1;
        }
        List<Word> parts = new ArrayList<>(List.of(word));
        List<Word> skipped = new ArrayList<>();
        String printed = normal(word.text());
        int next = at + 1;
        while (!printed.equals(token)) {
            Word previous = parts.get(parts.size() - 1);
            while (next < words.size() && words.get(next).line() == previous.line()
                    && words.get(next).left() - previous.right() > COLUMN_GAP) {
                skipped.add(words.get(next++));
            }
            if (next >= words.size() || words.get(next).line() == previous.line()) {
                return -1;
            }
            String joined = joined(printed, normal(words.get(next).text()), token);
            if (joined == null) {
                return -1;
            }
            printed = joined;
            parts.add(words.get(next++));
        }
        label.addAll(parts);
        values.addAll(skipped);
        return next;
    }

    /** {@code printed} and {@code next} as one word of {@code token}, kept or rid of a break's hyphen; null if neither is. */
    static String joined(String printed, String next, String token) {
        if (token.startsWith(printed + next)) {
            return printed + next;
        }
        String unbroken = printed.endsWith("-") ? printed.substring(0, printed.length() - 1) + next : null;
        return unbroken != null && token.startsWith(unbroken) ? unbroken : null;
    }

    /** The label ends at its cell's edge; the rest of its last line is its value cells. */
    private static Optional<Found> ended(List<Word> words, int at, List<Word> label, List<Word> values) {
        Word last = label.get(label.size() - 1);
        if (at < words.size() && words.get(at).line() == last.line() && words.get(at).left() - last.right() <= COLUMN_GAP) {
            return Optional.empty();
        }
        int end = at;
        while (end < words.size() && words.get(end).line() == last.line()) {
            values.add(words.get(end));
            end++;
        }
        return Optional.of(new Found(label, values, end));
    }

    /** U+2010, the hyphen the Gazette's text layer prints in "Chapter VI-A" where issuers print an ASCII one (W-126e2). */
    private static final char HYPHEN = (char) 0x2010;

    /** Quotes alike, so a curly and a straight quote match; hyphens alike, so the Gazette's and an issuer's match. */
    static String normal(String text) {
        return text.replace('“', '"').replace('”', '"').replace('‘', '\'').replace('’', '\'').replace(HYPHEN, '-');
    }
}
