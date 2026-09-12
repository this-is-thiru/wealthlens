package com.thiru.wealthlens.portfolio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import java.time.LocalDate;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class TradeFingerprintTest {

    private static final String EMAIL = "test@example.com";

    private static AssetRequest trade() {
        AssetRequest request = new AssetRequest();
        request.setStockCode("INFY");
        request.setBrokerName(BrokerName.ZERODHA);
        request.setAccountHolder("main");
        request.setAssetType(AssetType.EQUITY);
        request.setTransactionType(TransactionType.BUY);
        request.setQuantity(10.0);
        request.setPrice(1500.0);
        request.setTransactionDate(LocalDate.of(2025, 6, 10));
        return request;
    }

    private static String fingerprintWith(Consumer<AssetRequest> change) {
        AssetRequest request = trade();
        change.accept(request);
        return TradeFingerprint.of(EMAIL, request);
    }

    @Test
    @DisplayName("the same trade submitted twice fingerprints identically -- this is what makes a retry detectable")
    void of_whenTheTradeIsIdentical_isStable() {
        // Given / When / Then
        assertEquals(TradeFingerprint.of(EMAIL, trade()), TradeFingerprint.of(EMAIL, trade()));
    }

    @Test
    @DisplayName("every field that makes a trade distinct changes the fingerprint")
    void of_whenAnyIdentifyingFieldDiffers_differs() {
        // Given
        String base = TradeFingerprint.of(EMAIL, trade());

        // When / Then
        assertNotEquals(base, fingerprintWith(r -> r.setStockCode("TCS")), "stock code");
        assertNotEquals(base, fingerprintWith(r -> r.setBrokerName(BrokerName.UPSTOX)), "broker");
        assertNotEquals(base, fingerprintWith(r -> r.setAccountHolder("spouse")), "account holder");
        assertNotEquals(base, fingerprintWith(r -> r.setAssetType(AssetType.MUTUAL_FUND)), "asset type");
        assertNotEquals(base, fingerprintWith(r -> r.setTransactionType(TransactionType.SELL)), "transaction type");
        assertNotEquals(base, fingerprintWith(r -> r.setQuantity(11.0)), "quantity");
        assertNotEquals(base, fingerprintWith(r -> r.setPrice(1500.5)), "price");
        assertNotEquals(base, fingerprintWith(r -> r.setTransactionDate(LocalDate.of(2025, 6, 11))), "date");
    }

    @Test
    @DisplayName("a different user's identical trade is a different trade")
    void of_whenTheUserDiffers_differs() {
        // Given / When / Then
        assertNotEquals(TradeFingerprint.of(EMAIL, trade()),
                TradeFingerprint.of("other@example.com", trade()));
    }

    @Test
    @DisplayName("field values cannot run together into the same digest")
    void of_whenFieldsShiftAcrossTheBoundary_differs() {
        // Given -- "AB" + "C" must not fingerprint the same as "A" + "BC"
        String left = fingerprintWith(r -> {
            r.setStockCode("AB");
            r.setAccountHolder("C");
        });
        String right = fingerprintWith(r -> {
            r.setStockCode("A");
            r.setAccountHolder("BC");
        });

        // When / Then
        assertNotEquals(left, right);
    }

    @Test
    @DisplayName("a null optional field does not blow up, and still fingerprints")
    void of_whenAnOptionalFieldIsNull_stillProducesADigest() {
        // Given / When
        String fingerprint = fingerprintWith(r -> r.setAccountHolder(null));

        // Then
        assertEquals(64, fingerprint.length(), "SHA-256 hex");
        assertTrue(fingerprint.matches("[0-9a-f]{64}"));
    }
}
