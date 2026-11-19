package com.thiru.wealthlens.taxplanning.documents.parser;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** A child that tries to read the application's environment through /proc (W-131f's tests of {@link IsolatedPdfRead}). */
public final class ParentEnvironPdfReadChild {

    private ParentEnvironPdfReadChild() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        long parent = ProcessHandle.current().parent().orElseThrow().pid();
        String seen;
        try {
            java.nio.file.Files.readAllBytes(java.nio.file.Path.of("/proc/" + parent + "/environ"));
            seen = "READ";
        } catch (java.nio.file.AccessDeniedException e) {
            seen = "DENIED";
        }
        reply("{\"outcome\":\"READ\",\"pages\":0,\"words\":[],\"reason\":\"" + seen + "\"}");
    }

    static void reply(String json) throws IOException {
        try (OutputStream out = new FileOutputStream(FileDescriptor.out)) {
            out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
