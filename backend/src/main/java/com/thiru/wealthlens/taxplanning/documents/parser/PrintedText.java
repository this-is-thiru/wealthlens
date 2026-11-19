package com.thiru.wealthlens.taxplanning.documents.parser;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.ImageRenderInfo;
import com.itextpdf.text.pdf.parser.LineSegment;
import com.itextpdf.text.pdf.parser.PdfReaderContentParser;
import com.itextpdf.text.pdf.parser.RenderListener;
import com.itextpdf.text.pdf.parser.TextRenderInfo;
import com.itextpdf.text.pdf.parser.Vector;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A PDF's printed words with their places, page by page, top to bottom and left to right (W-126b): what L1 matches labels
 * against ({@code DR 06} G5.1). Places are in points, measured from the page's top left.
 */
record PrintedText(List<Word> words) {

    PrintedText {
        words = List.copyOf(words);
    }

    /** Two runs of text whose baselines are this close, in points, are on one line. */
    private static final float SAME_LINE = 2.0f;
    /** Two runs this close, in points, with no space between them, are one word. */
    private static final float SAME_WORD = 0.8f;

    /** One printed word: its page, its line's number in the whole document, its text and its box. */
    record Word(int page, int line, String text, float left, float right, float top, float bottom) {
    }

    /** One run of text as the PDF draws it. */
    private record Run(String text, float left, float right, float baseline, float top, float bottom) {
    }


    /** @throws IOException where a page's content cannot be read */
    static PrintedText of(PdfReader reader) throws IOException {
        List<Word> words = new ArrayList<>();
        PdfReaderContentParser parser = new PdfReaderContentParser(reader);
        int line = 0;
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            float height = reader.getPageSize(page).getHeight();
            List<Run> runs = parser.processContent(page, new Runs(height, new ArrayList<>())).runs();
            line = addLines(words, runs, page, line);
        }
        return new PrintedText(words);
    }

    private static int addLines(List<Word> words, List<Run> runs, int page, int firstLine) {
        List<Run> ordered = new ArrayList<>(runs);
        ordered.sort(Comparator.comparingDouble(Run::baseline).thenComparingDouble(Run::left));
        int line = firstLine;
        float lineBaseline = Float.NaN;
        List<Run> current = new ArrayList<>();
        for (Run run : ordered) {
            if (!current.isEmpty() && Math.abs(run.baseline() - lineBaseline) > SAME_LINE) {
                addWords(words, current, page, line++);
                current.clear();
            }
            if (current.isEmpty()) {
                lineBaseline = run.baseline();
            }
            current.add(run);
        }
        if (!current.isEmpty()) {
            addWords(words, current, page, line++);
        }
        return line;
    }

    private static void addWords(List<Word> words, List<Run> line, int page, int lineNumber) {
        line.sort(Comparator.comparingDouble(Run::left));
        Word previous = null;
        boolean spaceBefore = true;
        for (Run run : line) {
            String text = run.text();
            float width = (run.right() - run.left()) / Math.max(1, text.length());
            int start = 0;
            while (start < text.length()) {
                if (Character.isWhitespace(text.charAt(start))) {
                    spaceBefore = true;
                    start++;
                    continue;
                }
                int end = start;
                while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
                    end++;
                }
                Word word = new Word(page, lineNumber, text.substring(start, end), run.left() + width * start,
                        run.left() + width * end, run.top(), run.bottom());
                if (previous != null && !spaceBefore && word.left() - previous.right() < SAME_WORD) {
                    word = new Word(page, lineNumber, previous.text() + word.text(), previous.left(), word.right(),
                            Math.min(previous.top(), word.top()), Math.max(previous.bottom(), word.bottom()));
                    words.remove(words.size() - 1);
                }
                words.add(word);
                previous = word;
                spaceBefore = false;
                start = end;
            }
            spaceBefore = spaceBefore || text.isEmpty();
        }
    }

    /** Gathers each run the page draws, its box measured from the page's top. */
    private record Runs(float pageHeight, List<Run> runs) implements RenderListener {

        @Override
        public void renderText(TextRenderInfo info) {
            LineSegment baseline = info.getBaseline();
            float y = baseline.getStartPoint().get(Vector.I2);
            runs.add(new Run(info.getText(), baseline.getStartPoint().get(Vector.I1), baseline.getEndPoint().get(Vector.I1),
                    pageHeight - y, pageHeight - info.getAscentLine().getStartPoint().get(Vector.I2),
                    pageHeight - info.getDescentLine().getStartPoint().get(Vector.I2)));
        }

        @Override
        public void beginTextBlock() {
            // Runs carry their own places; a text block adds nothing to them
        }

        @Override
        public void endTextBlock() {
            // As beginTextBlock
        }

        @Override
        public void renderImage(ImageRenderInfo info) {
            // Images hold no text for L1; a page of images alone is a scan (DR 13 row 72)
        }
    }
}
