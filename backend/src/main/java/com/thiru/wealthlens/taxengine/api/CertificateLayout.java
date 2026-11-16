package com.thiru.wealthlens.taxengine.api;

import com.thiru.wealthlens.taxengine.rules.filing.CertificateField;
import com.thiru.wealthlens.taxengine.rules.filing.CertificatePart;
import com.thiru.wealthlens.taxengine.rules.filing.CertificateSpec;
import com.thiru.wealthlens.taxengine.rules.id.CertificateCode;
import java.util.List;
import java.util.Optional;

/**
 * A certificate's printed layout for one year, as a parser outside the engine reads it (W-126a): its parts by heading and its
 * lines by label, each with its row number, value kind, columns and Gazette page.
 *
 * @param title the form's title as printed
 * @param source where the layout was read: the Gazette and its pages
 */
public record CertificateLayout(CertificateCode code, String title, List<PartHeading> parts, List<PrintedLine> lines,
        String source) {

    public CertificateLayout {
        parts = List.copyOf(parts);
        lines = List.copyOf(lines);
    }

    static CertificateLayout of(CertificateSpec spec) {
        return new CertificateLayout(spec.code(), spec.title(), spec.parts().stream().map(PartHeading::of).toList(),
                spec.fields().stream().map(PrintedLine::of).toList(), spec.provenance().sourceDescription());
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
