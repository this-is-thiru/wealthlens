package com.thiru.wealthlens.taxplanning.documents.parser;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** A child that only burns CPU (W-131f's tests of {@link IsolatedPdfRead}). */
public final class SpinningPdfReadChild {

    private SpinningPdfReadChild() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        long spins = 0;
        while (spins >= 0) {
            spins++;
        }
    }

    static void reply(String json) throws IOException {
        try (OutputStream out = new FileOutputStream(FileDescriptor.out)) {
            out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
