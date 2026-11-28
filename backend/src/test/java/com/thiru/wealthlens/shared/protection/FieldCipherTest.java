package com.thiru.wealthlens.shared.protection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class FieldCipherTest {

    private static final String KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final String OTHER_KEY = "HyAhIiMkJSYnKCkqKywtLi8wMTIzNDU2Nzg5Ojs8PT4=";

    private static FieldCipher cipher(String key) {
        return new FieldCipher(new PersonalDataKeys(key, KEY));
    }

    @Test
    void decrypt_whenEncryptedUnderTheSameKey_isTheValue() {
        // Given
        String stored = cipher(KEY).encrypt("ABCPE1234F");

        // When / Then
        assertThat(stored).startsWith("k1:").doesNotContain("ABCPE1234F");
        assertThat(cipher(KEY).decrypt(stored)).isEqualTo("ABCPE1234F");
    }

    @Test
    void encrypt_whenOneValueIsEncryptedTwice_storesTwoDifferentForms() {
        // When / Then — a fresh nonce each time, so equal PANs are not visible as equal
        assertThat(cipher(KEY).encrypt("ABCPE1234F")).isNotEqualTo(cipher(KEY).encrypt("ABCPE1234F"));
    }

    @Test
    void decrypt_whenOneByteWasAltered_refuses() {
        // Given
        byte[] sealed = Base64.getDecoder().decode(cipher(KEY).encrypt("ABCPE1234F").substring(3));
        sealed[sealed.length - 1] ^= 1;

        // When / Then
        assertThatThrownBy(() -> cipher(KEY).decrypt("k1:" + Base64.getEncoder().encodeToString(sealed)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("could not be decrypted");
    }

    @Test
    void decrypt_whenWrittenUnderAnotherKey_refuses() {
        // Given
        String stored = cipher(OTHER_KEY).encrypt("ABCPE1234F");

        // When / Then
        assertThatThrownBy(() -> cipher(KEY).decrypt(stored)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decrypt_whenTheValueWasStoredInTheClear_refuses() {
        // When / Then
        assertThatThrownBy(() -> cipher(KEY).decrypt("ABCPE1234F"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not encrypted under a known key");
    }

    @Test
    void new_whenTheKeyIsMissingOrShort_refusesToStart() {
        // When / Then
        assertThatThrownBy(() -> cipher(null)).isInstanceOf(IllegalStateException.class).hasMessageContaining("app.personal-data.data-key");
        assertThatThrownBy(() -> cipher("AAEC")).isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
    }

    /**
     * Derived outside the code with Python {@code cryptography} 49.0.0: nonce = the first 12 bytes of HMAC-SHA256(digest key,
     * "identifier-nonce:a@b.in"), then AES-256-GCM under the data key, stored as {@code k1:} + base64(nonce, ciphertext, tag).
     */
    @Test
    void encryptSearchable_whenGivenAnIdentifier_storesTheFormDerivedIndependently() {
        // Given
        FieldCipher cipher = new FieldCipher(new PersonalDataKeys(KEY, OTHER_KEY));

        // When / Then
        assertThat(cipher.encryptSearchable("a@b.in")).isEqualTo("k1:S2S9g4PmcIjT3j52RiPQbAfhd5GQLs4wzEgLXeegdyJPvg==");
        assertThat(cipher.encryptSearchable("A@b.in")).isEqualTo("k1:5Dl7Zb6GJ6OMKbWR6Bm62BFn9IuNs3eTkxUEBpIBnnTfFQ==");
    }

    /** Derived as above, for "unknown": the actor auditing records with no signed-in user is stored like any other (W-256 C2a). */
    @Test
    void encryptSearchable_whenGivenAnActorThatIsNotAnEmail_storesTheFormDerivedIndependently() {
        // Given
        FieldCipher cipher = new FieldCipher(new PersonalDataKeys(KEY, OTHER_KEY));

        // When / Then
        assertThat(cipher.encryptSearchable("unknown")).isEqualTo("k1:vA4K6mlg0YnCDfGYuiUMQ1LqUPLHZMdzC7aLTgbbD7hryKM=");
    }

    @Test
    void encryptSearchable_whenOneValueIsEncryptedTwice_storesOneFormThatDecryptsBack() {
        // Given
        FieldCipher cipher = new FieldCipher(new PersonalDataKeys(KEY, OTHER_KEY));

        // When
        String stored = cipher.encryptSearchable("a@b.in");

        // Then — equal values store equally, so a query and a unique index match them
        assertThat(cipher.encryptSearchable("a@b.in")).isEqualTo(stored);
        assertThat(cipher.decrypt(stored)).isEqualTo("a@b.in");
    }

    @Test
    void decryptBytes_whenEncryptedUnderTheSameKey_isTheBytes() {
        // Given — the start of a PDF
        byte[] pdf = "%PDF-1.4 certificate".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        // When
        byte[] stored = cipher(KEY).encryptBytes(pdf);

        // Then
        assertThat(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1)).startsWith("k1:").doesNotContain("%PDF-");
        assertThat(cipher(KEY).decryptBytes(stored)).isEqualTo(pdf);
    }

    @Test
    void encryptBytes_whenOneContentIsEncryptedTwice_storesTwoDifferentForms() {
        // When / Then
        assertThat(cipher(KEY).encryptBytes(new byte[] {1, 2, 3})).isNotEqualTo(cipher(KEY).encryptBytes(new byte[] {1, 2, 3}));
    }

    @Test
    void decryptBytes_whenOneByteWasAltered_refuses() {
        // Given
        byte[] stored = cipher(KEY).encryptBytes(new byte[] {1, 2, 3});
        stored[stored.length - 1] ^= 1;

        // When / Then
        assertThatThrownBy(() -> cipher(KEY).decryptBytes(stored)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("could not be decrypted");
    }

    @Test
    void decryptBytes_whenTheBytesWereStoredInTheClear_refuses() {
        // When / Then
        assertThatThrownBy(() -> cipher(KEY).decryptBytes("%PDF-1.4".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not encrypted under a known key");
    }

    @Test
    void reveal_whenStoredBeforeW428a_decryptsIt() {
        // When / Then
        assertThat(cipher(KEY).reveal(cipher(KEY).encrypt("ABCPE1234F"))).isEqualTo("ABCPE1234F");
    }

    @Test
    void reveal_whenStoredPlain_returnsItAsItIs() {
        // When / Then
        assertThat(cipher(KEY).reveal("ABCPE1234F")).isEqualTo("ABCPE1234F");
    }

    @Test
    void revealBytes_whenSealedBeforeW428a_opensThem() {
        // Given
        byte[] file = "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        // When / Then
        assertThat(cipher(KEY).revealBytes(cipher(KEY).encryptBytes(file))).isEqualTo(file);
    }

    @Test
    void revealBytes_whenStoredPlainOrShorterThanTheKeyId_returnsThemAsTheyAre() {
        // Given
        byte[] file = "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] shortFile = {'k', '1'};

        // When / Then
        assertThat(cipher(KEY).revealBytes(file)).isEqualTo(file);
        assertThat(cipher(KEY).revealBytes(shortFile)).isEqualTo(shortFile);
    }
}
