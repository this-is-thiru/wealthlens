package com.thiru.wealthlens.portfolio.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.config.ChargeEngineProperties;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.repository.PortfolioRepository;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.testsupport.MoneyAssert;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * What a V2 buy's lot carries as its charges (W-223, D-437): the engine's figure when it has one, the entered 240 when it could
 * not price the trade. Every row is the plan's table.
 */
@ExtendWith(MockitoExtension.class)
class PortfolioServiceCostBasisTest {

    private static final double ENTERED = 240.0;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private ChargeRecordingGateway chargeRecordingGateway;

    @Mock
    private ProfitAndLossService profitAndLossService;

    @Mock
    private ChargeEngineProperties chargeEngineProperties;

    @InjectMocks
    private PortfolioService portfolioService;

    private static AssetRequest buy() {
        AssetRequest request = new AssetRequest();
        request.setStockCode("SGB-1");
        request.setStockName("A gold bond");
        request.setAssetType(AssetType.GOLD_BOND);
        request.setPrice(5000.0);
        request.setQuantity(10.0);
        request.setTransactionType(TransactionType.BUY);
        request.setTransactionDate(LocalDate.of(2026, 1, 15));
        request.setBrokerCharges(ENTERED);
        return request;
    }

    private static ChargeComputation computed(ChargeResolution resolution, double total) {
        return new ChargeComputation(null, null, null, null, null, resolution, List.of(), total);
    }

    private double lotChargesAfterBuying(boolean authoritative, Optional<ChargeComputation> computation) {
        lenient().when(chargeEngineProperties.authoritative()).thenReturn(authoritative);
        when(chargeRecordingGateway.record(any(), any())).thenReturn(computation);
        portfolioService.buyStockV2(UserMail.from("user@example.com"), "t-1", buy());
        ArgumentCaptor<AssetEntity> lot = ArgumentCaptor.forClass(AssetEntity.class);
        verify(portfolioRepository).save(lot.capture());
        return lot.getValue().getBrokerCharges();
    }

    static Stream<Arguments> computations() {
        return Stream.of(
                Arguments.of("no card", computed(ChargeResolution.NO_SCHEDULE, 0.0), ENTERED),
                Arguments.of("a card with no rule for the event", computed(ChargeResolution.NO_MATCHING_RULES, 0.0), ENTERED),
                Arguments.of("no instrument profile", computed(ChargeResolution.NO_INSTRUMENT_PROFILE, 0.0), ENTERED),
                Arguments.of("no statutory card", computed(ChargeResolution.NO_STATUTORY_SCHEDULE, 0.0), ENTERED),
                Arguments.of("a resolved figure", computed(ChargeResolution.RESOLVED, 45.5), 45.5),
                Arguments.of("a provisional figure", computed(ChargeResolution.PROVISIONAL, 12.0), 12.0),
                Arguments.of("a corporate-action exemption", computed(ChargeResolution.CORPORATE_ACTION_EXEMPT, 0.0), 0.0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("computations")
    void buyStockV2_whenAuthoritative_keepsTheEnteredChargeUnlessThereIsAFigure(String name, ChargeComputation computation,
            double expected) {
        // When / Then
        MoneyAssert.assertMoney(expected, lotChargesAfterBuying(true, Optional.of(computation)));
    }

    @Test
    void buyStockV2_whenTheEngineDeclines_keepsTheEnteredCharge() {
        // When / Then
        MoneyAssert.assertMoney(ENTERED, lotChargesAfterBuying(true, Optional.empty()));
    }

    @Test
    void buyStockV2_whenNotAuthoritative_keepsTheEnteredChargeOverAFigure() {
        // When / Then
        MoneyAssert.assertMoney(ENTERED,
                lotChargesAfterBuying(false, Optional.of(computed(ChargeResolution.PROVISIONAL, 45.5))));
    }
}
