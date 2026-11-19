package com.thiru.wealthlens.testsupport;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Small PDFs built in the test, so a limit is tested at its edge without a file checked in (W-131b). */
public final class TestPdfs {

    private TestPdfs() {
    }

    /** A PDF of {@code pages} pages, each saying its number. */
    public static byte[] pages(int pages) {
        return build(pages, false);
    }

    /** A one-page PDF that opens only with the password "open". */
    public static byte[] locked() {
        return build(1, true);
    }

    private static byte[] build(int pages, boolean locked) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document();
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            if (locked) {
                writer.setEncryption("open".getBytes(StandardCharsets.US_ASCII), "owner".getBytes(StandardCharsets.US_ASCII),
                        PdfWriter.ALLOW_PRINTING, PdfWriter.ENCRYPTION_AES_128);
            }
            document.open();
            for (int page = 1; page <= pages; page++) {
                document.newPage();
                document.add(new Paragraph("Page " + page));
            }
        } catch (DocumentException e) {
            throw new IllegalStateException("A test PDF could not be built", e);
        }
        document.close();
        return out.toByteArray();
    }
}
