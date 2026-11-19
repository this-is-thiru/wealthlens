package com.thiru.wealthlens.taxplanning.documents.parser;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** A child whose reply is not JSON, then one with a field the reply does not have (W-131f's tests of {@link IsolatedPdfRead}). */
public final class GarbagePdfReadChild {

    private GarbagePdfReadChild() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        reply(args[0].equals("PAGES") ? "{not json" : "{\"outcome\":\"READ\",\"pages\":1,\"words\":[],\"reason\":\"\",\"extra\":1}");
    }

    static void reply(String json) throws IOException {
        try (OutputStream out = new FileOutputStream(FileDescriptor.out)) {
            out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
