package com.thiru.wealthlens.taxplanning.compute;

import java.util.List;
import java.util.Optional;

/**
 * One stored computation as the PDF report prints it (PRD F10; W-123a): a projection of what was stored and shown, never
 * recomputed, with every line's sentence, rule and figure under its kind. Every text here is either a stored value or a
 * sentence of the year's content, so the template prints nothing of its own ({@code 02-lessons.md} O-20).
 *
 * @param header who, when and what in total
 * @param packNote where the rule data has moved on since the computation, the sentence saying so (W-123a C2a)
 * @param coarseNote where the stored computation was coarse, the sentence saying so with its band (W-122b1 C1a); none for
 *     one stored before W-122b, which said neither
 * @param columns the line table's headings
 * @param sections the lines grouped by kind, each kind in the order it first appears, lines in sequence order
 * @param disclaimer the one disclaimer, filled with the year's instruments (C3a)
 */
public record ComputationReport(Header header, Optional<String> packNote, Optional<String> coarseNote, Columns columns,
        List<Section> sections,
        String disclaimer) {

    public ComputationReport {
        sections = List.copyOf(sections);
    }

    /**
     * The report's first lines.
     *
     * @param title the report's title
     * @param receipt the receipt, in a sentence
     * @param taxYear the tax year, in a sentence
     * @param totalIncomeLabel what {@code totalIncome} is
     * @param totalIncome the total income, as the computation stored it
     * @param finalAmountLabel what {@code finalAmount} is
     * @param finalAmount the amount finally payable, or refundable below nil, as stored
     */
    public record Header(String title, String receipt, String taxYear, String totalIncomeLabel, String totalIncome,
            String finalAmountLabel, String finalAmount) {
    }

    /** The headings of a section's three columns. */
    public record Columns(String step, String rule, String figure) {
    }

    /** One kind of step and its lines. */
    public record Section(String heading, List<Line> lines) {

        public Section {
            lines = List.copyOf(lines);
        }
    }

    /**
     * One trace line.
     *
     * @param sequence its place in the computation
     * @param sentence what it did, as the computation stated it
     * @param rule the rule it applied
     * @param figure what it came to; empty text for a line with no figure
     * @param mark "not verified", or why no status is stated, where either applies (C1a); empty otherwise
     */
    public record Line(int sequence, String sentence, String rule, String figure, Optional<String> mark) {
    }
}
