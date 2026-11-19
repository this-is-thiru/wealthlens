package com.thiru.wealthlens.taxplanning.documents.parser;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** A child that writes far more than the reply cap (W-131f's tests of {@link IsolatedPdfRead}). */
public final class FloodingPdfReadChild {

    private FloodingPdfReadChild() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        try (OutputStream out = new FileOutputStream(FileDescriptor.out)) {
            byte[] block = new byte[64 * 1024];
            while (true) {
                out.write(block);
            }
        }
    }

    static void reply(String json) throws IOException {
        try (OutputStream out = new FileOutputStream(FileDescriptor.out)) {
            out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
