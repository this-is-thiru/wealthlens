package com.thiru.wealthlens.taxplanning.documents.parser;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** A child that exits with status 3 (W-131f's tests of {@link IsolatedPdfRead}). */
public final class FailingPdfReadChild {

    private FailingPdfReadChild() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Runtime.getRuntime().halt(3);
    }

    static void reply(String json) throws IOException {
        try (OutputStream out = new FileOutputStream(FileDescriptor.out)) {
            out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
