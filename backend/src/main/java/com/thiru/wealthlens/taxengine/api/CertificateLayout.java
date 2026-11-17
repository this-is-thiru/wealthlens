package com.thiru.wealthlens.taxengine.api;

import com.thiru.wealthlens.taxengine.rules.filing.CertificateAgreement;
import com.thiru.wealthlens.taxengine.rules.filing.CertificateCell;
import com.thiru.wealthlens.taxengine.rules.filing.CertificateComparison;
import com.thiru.wealthlens.taxengine.rules.filing.CertificateField;
import com.thiru.wealthlens.taxengine.rules.filing.CertificateIdentity;
import com.thiru.wealthlens.taxengine.rules.filing.CertificatePart;
import com.thiru.wealthlens.taxengine.rules.filing.CertificateSpec;
import com.thiru.wealthlens.taxengine.rules.filing.CertificateTable;
import com.thiru.wealthlens.taxengine.rules.filing.TableColumn;
import com.thiru.wealthlens.taxengine.rules.filing.TableTotal;
import com.thiru.wealthlens.taxengine.rules.id.CertificateCode;
import com.thiru.wealthlens.taxengine.rules.id.CertificateFact;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A certificate's printed layout for one year, as a parser outside the engine reads it (W-126a): its parts by heading and its
 * lines by label, each with its row number, value kind, columns and Gazette page.
 *
 * @param title the form's title as printed
 * @param tables the tables whose rows the issuer writes, each found by its title and column headings (W-126c2a)
 * @param agreements the amounts that should be near each other though no printed sum says so (W-128b)
 * @param comparisons the amounts the engine's computation also produces (W-130a)
 * @param source where the layout was read: the Gazette and its pages
 */
