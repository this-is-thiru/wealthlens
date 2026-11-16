package com.thiru.wealthlens.taxengine.api;

import com.thiru.wealthlens.taxengine.rules.filing.CertificateCell;
import com.thiru.wealthlens.taxengine.rules.filing.CertificateField;
import com.thiru.wealthlens.taxengine.rules.filing.CertificateIdentity;
import com.thiru.wealthlens.taxengine.rules.filing.CertificatePart;
import com.thiru.wealthlens.taxengine.rules.filing.CertificateSpec;
import com.thiru.wealthlens.taxengine.rules.id.CertificateCode;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A certificate's printed layout for one year, as a parser outside the engine reads it (W-126a): its parts by heading and its
 * lines by label, each with its row number, value kind, columns and Gazette page.
 *
 * @param title the form's title as printed
 * @param source where the layout was read: the Gazette and its pages
 */
public record CertificateLayout(CertificateCode code, String title, List<PartHeading> parts, List<PrintedLine> lines,
        List<PrintedSum> sums, String source) {

    public CertificateLayout {
        parts = List.copyOf(parts);
        lines = List.copyOf(lines);
        sums = List.copyOf(sums);
    }

    static CertificateLayout of(CertificateSpec spec) {
        return new CertificateLayout(spec.code(), spec.title(), spec.parts().stream().map(PartHeading::of).toList(),
                spec.fields().stream().map(PrintedLine::of).toList(), spec.identities().stream().map(PrintedSum::of).toList(),
                spec.provenance().sourceDescription());
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

    /** A part or a group, by the heading the form prints over it. */
    public record PartHeading(String path, String heading) {

        static PartHeading of(CertificatePart part) {
            return new PartHeading(part.path(), part.heading());
        }
    }

    /**
     * One line, by its printed label.
     *
     * @param kind {@code AMOUNT}, {@code YES_NO}, {@code TEXT}, or {@code NONE} for a heading line with no value
     */
    public record PrintedLine(String path, Optional<String> row, String label, String kind, List<String> columns,
            boolean repeating, int page) {

        public PrintedLine {
            columns = List.copyOf(columns);
        }

        static PrintedLine of(CertificateField field) {
            return new PrintedLine(field.path(), field.row(), field.label(), field.kind().name(), field.columns(),
                    field.repeating(), field.page());
        }
    }
}
