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
}
