package com.thiru.wealthlens.taxplanning.documents.parser;

import com.itextpdf.text.Document;
import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.PdfWriter;
import com.thiru.wealthlens.taxengine.api.CertificateLayout;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedLine;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import org.openpdf.text.DocumentException;
import org.xhtmlrenderer.pdf.ITextRenderer;

/**
 * Form No. 130 certificates for the parser's tests, rendered from HTML laid out as the Gazette lays the form out (W-126b,
 * D-729 C4a): a row number column, a label column and a value column, Part A's groups as full-width rows. The labels come
 * from the year's layout (W-126a, tested against the Gazette there); the values and any departures are the test's own.
 */
final class Form130Fixture {

    private Form130Fixture() {
    }

    /**
     * @param values the printed value of a line or of a Part B cell, by path; a line or cell not named prints empty
     * @param labels a label printed differently from the layout's, by path ({@code <br/>} for a line break)
     * @param omitted lines, parts, Part B's tables and their Total rows (by path) the certificate does not print
     */
    static byte[] certificate(CertificateLayout layout, Map<String, String> values, Map<String, String> labels,
            Set<String> omitted) throws DocumentException {
        StringBuilder html = new StringBuilder("<html><head><style>body { font-family: serif; font-size: 10pt; }"
                + " table { width: 100%; border-collapse: collapse; } td { vertical-align: top; padding: 2pt; }"
                + " td.n { width: 8%; } td.l { width: 57%; } td.v { width: 35%; text-align: right; }"
                + " p.h { text-align: center; font-weight: bold; }"
                + " table.b td { border: 0.5pt solid black; text-align: center; } table.b td.a { text-align: right; }</style></head><body><p class=\"h\">FORM NO. 130</p>");
        String top = "";
        boolean partBPrinted = omitted.contains("partB");
        for (PrintedLine line : layout.lines()) {
            String part = topPart(layout, line.path());
            if (line.repeating() || omitted.contains(line.path()) || omitted.contains(part)) {
                continue;
            }
            if (!part.equals(top)) {
                html.append(top.isEmpty() ? "" : "</table>");
                if (!partBPrinted && !part.startsWith("partA")) {
                    html.append(PartBFixture.html(layout, values, omitted));
                    partBPrinted = true;
                }
                html.append("<p class=\"h\">").append(heading(layout, part)).append("</p><table>");
                top = part;
            }
            group(layout, line, html);
            html.append(row(line, values.getOrDefault(line.path(), ""), labels.getOrDefault(line.path(), line.label())));
        }
        html.append("</table>").append(partBPrinted ? "" : PartBFixture.html(layout, values, omitted)).append("</body></html>");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ITextRenderer renderer = new ITextRenderer();
        renderer.setDocumentFromString(html.toString());
        renderer.layout();
        renderer.createPDF(out);
        return out.toByteArray();
    }

    /**
     * A PDF that opens only with its user password ("user"), encrypted as {@code name} says: written once by pypdf 6.14.0
     * (`PdfWriter.encrypt(user_password, owner_password, algorithm)`, RC4-128 and AES-128), so the encryption tested is
     * not the reader's own.
     */
    static byte[] withPassword(String name) throws IOException {
        try (InputStream in = Form130Fixture.class.getResourceAsStream("/taxplanning/documents/" + name)) {
            return in.readAllBytes();
        }
    }

    /** A page that is an image only, as a scan is. */
    static byte[] scanned() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, out);
        document.open();
        document.add(Image.getInstance(new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB), null));
        document.close();
        return out.toByteArray();
    }

    private static void group(CertificateLayout layout, PrintedLine line, StringBuilder html) {
        layout.parts().stream().filter(part -> line.path().startsWith(part.path() + ".")).filter(part -> part.path().contains("."))
                .filter(part -> !part.path().equals(topPart(layout, line.path())))
                .filter(part -> html.indexOf(">" + part.heading() + "<") < 0)
                .forEach(part -> html.append("<tr><td colspan=\"3\"><b>").append(part.heading()).append("</b></td></tr>"));
    }

    private static String row(PrintedLine line, String value, String label) {
        String row = line.row().orElse("");
        boolean sub = row.contains("(");
        String number = sub ? "" : (row.isEmpty() ? "" : row.length() == 1 && Character.isLetter(row.charAt(0)) ? row : row + ".");
        String marker = sub ? row.substring(row.indexOf('(')) + " " : "";
        return "<tr><td class=\"n\">" + number + "</td><td class=\"l\">" + marker + label + "</td><td class=\"v\">" + value
                + "</td></tr>";
    }

    private static String topPart(CertificateLayout layout, String path) {
        return layout.parts().stream().map(CertificateLayout.PartHeading::path)
                .filter(part -> path.startsWith(part + ".") && layout.parts().stream()
                        .noneMatch(other -> !other.path().equals(part) && part.startsWith(other.path() + ".")))
                .findFirst().orElseThrow();
    }

    private static String heading(CertificateLayout layout, String part) {
        return layout.parts().stream().filter(p -> p.path().equals(part)).findFirst().orElseThrow().heading();
    }
}
