package com.thiru.wealthlens.portfolio.service;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertMoney;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.entity.ChargeLine;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.brokercharges.service.UserChargeService;
import com.thiru.wealthlens.portfolio.dto.charges.AssetCharges;
import com.thiru.wealthlens.portfolio.dto.charges.ChargeNote;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ChargeViewAssemblerTest {

    private static final String EMAIL = "test@example.com";

    @Mock private UserChargeService userChargeService;
    @Mock private TradeOutcomeRepository tradeOutcomeRepository;

    @InjectMocks private ChargeViewAssembler assembler;

    /**
     * The defect this class exists to prevent. One sell of 6 units consumes three lots; the sell's
     * charges are recorded once against the transaction and allocated across the lots. Reading them
     * back per asset must reproduce the whole and not a multiple of it.
     */
    @Test
    @DisplayName("assetCharges: a sell spanning three lots is split across them, never repeated")
    void assetCharges_whenOneSellSpansThreeLots_sellChargesSumToTheTransactionTotal() {
        // Given
        AssetEntity lotA = lot("asset-a", 3);
        AssetEntity lotB = lot("asset-b", 2);
        AssetEntity lotC = lot("asset-c", 1);

        when(tradeOutcomeRepository.findByEmailAndSourceBuyLotIdIn(anyString(), any()))
                .thenReturn(List.of(
                        outcome("asset-a", Map.of("BROKERAGE", 50.00)),
                        outcome("asset-b", Map.of("BROKERAGE", 33.33)),
                        outcome("asset-c", Map.of("BROKERAGE", 16.67))));
        when(userChargeService.findForTransactions(anyString(), any())).thenReturn(Map.of());

        // When
        var lookup = assembler.forLots(EMAIL, List.of(lotA, lotB, lotC));
        AssetCharges a = assembler.assetCharges(lookup, List.of(lotA));
        AssetCharges b = assembler.assetCharges(lookup, List.of(lotB));
        AssetCharges c = assembler.assetCharges(lookup, List.of(lotC));

        // Then
        assertMoney(50.00, a.getSell().getTotal());
        assertMoney(33.33, b.getSell().getTotal());
        assertMoney(16.67, c.getSell().getTotal());
        assertMoney("the parts must sum to the charge actually levied", 100.00,
                a.getSell().getTotal() + b.getSell().getTotal() + c.getSell().getTotal());
    }

    /** The whole point of batching: two queries for a portfolio, not two per row. */
    @Test
    @DisplayName("forLots: loads every lot's charges in one query per side")
    void forLots_whenManyLots_issuesOneQueryPerSide() {
        // Given
        List<AssetEntity> lots = List.of(lot("asset-a", 3), lot("asset-b", 2), lot("asset-c", 1));
        when(tradeOutcomeRepository.findByEmailAndSourceBuyLotIdIn(anyString(), any())).thenReturn(List.of());
        when(userChargeService.findForTransactions(anyString(), any())).thenReturn(Map.of());

        // When
        assembler.forLots(EMAIL, lots);

        // Then
        verify(tradeOutcomeRepository, times(1)).findByEmailAndSourceBuyLotIdIn(anyString(), any());
        verify(userChargeService, times(1)).findForTransactions(anyString(), any());
    }

    /**
     * A lot bought before the engine was switched on has no charge row, and never will — ADR-32
     * forbids re-driving it. A zero would be indistinguishable from a correct zero.
     */
    @Test
    @DisplayName("assetCharges: an unpriced lot reports null rather than zero")
    void assetCharges_whenNoChargeRowExists_returnsNull() {
        // Given
        AssetEntity lot = lot("asset-a", 3);
        when(tradeOutcomeRepository.findByEmailAndSourceBuyLotIdIn(anyString(), any())).thenReturn(List.of());
        when(userChargeService.findForTransactions(anyString(), any())).thenReturn(Map.of());

        // When
        AssetCharges charges = assembler.assetCharges(assembler.forLots(EMAIL, List.of(lot)), List.of(lot));

        // Then
        assertNull(charges, "an unpriced lot must be distinguishable from one charged nothing");
    }

    /** A single lot's buy is a 1:1 link, so its contract note survives intact. */
    @Test
    @DisplayName("assetCharges: a single lot carries its buy contract note verbatim")
    void assetCharges_whenSingleLot_carriesBuyLinesVerbatim() {
        // Given
        AssetEntity lot = lot("asset-a", 3);
        when(tradeOutcomeRepository.findByEmailAndSourceBuyLotIdIn(anyString(), any())).thenReturn(List.of());
        when(userChargeService.findForTransactions(anyString(), any()))
                .thenReturn(Map.of("buy-a", userCharge(Map.of("BROKERAGE", 20.00, "GST", 3.60))));

        // When
        AssetCharges charges = assembler.assetCharges(assembler.forLots(EMAIL, List.of(lot)), List.of(lot));

        // Then
        assertTrue(charges.getBuy().isVerbatim());
        assertEquals(1, charges.getBuy().getLines().size());
        assertEquals(ChargeResolution.RESOLVED, charges.getBuy().getResolution());
        assertMoney(23.60, charges.getBuy().getTotal());
    }

    /**
     * Most portfolio endpoints group lots by scrip and broker before responding. The merged row
     * cannot claim a verbatim contract note, because the lines belong to several trades.
     */
    @Test
    @DisplayName("assetCharges: grouped lots merge by code and drop the verbatim claim")
    void assetCharges_whenLotsAreGrouped_mergesByCodeWithoutLines() {
        // Given
        AssetEntity lotA = lot("asset-a", 3);
        AssetEntity lotB = lot("asset-b", 2);
        when(tradeOutcomeRepository.findByEmailAndSourceBuyLotIdIn(anyString(), any())).thenReturn(List.of());
        when(userChargeService.findForTransactions(anyString(), any())).thenReturn(Map.of(
                "buy-a", userCharge(Map.of("BROKERAGE", 20.00)),
                "buy-b", userCharge(Map.of("BROKERAGE", 10.00))));

        // When
        AssetCharges charges = assembler.assetCharges(assembler.forLots(EMAIL, List.of(lotA, lotB)), List.of(lotA, lotB));

        // Then
        ChargeNote buy = charges.getBuy();
        assertFalse(buy.isVerbatim(), "lines from two trades cannot be presented as one contract note");
        assertNull(buy.getLines());
        assertEquals(2, buy.getSourceCount());
        assertMoney(30.00, buy.getTotal());
    }

    private static AssetEntity lot(String id, double quantity) {
        AssetEntity asset = new AssetEntity();
        asset.setId(id);
        asset.setEmail(EMAIL);
        asset.setQuantity(quantity);
        asset.getBuyTransactionIds().add("buy-" + id.substring("asset-".length()));
        return asset;
    }

    private static TradeOutcomeEntity outcome(String lotId, Map<String, Double> sellBreakup) {
        TradeOutcomeEntity outcome = new TradeOutcomeEntity();
        outcome.setEmail(EMAIL);
        outcome.setSourceBuyLotId(lotId);
        outcome.setSellChargeBreakup(new LinkedHashMap<>(sellBreakup));
        outcome.setSellBrokerCharges(sellBreakup.values().stream().mapToDouble(Double::doubleValue).sum());
        return outcome;
    }

    private static UserChargeEntity userCharge(Map<String, Double> byCode) {
        UserChargeEntity charge = new UserChargeEntity();
        charge.setEmail(EMAIL);
        charge.setResolution(ChargeResolution.RESOLVED);
        charge.setTransactionDate(LocalDate.of(2025, 1, 1));
        charge.setAmountByCode(new LinkedHashMap<>(byCode));
        charge.setTotalCharges(byCode.values().stream().mapToDouble(Double::doubleValue).sum());
        ChargeLine line = new ChargeLine();
        line.setCode("BROKERAGE");
        line.setAmount(byCode.getOrDefault("BROKERAGE", 0.0));
        charge.setLines(List.of(line));
        return charge;
    }
}
