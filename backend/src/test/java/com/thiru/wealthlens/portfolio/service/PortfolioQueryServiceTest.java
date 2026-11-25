package com.thiru.wealthlens.portfolio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.portfolio.dto.AssetResponse;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.repository.PortfolioRepository;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioQueryServiceTest {

    private final UserMail userMail = UserMail.from("query@example.com");

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private ProfitAndLossService profitAndLossService;

    @Mock
    private ChargeViewAssembler chargeViewAssembler;

    /** 00:30 on 1 February 2026 in Kolkata, still 31 January in UTC: "today" is the business zone's date (D-958). */
    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-01-31T19:00:00Z"), ZoneId.of("Asia/Kolkata"));

    @InjectMocks
    private PortfolioQueryService portfolioQueryService;

    @Test
    void getAllStocks_whenAHoldingWasBoughtOnTwoDates_reportsTheDaysHeldForEach() {
        // Given -- 5 bought 400 days ago and 3 bought 30 days ago, one stock at one broker
        LocalDate today = LocalDate.of(2026, 2, 1);
        when(portfolioRepository.findByEmail(userMail.getEmail()))
                .thenReturn(List.of(lot(today.minusDays(400), 5.0), lot(today.minusDays(30), 3.0)));

        // When
        List<AssetResponse> holdings = portfolioQueryService.getAllStocks(userMail);

        // Then
        assertEquals(1, holdings.size());
        assertEquals(Map.of(today.minusDays(400).toString(), 400L, today.minusDays(30).toString(), 30L),
                holdings.getFirst().getDaysHeld());
        assertEquals(List.copyOf(holdings.getFirst().getTransactionQuantities().keySet()),
                List.copyOf(holdings.getFirst().getDaysHeld().keySet()));
    }

    private static AssetEntity lot(LocalDate boughtOn, double quantity) {
        AssetEntity lot = new AssetEntity();
        lot.setStockCode("STOCK1");
        lot.setBrokerName(BrokerName.ZERODHA);
        lot.setPrice(100.0);
        lot.setQuantity(quantity);
        lot.setTransactionDate(boughtOn);
        return lot;
    }
}
