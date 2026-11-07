package com.thiru.wealthlens.shared.protection;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * A keyed HMAC-SHA256 of an identifier, stored beside its encrypted value so that a unique index can still hold ({@code DR 14} O1.1,
 * D-447 C1a): equal identifiers digest equally, and without the key the digest cannot be reversed by trying every PAN.
 */
@Component
public class IdentifierDigest {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public IdentifierDigest(PersonalDataKeys keys) {
        this.key = new SecretKeySpec(KeyBytes.decode(keys.digestKey(), "app.personal-data.digest-key"), ALGORITHM);
    }

    public String of(String identifier) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(identifier.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("An identifier could not be digested", e);
        }
    }
}
