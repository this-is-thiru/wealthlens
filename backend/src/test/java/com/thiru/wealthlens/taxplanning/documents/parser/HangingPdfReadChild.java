package com.thiru.wealthlens.taxplanning.documents.parser;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** A child that never answers, after writing its process id where the test asked (W-131f's tests of {@link IsolatedPdfRead}). */
public final class HangingPdfReadChild {

    private HangingPdfReadChild() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("w131f.pidfile")), String.valueOf(ProcessHandle.current().pid()));
        Thread.sleep(Long.MAX_VALUE);
    }

    static void reply(String json) throws IOException {
        try (OutputStream out = new FileOutputStream(FileDescriptor.out)) {
            out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
