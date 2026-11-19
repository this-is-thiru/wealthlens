package com.thiru.wealthlens.taxplanning.documents.parser;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** A child that tries to resolve a name and to open a socket, and reports what happened (W-131f's tests of {@link IsolatedPdfRead}). */
public final class NetworkPdfReadChild {

    private NetworkPdfReadChild() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        String lookup;
        try {
            java.net.InetAddress.getByName("example.com");
            lookup = "resolved";
        } catch (java.net.UnknownHostException e) {
            lookup = "UnknownHostException";
        }
        String socket;
        try (java.net.Socket opened = new java.net.Socket()) {
            opened.connect(new java.net.InetSocketAddress(java.net.InetAddress.getByAddress(new byte[] {(byte) 192, 0, 2, 1}), 80), 3_000);
            socket = "connected";
        } catch (java.net.SocketTimeoutException e) {
            socket = "timed out";
        } catch (IOException e) {
            socket = "refused: " + e.getClass().getSimpleName();
        }
        reply("{\"outcome\":\"READ\",\"pages\":0,\"words\":[],\"reason\":\"lookup=" + lookup + " socket=" + socket + "\"}");
    }

    static void reply(String json) throws IOException {
        try (OutputStream out = new FileOutputStream(FileDescriptor.out)) {
            out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
