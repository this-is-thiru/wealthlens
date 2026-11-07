package com.thiru.wealthlens.shared.protection;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Encrypts one stored field with AES-256-GCM ({@code DR 14} O1.1, D-447 C1a). A fresh nonce per value, so two encryptions of one PAN
 * differ; the stored form names its key, {@code k1:}, so a later key is new data rather than a rewrite of this class. A value that
 * was altered, or written under another key, refuses rather than reading as something else.
 */
@Component
public class FieldCipher {

    static final String KEY_ID = "k1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public FieldCipher(PersonalDataKeys keys) {
        this.key = new SecretKeySpec(KeyBytes.decode(keys.dataKey(), "app.personal-data.data-key"), "AES");
    }

    public String encrypt(String plain) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
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
