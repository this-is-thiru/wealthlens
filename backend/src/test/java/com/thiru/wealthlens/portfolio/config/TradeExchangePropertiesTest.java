package com.thiru.wealthlens.portfolio.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TradeExchangePropertiesTest {

    @Test
    void constructor_whenNoListIsConfigured_refusesToStart() {
        // Given / When / Then -- an absent key binds as null, an empty block as an empty map
        assertThrows(IllegalStateException.class, () -> new TradeExchangeProperties(null));
        assertThrows(IllegalStateException.class, () -> new TradeExchangeProperties(Map.of()));
    }

    @Test
    void acceptedFor_whenTheBoundMapChangesLater_keepsTheListAsBound() {
        // Given
        List<String> equity = new ArrayList<>(List.of("NSE", "BSE"));
        Map<AssetType, List<String>> source = new HashMap<>(Map.of(AssetType.EQUITY, equity));
        TradeExchangeProperties properties = new TradeExchangeProperties(source);

        // When
        equity.add("BMF");
        source.put(AssetType.FD, List.of("NSE"));

        // Then
        assertEquals(List.of("NSE", "BSE"), properties.acceptedFor(AssetType.EQUITY));
        assertTrue(properties.acceptedFor(AssetType.FD).isEmpty());
    }

    @Test
    void acceptedFor_whenATypeIsNotListed_acceptsNothing() {
        // Given
        TradeExchangeProperties properties = new TradeExchangeProperties(Map.of(AssetType.EQUITY, List.of("NSE")));

        // When / Then
        assertTrue(properties.acceptedFor(AssetType.INSURANCE).isEmpty());
    }
}
