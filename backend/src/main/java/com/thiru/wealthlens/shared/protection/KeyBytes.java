package com.thiru.wealthlens.shared.protection;

import java.util.Base64;

/** Decodes a configured key, refusing anything but 32 bytes so that a missing or short key stops the application at start. */
final class KeyBytes {

    private static final int LENGTH = 32;

    private KeyBytes() {
    }

    static byte[] decode(String base64, String property) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException(property + " is not set; stored personal data cannot be protected without it");
        }
        byte[] key = Base64.getDecoder().decode(base64);
        if (key.length != LENGTH) {
            throw new IllegalStateException(property + " must be " + LENGTH + " bytes in base64, not " + key.length);
        }
        return key;
    }
}
