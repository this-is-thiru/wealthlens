package com.thiru.wealthlens.taxplanning.documents.parser;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** A child that reports the names of its environment variables (W-131f's tests of {@link IsolatedPdfRead}). */
public final class EnvironmentPdfReadChild {

    private EnvironmentPdfReadChild() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        reply("{\"outcome\":\"READ\",\"pages\":0,\"words\":[],\"reason\":\"" + String.join(",", new java.util.TreeSet<>(System.getenv().keySet())) + "\"}");
    }

    static void reply(String json) throws IOException {
        try (OutputStream out = new FileOutputStream(FileDescriptor.out)) {
            out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
