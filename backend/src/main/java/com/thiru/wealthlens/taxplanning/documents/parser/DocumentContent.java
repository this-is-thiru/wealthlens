package com.thiru.wealthlens.taxplanning.documents.parser;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** A document's bytes, as uploaded. Copied in and out, so a parser cannot change what another reads. */
public record DocumentContent(byte[] bytes) {

    public DocumentContent {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("A document's content cannot be empty");
        }
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    /** The SHA-256 of the bytes as 64 lowercase hex characters, the form {@code IngestedDocument} holds. */
    public String sha256() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("This Java runtime has no SHA-256", e);
        }
    }
}
