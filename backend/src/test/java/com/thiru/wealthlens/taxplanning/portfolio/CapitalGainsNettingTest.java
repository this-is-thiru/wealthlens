package com.thiru.wealthlens.taxplanning.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.enums.AcquisitionMode;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import com.thiru.wealthlens.portfolio.service.CapitalGainsSummaryService;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.pipeline.CapitalGainsHead;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.test.CapitalGainsHarness;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The gain the engine nets from {@code portfolio}'s amounts equals the gain {@code portfolio} used to net itself
 * (W-162a, D-375): W-086's rows A – C, through the summary, the adapter and {@code CapitalGainsNode}, give the
 * hand-derived 263.00 and 390.00 of {@code CapitalGainsSummaryServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class CapitalGainsNettingTest {

    private static final String EMAIL = "user@example.com";

    @Mock
    private TradeOutcomeRepository tradeOutcomeRepository;

    /** A sale of equity on NSE on 2-6-2025, bought on {@code buyDate}, with each side's breakdown as given. */
    private static TradeOutcomeEntity row(String id, double sellPrice, double buyPrice, double quantity,
            LocalDate buyDate, Map<String, Double> buyBreakup, Map<String, Double> sellBreakup) {
        TradeOutcomeEntity row = new TradeOutcomeEntity();
        row.setId(id);
        row.setStockCode("STOCK-" + id);
        row.setAssetType(AssetType.EQUITY);
        row.setSellDate(LocalDate.of(2025, 6, 2));
        row.setBuyDate(buyDate);
        row.setSellPrice(sellPrice);
        row.setSellQuantity(quantity);
        row.setCorporateActionAdjustedBuyPrice(buyPrice);
        row.setBuyQuantity(quantity);
        row.setSellExchangeName("NSE");
        row.setAcquisitionMode(AcquisitionMode.PURCHASE);
        row.setBuyChargeBreakup(buyBreakup);
        row.setBuyBrokerCharges(buyBreakup.values().stream().mapToDouble(Double::doubleValue).sum());
        row.setSellChargeBreakup(sellBreakup);
        row.setSellBrokerCharges(sellBreakup.values().stream().mapToDouble(Double::doubleValue).sum());
        return row;
    }

    @Test
    void inputFor_whenW086RowsReachTheEngine_netsTheGainsW086DerivedByHand() {
        // Given — W-086's A, B (bought 2-1-2025) and C (bought 2-1-2024), their breakdowns as there; B's misc 10.
        // C is a bonus lot, so the long-term part rows take it (S.O. 5054(E)); its amounts are W-086's
        LocalDate abBought = LocalDate.of(2025, 1, 2);
        TradeOutcomeEntity a = row("A", 120, 100, 10, abBought, Map.of("BROKERAGE", 5.0),
                Map.of("BROKERAGE", 7.0, "STT", 1.20));
        TradeOutcomeEntity b = row("B", 150, 130, 4, abBought, Map.of("BROKERAGE", 2.0),
                Map.of("BROKERAGE", 3.0, "STT", 0.60));
        b.setSellMiscCharges(10);
        TradeOutcomeEntity c = row("C", 500, 300, 2, LocalDate.of(2024, 1, 2), Map.of("BROKERAGE", 4.0),
                Map.of("BROKERAGE", 6.0, "STT", 1.00));
        c.setAcquisitionMode(AcquisitionMode.CORPORATE_ACTION);
        c.setAcquisitionCorporateAction(CorporateActionType.BONUS);
        when(tradeOutcomeRepository.findByEmailAndSellDateBetween(eq(EMAIL), any(), any())).thenReturn(List.of(a, b, c));
        CapitalGainsInput input = new CapitalGainsAdapter(new CapitalGainsSummaryService(tradeOutcomeRepository))
                .inputFor(EMAIL, TaxYearId.of(2025));

        // When
        CapitalGainsHead head = (CapitalGainsHead) CapitalGainsHarness.run(2025, input).value();

        // Then — A + B: 1,800 − 1,520 − (5 + 2) − (7 + 3) = 263, STT 1.80 and misc 10 not deducted, short-term;
        // C: 1,000 − 600 − 4 − 6 = 390, STT 1.00 not deducted, long-term
        assertThat(head.part(new CapitalGainsPart("SHORT_TERM_LISTED_EQUITY"))).isEqualTo(Money.of("263.00"));
        assertThat(head.part(new CapitalGainsPart("LONG_TERM_LISTED_EQUITY"))).isEqualTo(Money.of("390.00"));
    }
}
