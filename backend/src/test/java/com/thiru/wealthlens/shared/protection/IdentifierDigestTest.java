package com.thiru.wealthlens.shared.protection;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdentifierDigestTest {

    @Test
    void of_whenTheKeyIsBytesNilToThirtyOne_isPythonsHmac() {
        // Given — key bytes 0x00 … 0x1F
        IdentifierDigest digest = new IdentifierDigest(new PersonalDataKeys(null, "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="));

        // When / Then — hmac.new(bytes(range(32)), b"ABCPE1234F", hashlib.sha256).hexdigest(), run outside the code
        assertThat(digest.of("ABCPE1234F")).isEqualTo("333bec6096ed665269070f58e3852ef8cf148bb3b73b2293d3f9109d8f4c608b");
    }
}
