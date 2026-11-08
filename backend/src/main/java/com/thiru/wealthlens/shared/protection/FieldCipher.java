package com.thiru.wealthlens.shared.protection;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Encrypts one stored field with AES-256-GCM ({@code DR 14} O1.1, D-447 C1a). A fresh nonce per value, so two encryptions of one PAN
 * differ; the stored form names its key, {@code k1:}, so a later key is new data rather than a rewrite of this class. A value that
 * was altered, or written under another key, refuses rather than reading as something else. An identifier that must stay
 * queryable takes {@link #encryptSearchable} instead (W-253).
 */
@Component
public class FieldCipher {

    static final String KEY_ID = "k1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String NONCE_ALGORITHM = "HmacSHA256";
    private static final byte[] NONCE_LABEL = "identifier-nonce:".getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec key;
    private final SecretKeySpec nonceKey;
    private final SecureRandom random = new SecureRandom();

    public FieldCipher(PersonalDataKeys keys) {
        this.key = new SecretKeySpec(KeyBytes.decode(keys.dataKey(), "app.personal-data.data-key"), "AES");
        this.nonceKey = new SecretKeySpec(KeyBytes.decode(keys.digestKey(), "app.personal-data.digest-key"), NONCE_ALGORITHM);
    }

    public String encrypt(String plain) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        return seal(nonce, plain);
    }

    /**
     * The same value always stores the same form, so equality queries and unique indexes still work on the stored field (W-253,
     * D-485 C1a). The nonce is the value's keyed digest under the digest key, labelled apart from {@link IdentifierDigest}; it reveals
     * only which rows hold equal values, as a digest stored beside the value would. Read back by {@link #decrypt}.
     */
    public String encryptSearchable(String plain) {
        return seal(nonceOf(plain), plain);
    }

    private String seal(byte[] nonce, String plain) {
        byte[] sealed = run(Cipher.ENCRYPT_MODE, nonce, plain.getBytes(StandardCharsets.UTF_8));
        return KEY_ID + Base64.getEncoder().encodeToString(ByteBuffer.allocate(NONCE_BYTES + sealed.length).put(nonce).put(sealed).array());
    }

    /**
     * @throws IllegalStateException if {@code stored} is not this key's, or was altered
     */
    public String decrypt(String stored) {
        if (!stored.startsWith(KEY_ID)) {
            throw new IllegalStateException("A stored field is not encrypted under a known key");
        }
        ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(stored.substring(KEY_ID.length())));
        byte[] nonce = new byte[NONCE_BYTES];
        buffer.get(nonce);
        byte[] sealed = new byte[buffer.remaining()];
        buffer.get(sealed);
        return new String(run(Cipher.DECRYPT_MODE, nonce, sealed), StandardCharsets.UTF_8);
    }

    private byte[] nonceOf(String plain) {
        try {
            Mac mac = Mac.getInstance(NONCE_ALGORITHM);
            mac.init(nonceKey);
            mac.update(NONCE_LABEL);
            return Arrays.copyOf(mac.doFinal(plain.getBytes(StandardCharsets.UTF_8)), NONCE_BYTES);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("An identifier's nonce could not be derived", e);
        }
    }

    private byte[] run(int mode, byte[] nonce, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("A stored field could not be " + (mode == Cipher.ENCRYPT_MODE ? "encrypted" : "decrypted"), e);
        }
    }
}
