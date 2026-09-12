package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A stable hash of the fields that make a trade the trade it is.
 *
 * <p>The fallback when a client supplies no idempotency key. Two submissions of the same trade
 * produce the same fingerprint, which is what makes a retry detectable — and also why the
 * fingerprint alone can never be a unique constraint: two genuinely identical trades on one day are
 * legitimate and must both be accepted. It is the fingerprint <em>plus a short time window</em>
 * that distinguishes a retry from a real second trade.
 */
public final class TradeFingerprint {

    private TradeFingerprint() {
    }

    /** Separates fields so "AB" + "C" cannot digest the same as "A" + "BC". */
    private static final char SEPARATOR = '\u001f';

    public static String of(String email, AssetRequest request) {
        String material = String.join(String.valueOf(SEPARATOR),
                String.valueOf(email),
                String.valueOf(request.getStockCode()),
                String.valueOf(request.getBrokerName()),
                String.valueOf(request.getAccountHolder()),
                String.valueOf(request.getAssetType()),
                String.valueOf(request.getTransactionType()),
                String.valueOf(request.getQuantity()),
                String.valueOf(request.getPrice()),
                String.valueOf(request.getTransactionDate()));
        return sha256(material);
    }

    private static String sha256(String material) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required of every JVM, so this cannot happen -- but swallowing it would
            // silently disable duplicate detection, which is worse than failing the trade.
            throw new IllegalStateException("SHA-256 is unavailable, so trades cannot be fingerprinted", e);
        }
    }
}