public record CertificateLayout(CertificateCode code, String title, List<PartHeading> parts, List<PrintedLine> lines,
        List<PrintedSum> sums, List<PrintedTable> tables, List<PrintedAgreement> agreements, List<PrintedComparison> comparisons,
        String source) {

    public CertificateLayout {
        parts = List.copyOf(parts);
        lines = List.copyOf(lines);
        sums = List.copyOf(sums);
        tables = List.copyOf(tables);
        agreements = List.copyOf(agreements);
        comparisons = List.copyOf(comparisons);
    }

    static CertificateLayout of(CertificateSpec spec) {
        return new CertificateLayout(spec.code(), spec.title(), spec.parts().stream().map(PartHeading::of).toList(),
                spec.fields().stream().map(PrintedLine::of).toList(), spec.identities().stream().map(PrintedSum::of).toList(),
                spec.tables().stream().map(PrintedTable::of).toList(), spec.agreements().stream().map(PrintedAgreement::of).toList(),
                spec.comparisons().stream().map(PrintedComparison::of).toList(), spec.provenance().sourceDescription());
    }

    /**
     * A cell's path: the line's own for a line of one cell, else the line's path and the column's heading in camel case —
     * "Gross Amount" under {@code row10a} is {@code row10a.grossAmount} (W-127a, W-127b). The one place it is made.
     */
    public static String cellPath(String line, Optional<String> column) {
        return column.map(heading -> {
            String[] words = heading.trim().split("\\s+");
            StringBuilder key = new StringBuilder(line).append('.').append(words[0].toLowerCase(Locale.ROOT));
            for (int at = 1; at < words.length; at++) {
                key.append(Character.toUpperCase(words[at].charAt(0))).append(words[at].substring(1).toLowerCase(Locale.ROOT));
            }
            return key.toString();
        }).orElse(line);
    }

    /**
     * Two amounts that should be near each other (W-128b): each by its path in a reading, how far apart they may be, and why.
     */
    public record PrintedAgreement(String printed, String near, Money tolerance, String basis) {

        static PrintedAgreement of(CertificateAgreement agreement) {
            return new PrintedAgreement(agreement.printed(), agreement.near(), agreement.tolerance(), agreement.basis());
        }
    }

    /**
     * An amount the engine's computation also produces (W-130a): a node's output, or one of its totals ({@code BILL}).
     *
     * @param printed the amount's path in a reading: the line's, or the line and its column ({@link #cellPath})
     * @param reason the reconciliation reason that explains the pairing, where one does
     * @param whenHigher the reason a certificate figure above the computation's has (W-130b)
     * @param whenLower the reason a certificate figure below the computation's has (W-130b)
     */
    public record PrintedComparison(String printed, Optional<String> node, Optional<String> total, Optional<String> reason,
            Optional<String> whenHigher, Optional<String> whenLower) {

        static PrintedComparison of(CertificateComparison comparison) {
            return new PrintedComparison(cellPath(comparison.printed(), comparison.column()), comparison.node().map(NodeId::value),
                    comparison.total().map(Enum::name), comparison.reason(), comparison.whenHigher(), comparison.whenLower());
        }
    }

    /**
     * A sum the form prints: its target equals its terms, each added or taken away.
     *
     * @param page the Gazette page it is printed on
     */
    public record PrintedSum(SumCell target, List<SumCell> terms, int page) {

        public PrintedSum {
            terms = List.copyOf(terms);
        }

        static PrintedSum of(CertificateIdentity identity) {
            return new PrintedSum(SumCell.of(identity.target(), 1),
                    identity.terms().stream().map(term -> SumCell.of(term.cell(), term.sign())).toList(), identity.page());
        }
    }

    /**
     * One cell of a printed sum.
     *
     * @param line the line it is printed on
     * @param path the cell's path ({@link #cellPath})
     * @param sign +1 or -1; the target's is +1
     */
    public record SumCell(String line, String path, int sign) {

        static SumCell of(CertificateCell cell, int sign) {
            return new SumCell(cell.line(), cellPath(cell.line(), cell.column()), sign);
        }
    }

    /**
     * A table, by the lines printed over it and its column headings; its rows carry no label (W-126c2a).
     *
     * @param title the lines printed over it; none where the table is found by its first heading (W-126e3a)
     * @param totalLabel the label of the row that prints the totals, e.g. "Total"; none for a table of one row
     * @param totalCells that row's cells in printed order, each the keys of the columns it spans; none without the row
     * @param page the Gazette page the table starts on
     */
    public record PrintedTable(String path, List<String> title, List<PrintedColumn> columns, Optional<String> totalLabel,
            List<List<String>> totalCells, int page) {

        public PrintedTable {
            title = List.copyOf(title);
            columns = List.copyOf(columns);
            totalCells = totalCells.stream().map(List::copyOf).toList();
        }

        static PrintedTable of(CertificateTable table) {
            return new PrintedTable(table.path(), table.title(), table.columns().stream().map(PrintedColumn::of).toList(),
                    table.total().map(TableTotal::label), table.total().map(TableTotal::cells).orElse(List.of()), table.page());
        }
    }

    /**
     * One column of a table.
     *
     * @param key names the column's cells in a reading
     * @param kind {@code AMOUNT}, {@code YES_NO} or {@code TEXT}
     * @param group the heading printed over this column and its neighbours, where there is one
     * @param captions the words printed inside the column's cell before each value, in order (W-126e3a)
     */
    public record PrintedColumn(String heading, String key, String kind, Optional<String> group, List<String> captions) {

        public PrintedColumn {
            captions = List.copyOf(captions);
        }

        static PrintedColumn of(TableColumn column) {
            return new PrintedColumn(column.heading(), column.key(), column.kind().name(), column.group(), column.captions());
        }
    }

    /** A part or a group, by the heading the form prints over it. */
    public record PartHeading(String path, String heading) {

        static PartHeading of(CertificatePart part) {
            return new PartHeading(part.path(), part.heading());
        }
    }

    /**
     * One line, by its printed label.
     *
     * @param kind {@code AMOUNT}, {@code YES_NO}, {@code PERIOD}, {@code TEXT}, or {@code NONE} for a heading line with no value
     * @param fact what the line states, where a check needs to find it, e.g. {@code STANDARD_DEDUCTION} (W-128c)
     */
    public record PrintedLine(String path, Optional<String> row, String label, String kind, List<String> columns,
            boolean repeating, int page, Optional<String> fact) {

        public PrintedLine {
            columns = List.copyOf(columns);
        }

        static PrintedLine of(CertificateField field) {
            return new PrintedLine(field.path(), field.row(), field.label(), field.kind().name(), field.columns(),
                    field.repeating(), field.page(), field.fact().map(CertificateFact::value));
        }
    }
}
