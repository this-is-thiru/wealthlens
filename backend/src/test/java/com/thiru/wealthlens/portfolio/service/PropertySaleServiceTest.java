package com.thiru.wealthlens.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.portfolio.dto.PropertySaleRequest;
import com.thiru.wealthlens.portfolio.dto.enums.AcquisitionMode;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.testsupport.MoneyAssert;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
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

@ExtendWith(MockitoExtension.class)
class PropertySaleServiceTest {

    private static final UserMail USER = UserMail.from("user@example.com");

    @Mock
    private TradeOutcomeRepository tradeOutcomeRepository;

    @InjectMocks
    private PropertySaleService propertySaleService;

    private static PropertySaleRequest sale() {
        PropertySaleRequest request = new PropertySaleRequest();
        request.setPropertyName(" Plot 7 ");
        request.setAcquiredOn(LocalDate.of(2015, 6, 1));
        request.setCostRupees(4000000);
        request.setAcquisitionExpenses(Map.of("STAMP_DUTY", 280000.0, "REGISTRATION", 40000.0));
        request.setTransferredOn(LocalDate.of(2025, 9, 1));
        request.setConsiderationRupees(10000000);
        request.setTransferExpenses(Map.of("BROKERAGE", 100000.0));
        return request;
    }

    private TradeOutcomeEntity saved(PropertySaleRequest request) {
        propertySaleService.recordSale(USER, request);
        ArgumentCaptor<TradeOutcomeEntity> row = ArgumentCaptor.forClass(TradeOutcomeEntity.class);
        verify(tradeOutcomeRepository).save(row.capture());
        return row.getValue();
    }

    @Test
    void recordSale_whenWellFormed_savesTheFactsTheSummaryReads() {
        // When
        TradeOutcomeEntity row = saved(sale());

        // Then
        assertThat(row.getEmail()).isEqualTo("user@example.com");
        assertThat(row.getStockCode()).isEqualTo("Plot 7");
        assertThat(row.getAssetType()).isEqualTo(AssetType.LAND_OR_BUILDING);
        assertThat(row.getAccountType()).isEqualTo(AccountType.SELF);
        assertThat(row.getAcquisitionMode()).isEqualTo(AcquisitionMode.PURCHASE);
        assertThat(row.getBuyDate()).isEqualTo(LocalDate.of(2015, 6, 1));
        assertThat(row.getSellDate()).isEqualTo(LocalDate.of(2025, 9, 1));
        assertThat(row.getBuyQuantity()).isEqualTo(1.0);
        assertThat(row.getSellQuantity()).isEqualTo(1.0);
        assertThat(row.getFinancialYear()).isEqualTo("2025-2026");
        assertThat(row.getBuyChargeBreakup()).containsOnlyKeys("STAMP_DUTY", "REGISTRATION");
        assertThat(row.getSellChargeBreakup()).containsOnlyKeys("BROKERAGE");
        MoneyAssert.assertMoney(4000000, row.getCorporateActionAdjustedBuyPrice());
        MoneyAssert.assertMoney(10000000, row.getSellPrice());
        MoneyAssert.assertMoney(280000, row.getBuyChargeBreakup().get("STAMP_DUTY"));
        MoneyAssert.assertMoney(100000, row.getSellChargeBreakup().get("BROKERAGE"));
    }

    @Test
    void recordSale_whenWellFormed_totalsTheRowsOwnFigures() {
        // When
        TradeOutcomeEntity row = saved(sale());

        // Then — buy 40,00,000 + 3,20,000; sell 1,00,00,000 − 1,00,000; net 99,00,000 − 43,20,000 = 55,80,000
        MoneyAssert.assertMoney(320000, row.getBuyBrokerCharges());
        MoneyAssert.assertMoney(100000, row.getSellBrokerCharges());
        MoneyAssert.assertMoney(4320000, row.getTotalBuyValue());
        MoneyAssert.assertMoney(9900000, row.getTotalSellValue());
        MoneyAssert.assertMoney(5580000, row.getNetProfit());
    }

