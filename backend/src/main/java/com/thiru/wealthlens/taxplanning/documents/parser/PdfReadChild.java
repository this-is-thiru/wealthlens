package com.thiru.wealthlens.taxplanning.documents.parser;

import com.itextpdf.text.exceptions.BadPasswordException;
import com.itextpdf.text.pdf.PdfReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

/**
 * The child process that reads an uploaded PDF (W-131f, {@code DR 14} O6 "Parsing isolation"): the only code that hands untrusted
 * bytes to iText. It reads the PDF from stdin, writes one {@link PdfText} as JSON to stdout and exits; it has no Spring, no rule
 * pack and no database, so a hostile PDF that takes it over reaches nothing but this process. {@link IsolatedPdfRead} starts it with
 * a time, CPU and memory limit and no network.
 */
public final class PdfReadChild {

    private PdfReadChild() {
    }

    /** @param args one {@link PdfText.Depth} */
    public static void main(String[] args) throws IOException {
        PdfText.Depth depth = PdfText.Depth.valueOf(args[0]);
        PdfText reply = read(System.in.readAllBytes(), depth);
        // The reply is the process's output, not a log line: FileDescriptor.out, so no logger can interleave with it.
        try (OutputStream out = new FileOutputStream(FileDescriptor.out)) {
            JsonMapper.builder().build().writeValue(out, reply);
        }
    }

    static PdfText read(byte[] pdf, PdfText.Depth depth) {
        PdfReader reader;
        try {
            reader = new PdfReader(pdf);
        } catch (BadPasswordException e) {
            return new PdfText(PdfText.Outcome.PASSWORD, 0, List.of(), "");
        } catch (IOException e) {
            return new PdfText(PdfText.Outcome.UNREADABLE, 0, List.of(), String.valueOf(e.getMessage()));
        }
        try {
            int pages = reader.getNumberOfPages();
            List<PrintedText.Word> words = depth == PdfText.Depth.WORDS ? PrintedText.of(reader).words() : List.of();
            return new PdfText(PdfText.Outcome.READ, pages, words, "");
        } catch (IOException e) {
            return new PdfText(PdfText.Outcome.UNREADABLE, 0, List.of(), String.valueOf(e.getMessage()));
        } finally {
            reader.close();
        }
    }
}