    @Test
    void recordSale_whenExpensesAreLeftOut_savesEmptyBreakdowns() {
        // Given — the request's own defaults, as an omitted JSON field leaves them
        PropertySaleRequest request = sale();
        request.setAcquisitionExpenses(new HashMap<>());
        request.setTransferExpenses(new HashMap<>());

        // When
        TradeOutcomeEntity row = saved(request);

        // Then
        assertThat(row.getBuyChargeBreakup()).isEmpty();
        assertThat(row.getSellChargeBreakup()).isEmpty();
        MoneyAssert.assertMoney(6000000, row.getNetProfit());
    }

    @Test
    void recordSale_whenTheSameSaleIsPostedTwice_refusesTheSecond() {
        // Given — the first post's row id, the same name however it is padded
        TradeOutcomeEntity first = saved(sale());
        when(tradeOutcomeRepository.existsById(first.getId())).thenReturn(true);
        PropertySaleRequest again = sale();
        again.setPropertyName("Plot 7");

        // When / Then
        assertThatThrownBy(() -> propertySaleService.recordSale(USER, again))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("The sale of Plot 7 bought on 2015-06-01 and sold on 2025-09-01 is already recorded");
        verify(tradeOutcomeRepository).save(any());
    }

    @Test
    void recordSale_whenAnotherUserSellsTheSameName_isADifferentRow() {
        // Given
        TradeOutcomeEntity mine = saved(sale());

        // When
        propertySaleService.recordSale(UserMail.from("other@example.com"), sale());

        // Then
        ArgumentCaptor<TradeOutcomeEntity> rows = ArgumentCaptor.forClass(TradeOutcomeEntity.class);
        verify(tradeOutcomeRepository, times(2)).save(rows.capture());
        assertThat(rows.getAllValues().get(1).getId()).isNotEqualTo(mine.getId());
    }

    static Stream<Arguments> malformedSales() {
        return Stream.of(
                malformed(r -> r.setPropertyName(null), "A property sale needs the property's name"),
                malformed(r -> r.setPropertyName("  "), "A property sale needs the property's name"),
                malformed(r -> r.setAcquiredOn(null),
                        "A property sale needs both the date it was bought and the date it was sold"),
                malformed(r -> r.setTransferredOn(null),
                        "A property sale needs both the date it was bought and the date it was sold"),
                malformed(r -> r.setTransferredOn(LocalDate.of(2015, 6, 1)),
                        "A property is sold after it is bought, but it was bought on 2015-06-01 and sold on 2015-06-01"),
                malformed(r -> r.setCostRupees(0), "A property sale needs a cost and a sale price above nil"),
                malformed(r -> r.setConsiderationRupees(-1), "A property sale needs a cost and a sale price above nil"),
                malformed(r -> r.setAcquisitionExpenses(null), "A property sale's expenses are a map of code to rupees;"
                        + " leave it out or empty if there were none"),
                malformed(r -> r.setTransferExpenses(Map.of("PAINTING", 100.0)),
                        "The expense PAINTING is not one of [STAMP_DUTY, REGISTRATION, BROKERAGE, LEGAL_FEE]"),
                malformed(r -> r.setAcquisitionExpenses(Map.of("STAMP_DUTY", -1.0)),
                        "The expense STAMP_DUTY needs an amount of nil or more"));
    }

    private static Arguments malformed(Consumer<PropertySaleRequest> change, String message) {
        return Arguments.of(change, message);
    }

    @ParameterizedTest
    @MethodSource("malformedSales")
    void recordSale_whenMalformed_refusesBeforeAnyRead(Consumer<PropertySaleRequest> change, String message) {
        // Given
        PropertySaleRequest request = sale();
        change.accept(request);

        // When / Then
        assertThatThrownBy(() -> propertySaleService.recordSale(USER, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(message);
        verify(tradeOutcomeRepository, never()).existsById(anyString());
        verify(tradeOutcomeRepository, never()).save(any());
    }
}
