package com.thiru.wealthlens.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.dto.context.BrokerChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.AmcChargeFrequency;
import com.thiru.wealthlens.brokercharges.dto.enums.BrokerChargeTransactionType;
import com.thiru.wealthlens.brokercharges.dto.enums.BrokerageAggregatorType;
import com.thiru.wealthlens.brokercharges.entity.BrokerCharges;
import com.thiru.wealthlens.brokercharges.entity.UserBrokerCharges;
import com.thiru.wealthlens.brokercharges.repository.UserBrokerChargesRepository;
import com.thiru.wealthlens.brokercharges.service.BrokerChargeService;
import com.thiru.wealthlens.brokercharges.service.UserBrokerChargeService;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.entity.model.BrokerageCharges;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserBrokerChargeServiceTest {

    @Mock
    private UserBrokerChargesRepository userBrokerChargesRepository;

    @Mock
    private BrokerChargeService brokerChargeService;

    @InjectMocks
    private UserBrokerChargeService userBrokerChargeService;

    private UserMail userMail;
    private BrokerCharges brokerCharges;

    @BeforeEach
    void setUp() {
        userMail = UserMail.from("test@example.com");

        brokerCharges = new BrokerCharges();
        brokerCharges.setId("broker-charges-id");
        brokerCharges.setBrokerName(BrokerName.ZERODHA);
        brokerCharges.setStt(0.1);
        brokerCharges.setSebiCharges(0.0001);
        brokerCharges.setStampDuty(0.015);
        brokerCharges.setDpChargesPerScrip(15.93);
        brokerCharges.setGstApplicableDescription("18%-brokerage,18%-dp_charges,18%-stt,18%-amc_charges");

        BrokerageCharges brokerageCharges = new BrokerageCharges();
        brokerageCharges.setBrokerage(0.1);
        brokerageCharges.setBrokerageCharges(20.0);
        brokerageCharges.setBrokerageAggregator(BrokerageAggregatorType.MIN);
        brokerageCharges.setMinimumBrokerage(10.0);
        brokerageCharges.setMaximumBrokerage(50.0);
        brokerCharges.setBrokerageCharges(brokerageCharges);
    }

    @Test
    void addUserBrokerChargeEntry_buy_success() {
        // Given
        BrokerChargeContext context = new BrokerChargeContext(
                "txn-001", "RELIANCE", BrokerName.ZERODHA,
                BrokerChargeTransactionType.BUY, LocalDate.of(2024, 1, 15),
                "NSE", null, 50000.0
        );
        when(brokerChargeService.getBrokerCharge(BrokerName.ZERODHA, LocalDate.of(2024, 1, 15)))
                .thenReturn(brokerCharges);
        when(userBrokerChargesRepository.save(any(UserBrokerCharges.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        UserBrokerCharges result = userBrokerChargeService.addUserBrokerChargeEntry(userMail, context);

        // Then
        ArgumentCaptor<UserBrokerCharges> captor = ArgumentCaptor.forClass(UserBrokerCharges.class);
        verify(userBrokerChargesRepository).save(captor.capture());

        UserBrokerCharges saved = captor.getValue();
        assertEquals("test@example.com", saved.getEmail());
        assertEquals(BrokerName.ZERODHA, saved.getBrokerName());
        assertEquals("RELIANCE", saved.getStockCode());
        assertEquals(BrokerChargeTransactionType.BUY, saved.getType());
        assertEquals("txn-001", saved.getTransactionId());
        assertEquals(LocalDate.of(2024, 1, 15), saved.getTransactionDate());

        // Brokerage: percentage = 50000 * 0.1 / 100 = 50, fixed = 20, minBrokerage = 10
        // MAX(10, 50) = 50, MIN(50, 20) = 20
        assertEquals(20.0, saved.getBrokerage());

        // Govt charges: STT (50000 * 0.1 / 100 = 50) + SEBI (50000 * 0.0001 / 100 = 0.05) + Stamp Duty (50000 * 0.015 / 100 = 7.5) = 57.55
        assertEquals(57.55, saved.getGovtCharges());

        // DP charges not applicable for BUY
        assertEquals(0.0, saved.getDpCharges());

        // Taxes: 18% * brokerage (20) + 18% * dp_charges (0) + 18% * stt (57.55) + 18% * amc_charges (0) = 3.6 + 0 + 10.359 + 0 = 13.959
        assertEquals(13.959, saved.getTaxes(), 0.001);
    }

    @Test
    void addUserBrokerChargeEntry_sell_success() {
        // Given
        BrokerChargeContext context = new BrokerChargeContext(
                "txn-002", "RELIANCE", BrokerName.ZERODHA,
                BrokerChargeTransactionType.SELL, LocalDate.of(2024, 1, 15),
                "NSE", null, 60000.0
        );
        when(brokerChargeService.getBrokerCharge(BrokerName.ZERODHA, LocalDate.of(2024, 1, 15)))
                .thenReturn(brokerCharges);
        when(userBrokerChargesRepository.findTopSellTxnByBrokerNameAndStockCodeAndTransactionDate(
                "test@example.com", BrokerName.ZERODHA, "RELIANCE", LocalDate.of(2024, 1, 15)))
                .thenReturn(Collections.emptyList());
        when(userBrokerChargesRepository.save(any(UserBrokerCharges.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        UserBrokerCharges result = userBrokerChargeService.addUserBrokerChargeEntry(userMail, context);

        // Then
        ArgumentCaptor<UserBrokerCharges> captor = ArgumentCaptor.forClass(UserBrokerCharges.class);
        verify(userBrokerChargesRepository).save(captor.capture());

        UserBrokerCharges saved = captor.getValue();
        assertEquals(BrokerChargeTransactionType.SELL, saved.getType());

        // Brokerage: percentage = 60000 * 0.1 / 100 = 60, fixed = 20, minBrokerage = 10
        // MAX(10, 60) = 60, MIN(60, 20) = 20
        assertEquals(20.0, saved.getBrokerage());

        // Govt charges for SELL: STT (60000 * 0.1 / 100 = 60) + SEBI (60000 * 0.0001 / 100 = 0.06) = 60.06 (no stamp duty)
        assertEquals(60.06, saved.getGovtCharges());

        // DP charges applied for first sell
        assertEquals(15.93, saved.getDpCharges());

        // Taxes: 18% * brokerage (20) + 18% * dp_charges (15.93) + 18% * stt (60.06) + 18% * amc_charges (0)
        // = 3.6 + 2.8674 + 10.8108 + 0 = 17.2782
        assertEquals(17.2782, saved.getTaxes(), 0.001);
    }

    @Test
    void addUserBrokerChargeEntry_sell_dpChargeDedup_firstSellApplied() {
        // Given
        BrokerChargeContext context = new BrokerChargeContext(
                "txn-003", "RELIANCE", BrokerName.ZERODHA,
                BrokerChargeTransactionType.SELL, LocalDate.of(2024, 1, 15),
                "NSE", null, 30000.0
        );
        when(brokerChargeService.getBrokerCharge(BrokerName.ZERODHA, LocalDate.of(2024, 1, 15)))
                .thenReturn(brokerCharges);
        // No existing sell transaction - DP charges should apply
        when(userBrokerChargesRepository.findTopSellTxnByBrokerNameAndStockCodeAndTransactionDate(
                "test@example.com", BrokerName.ZERODHA, "RELIANCE", LocalDate.of(2024, 1, 15)))
                .thenReturn(Collections.emptyList());
        when(userBrokerChargesRepository.save(any(UserBrokerCharges.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        UserBrokerCharges result = userBrokerChargeService.addUserBrokerChargeEntry(userMail, context);

        // Then
        ArgumentCaptor<UserBrokerCharges> captor = ArgumentCaptor.forClass(UserBrokerCharges.class);
        verify(userBrokerChargesRepository).save(captor.capture());

        UserBrokerCharges saved = captor.getValue();
        assertEquals(15.93, saved.getDpCharges());
    }

    @Test
    void addUserBrokerChargeEntry_sell_dpChargeDedup_secondSellSkipped() {
        // Given
        BrokerChargeContext context = new BrokerChargeContext(
                "txn-004", "RELIANCE", BrokerName.ZERODHA,
                BrokerChargeTransactionType.SELL, LocalDate.of(2024, 1, 15),
                "NSE", null, 25000.0
        );
        when(brokerChargeService.getBrokerCharge(BrokerName.ZERODHA, LocalDate.of(2024, 1, 15)))
                .thenReturn(brokerCharges);
        // Existing sell transaction found - DP charges should be skipped
        UserBrokerCharges existingSell = new UserBrokerCharges();
        existingSell.setId("existing-id");
        when(userBrokerChargesRepository.findTopSellTxnByBrokerNameAndStockCodeAndTransactionDate(
                "test@example.com", BrokerName.ZERODHA, "RELIANCE", LocalDate.of(2024, 1, 15)))
                .thenReturn(List.of(existingSell));
        when(userBrokerChargesRepository.save(any(UserBrokerCharges.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        UserBrokerCharges result = userBrokerChargeService.addUserBrokerChargeEntry(userMail, context);

        // Then
        ArgumentCaptor<UserBrokerCharges> captor = ArgumentCaptor.forClass(UserBrokerCharges.class);
        verify(userBrokerChargesRepository).save(captor.capture());

        UserBrokerCharges saved = captor.getValue();
        assertEquals(0.0, saved.getDpCharges());
    }

    @Test
    void addUserBrokerChargeEntry_amcQuarterly_success() {
        // Given
        brokerCharges.setAmcChargesAnnually(400.0);
        brokerCharges.setAmcChargeFrequency(AmcChargeFrequency.QUARTERLY);

        BrokerChargeContext context = new BrokerChargeContext(
                "txn-005", null, BrokerName.ZERODHA,
                BrokerChargeTransactionType.AMC_CHARGES, LocalDate.of(2024, 1, 15),
                null, null, 0.0
        );
        when(brokerChargeService.getBrokerCharge(BrokerName.ZERODHA, LocalDate.of(2024, 1, 15)))
                .thenReturn(brokerCharges);
        when(userBrokerChargesRepository.save(any(UserBrokerCharges.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        UserBrokerCharges result = userBrokerChargeService.addUserBrokerChargeEntry(userMail, context);

        // Then
        ArgumentCaptor<UserBrokerCharges> captor = ArgumentCaptor.forClass(UserBrokerCharges.class);
        verify(userBrokerChargesRepository).save(captor.capture());

        UserBrokerCharges saved = captor.getValue();
        assertEquals(BrokerChargeTransactionType.AMC_CHARGES, saved.getType());
        assertEquals(100.0, saved.getAmcCharges()); // 400 / 4 = 100

        // Taxes: 18% * amc_charges (100) = 18 (brokerage=0, dp=0, stt=0)
        assertEquals(18.0, saved.getTaxes(), 0.001);
    }

    @Test
    void addUserBrokerChargeEntry_amcAnnually_success() {
        // Given
        brokerCharges.setAmcChargesAnnually(1000.0);
        brokerCharges.setAmcChargeFrequency(AmcChargeFrequency.ANNUALLY);

        BrokerChargeContext context = new BrokerChargeContext(
                "txn-006", null, BrokerName.ZERODHA,
                BrokerChargeTransactionType.AMC_CHARGES, LocalDate.of(2024, 1, 15),
                null, null, 0.0
        );
        when(brokerChargeService.getBrokerCharge(BrokerName.ZERODHA, LocalDate.of(2024, 1, 15)))
                .thenReturn(brokerCharges);
        when(userBrokerChargesRepository.save(any(UserBrokerCharges.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        UserBrokerCharges result = userBrokerChargeService.addUserBrokerChargeEntry(userMail, context);

        // Then
        ArgumentCaptor<UserBrokerCharges> captor = ArgumentCaptor.forClass(UserBrokerCharges.class);
        verify(userBrokerChargesRepository).save(captor.capture());

        UserBrokerCharges saved = captor.getValue();
        assertEquals(BrokerChargeTransactionType.AMC_CHARGES, saved.getType());
        assertEquals(1000.0, saved.getAmcCharges()); // full annual amount

        // Taxes: 18% * amc_charges (1000) = 180
        assertEquals(180.0, saved.getTaxes(), 0.001);
    }

    @Test
    void addUserBrokerChargeEntry_noBrokerTemplate_returnsNull() {
        // Given
        BrokerChargeContext context = new BrokerChargeContext(
                "txn-007", "RELIANCE", BrokerName.ZERODHA,
                BrokerChargeTransactionType.BUY, LocalDate.of(2024, 1, 15),
                "NSE", null, 50000.0
        );
        when(brokerChargeService.getBrokerCharge(BrokerName.ZERODHA, LocalDate.of(2024, 1, 15)))
                .thenReturn(null);

        // When
        UserBrokerCharges result = userBrokerChargeService.addUserBrokerChargeEntry(userMail, context);

        // Then
        assertNull(result);
    }

    @Test
    void addUserBrokerChargeEntry_amcNullFrequency_returnsNull() {
        // Given
        brokerCharges.setAmcChargesAnnually(400.0);
        brokerCharges.setAmcChargeFrequency(null);

        BrokerChargeContext context = new BrokerChargeContext(
                "txn-008", null, BrokerName.ZERODHA,
                BrokerChargeTransactionType.AMC_CHARGES, LocalDate.of(2024, 1, 15),
                null, null, 0.0
        );
        when(brokerChargeService.getBrokerCharge(BrokerName.ZERODHA, LocalDate.of(2024, 1, 15)))
                .thenReturn(brokerCharges);

        // When
        UserBrokerCharges result = userBrokerChargeService.addUserBrokerChargeEntry(userMail, context);

        // Then
        assertNull(result);
    }

    @Test
    void getBrokerage_minAggregator() {
        // Given - MIN aggregator: max(minimumBrokerage, percentageAmount), then min(result, fixedCharges)
        BrokerChargeContext context = new BrokerChargeContext(
                "txn-009", "RELIANCE", BrokerName.ZERODHA,
                BrokerChargeTransactionType.BUY, LocalDate.of(2024, 1, 15),
                "NSE", null, 50000.0 // 0.1% = 50, minBrokerage = 10, fixed = 20
        );
        // max(10, 50) = 50, min(50, 20) = 20
        when(brokerChargeService.getBrokerCharge(BrokerName.ZERODHA, LocalDate.of(2024, 1, 15)))
                .thenReturn(brokerCharges);
        when(userBrokerChargesRepository.save(any(UserBrokerCharges.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        UserBrokerCharges result = userBrokerChargeService.addUserBrokerChargeEntry(userMail, context);

        // Then
        ArgumentCaptor<UserBrokerCharges> captor = ArgumentCaptor.forClass(UserBrokerCharges.class);
        verify(userBrokerChargesRepository).save(captor.capture());

        UserBrokerCharges saved = captor.getValue();
        // MIN aggregator: MAX(minimumBrokerage, percentageAmount) = MAX(10, 50) = 50
        // then MIN(50, brokerageCharges) = MIN(50, 20) = 20
        assertEquals(20.0, saved.getBrokerage());
    }

    @Test
    void getBrokerage_maxAggregator() {
        // Given - MAX aggregator: min(maximumBrokerage, percentageAmount), then max(result, fixedCharges)
        BrokerChargeContext context = new BrokerChargeContext(
                "txn-010", "RELIANCE", BrokerName.ZERODHA,
                BrokerChargeTransactionType.BUY, LocalDate.of(2024, 1, 15),
                "NSE", null, 50000.0 // 0.1% = 50, maxBrokerage = 50, fixed = 20
        );

        BrokerageCharges maxAggCharges = new BrokerageCharges();
        maxAggCharges.setBrokerage(0.1);
        maxAggCharges.setBrokerageCharges(20.0);
        maxAggCharges.setBrokerageAggregator(BrokerageAggregatorType.MAX);
        maxAggCharges.setMinimumBrokerage(10.0);
        maxAggCharges.setMaximumBrokerage(50.0);
        brokerCharges.setBrokerageCharges(maxAggCharges);

        when(brokerChargeService.getBrokerCharge(BrokerName.ZERODHA, LocalDate.of(2024, 1, 15)))
                .thenReturn(brokerCharges);
        when(userBrokerChargesRepository.save(any(UserBrokerCharges.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        UserBrokerCharges result = userBrokerChargeService.addUserBrokerChargeEntry(userMail, context);

        // Then
        ArgumentCaptor<UserBrokerCharges> captor = ArgumentCaptor.forClass(UserBrokerCharges.class);
        verify(userBrokerChargesRepository).save(captor.capture());

        UserBrokerCharges saved = captor.getValue();
        // MAX aggregator: MIN(maximumBrokerage, percentageAmount) = MIN(50, 50) = 50
        // then MAX(50, brokerageCharges) = MAX(50, 20) = 50
        assertEquals(50.0, saved.getBrokerage());
    }

    @Test
    void getBrokerage_nullBrokerageCharges_returnsZero() {
        // Given
        brokerCharges.setBrokerageCharges(null);

        BrokerChargeContext context = new BrokerChargeContext(
                "txn-011", "RELIANCE", BrokerName.ZERODHA,
                BrokerChargeTransactionType.BUY, LocalDate.of(2024, 1, 15),
                "NSE", null, 50000.0
        );
        when(brokerChargeService.getBrokerCharge(BrokerName.ZERODHA, LocalDate.of(2024, 1, 15)))
                .thenReturn(brokerCharges);
        when(userBrokerChargesRepository.save(any(UserBrokerCharges.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        UserBrokerCharges result = userBrokerChargeService.addUserBrokerChargeEntry(userMail, context);

        // Then
        ArgumentCaptor<UserBrokerCharges> captor = ArgumentCaptor.forClass(UserBrokerCharges.class);
        verify(userBrokerChargesRepository).save(captor.capture());

        UserBrokerCharges saved = captor.getValue();
        assertEquals(0.0, saved.getBrokerage());
    }

    @Test
    void getGovtCharges_buy_includesStampDuty() {
        // Given
        BrokerChargeContext context = new BrokerChargeContext(
                "txn-012", "RELIANCE", BrokerName.ZERODHA,
                BrokerChargeTransactionType.BUY, LocalDate.of(2024, 1, 15),
                "NSE", null, 100000.0
        );
        when(brokerChargeService.getBrokerCharge(BrokerName.ZERODHA, LocalDate.of(2024, 1, 15)))
                .thenReturn(brokerCharges);
        when(userBrokerChargesRepository.save(any(UserBrokerCharges.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        UserBrokerCharges result = userBrokerChargeService.addUserBrokerChargeEntry(userMail, context);

        // Then
        ArgumentCaptor<UserBrokerCharges> captor = ArgumentCaptor.forClass(UserBrokerCharges.class);
        verify(userBrokerChargesRepository).save(captor.capture());

        UserBrokerCharges saved = captor.getValue();
        // STT: 100000 * 0.1 / 100 = 100
        // SEBI: 100000 * 0.0001 / 100 = 0.1
        // Stamp Duty: 100000 * 0.015 / 100 = 15
        // Total: 100 + 0.1 + 15 = 115.1
        assertEquals(115.1, saved.getGovtCharges());
    }

    @Test
    void getGovtCharges_sell_excludesStampDuty() {
        // Given
        BrokerChargeContext context = new BrokerChargeContext(
                "txn-013", "RELIANCE", BrokerName.ZERODHA,
                BrokerChargeTransactionType.SELL, LocalDate.of(2024, 1, 15),
                "NSE", null, 100000.0
        );
        when(brokerChargeService.getBrokerCharge(BrokerName.ZERODHA, LocalDate.of(2024, 1, 15)))
                .thenReturn(brokerCharges);
        when(userBrokerChargesRepository.findTopSellTxnByBrokerNameAndStockCodeAndTransactionDate(
                "test@example.com", BrokerName.ZERODHA, "RELIANCE", LocalDate.of(2024, 1, 15)))
                .thenReturn(Collections.emptyList());
        when(userBrokerChargesRepository.save(any(UserBrokerCharges.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        UserBrokerCharges result = userBrokerChargeService.addUserBrokerChargeEntry(userMail, context);

        // Then
        ArgumentCaptor<UserBrokerCharges> captor = ArgumentCaptor.forClass(UserBrokerCharges.class);
        verify(userBrokerChargesRepository).save(captor.capture());

        UserBrokerCharges saved = captor.getValue();
        // STT: 100000 * 0.1 / 100 = 100
        // SEBI: 100000 * 0.0001 / 100 = 0.1
        // Stamp Duty: NOT included for SELL
        // Total: 100 + 0.1 = 100.1
        assertEquals(100.1, saved.getGovtCharges());
    }

    @Test
    void setTaxes_normal() {
        // Given - GST description: "18%-brokerage,18%-stt"
        brokerCharges.setGstApplicableDescription("18%-brokerage,18%-stt");

        BrokerChargeContext context = new BrokerChargeContext(
                "txn-014", "RELIANCE", BrokerName.ZERODHA,
                BrokerChargeTransactionType.BUY, LocalDate.of(2024, 1, 15),
                "NSE", null, 100000.0
        );
        when(brokerChargeService.getBrokerCharge(BrokerName.ZERODHA, LocalDate.of(2024, 1, 15)))
                .thenReturn(brokerCharges);
        when(userBrokerChargesRepository.save(any(UserBrokerCharges.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        UserBrokerCharges result = userBrokerChargeService.addUserBrokerChargeEntry(userMail, context);

        // Then
        ArgumentCaptor<UserBrokerCharges> captor = ArgumentCaptor.forClass(UserBrokerCharges.class);
        verify(userBrokerChargesRepository).save(captor.capture());

        UserBrokerCharges saved = captor.getValue();
        // Brokerage: percentage = 100000 * 0.1 / 100 = 100, minBrokerage = 10, fixed = 20
        // MAX(10, 100) = 100, MIN(100, 20) = 20
        // Govt charges: STT (100) + SEBI (0.1) + Stamp Duty (15) = 115.1
        // Taxes: 18% * 20 (brokerage) + 18% * 115.1 (stt) = 3.6 + 20.718 = 24.318
        assertEquals(24.318, saved.getTaxes(), 0.001);
    }

    @Test
    void setTaxes_nullGstDescription_setsZeroTax() {
        // Given
        brokerCharges.setGstApplicableDescription(null);

        BrokerChargeContext context = new BrokerChargeContext(
                "txn-015", "RELIANCE", BrokerName.ZERODHA,
                BrokerChargeTransactionType.BUY, LocalDate.of(2024, 1, 15),
                "NSE", null, 50000.0
        );
        when(brokerChargeService.getBrokerCharge(BrokerName.ZERODHA, LocalDate.of(2024, 1, 15)))
                .thenReturn(brokerCharges);
        when(userBrokerChargesRepository.save(any(UserBrokerCharges.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        UserBrokerCharges result = userBrokerChargeService.addUserBrokerChargeEntry(userMail, context);

        // Then
        ArgumentCaptor<UserBrokerCharges> captor = ArgumentCaptor.forClass(UserBrokerCharges.class);
        verify(userBrokerChargesRepository).save(captor.capture());

        UserBrokerCharges saved = captor.getValue();
        assertEquals(0.0, saved.getTaxes());
    }

    @Test
    void setTaxes_blankGstDescription_setsZeroTax() {
        // Given
        brokerCharges.setGstApplicableDescription("   ");

        BrokerChargeContext context = new BrokerChargeContext(
                "txn-016", "RELIANCE", BrokerName.ZERODHA,
                BrokerChargeTransactionType.BUY, LocalDate.of(2024, 1, 15),
                "NSE", null, 50000.0
        );
        when(brokerChargeService.getBrokerCharge(BrokerName.ZERODHA, LocalDate.of(2024, 1, 15)))
                .thenReturn(brokerCharges);
        when(userBrokerChargesRepository.save(any(UserBrokerCharges.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        UserBrokerCharges result = userBrokerChargeService.addUserBrokerChargeEntry(userMail, context);

        // Then
        ArgumentCaptor<UserBrokerCharges> captor = ArgumentCaptor.forClass(UserBrokerCharges.class);
        verify(userBrokerChargesRepository).save(captor.capture());

        UserBrokerCharges saved = captor.getValue();
        assertEquals(0.0, saved.getTaxes());
    }

    @Test
    void setTaxes_unknownComponent_logsError() {
        // Given - GST with unknown component "unknown_charge"
        brokerCharges.setGstApplicableDescription("18%-brokerage,18%-unknown_charge,18%-stt");

        BrokerChargeContext context = new BrokerChargeContext(
                "txn-017", "RELIANCE", BrokerName.ZERODHA,
                BrokerChargeTransactionType.BUY, LocalDate.of(2024, 1, 15),
                "NSE", null, 100000.0
        );
        when(brokerChargeService.getBrokerCharge(BrokerName.ZERODHA, LocalDate.of(2024, 1, 15)))
                .thenReturn(brokerCharges);
        when(userBrokerChargesRepository.save(any(UserBrokerCharges.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        UserBrokerCharges result = userBrokerChargeService.addUserBrokerChargeEntry(userMail, context);

        // Then
        ArgumentCaptor<UserBrokerCharges> captor = ArgumentCaptor.forClass(UserBrokerCharges.class);
        verify(userBrokerChargesRepository).save(captor.capture());

        UserBrokerCharges saved = captor.getValue();
        // Taxes: 18% * brokerage (20) + 0 (unknown) + 18% * stt (115.1) = 3.6 + 0 + 20.718 = 24.318
        assertEquals(24.318, saved.getTaxes(), 0.001);
    }

    @Test
    void getTaxComponentTax_amcAppliesPercentage() {
        // Given - AMC charges with "18%-amc_charges" in GST
        brokerCharges.setAmcChargesAnnually(1000.0);
        brokerCharges.setAmcChargeFrequency(AmcChargeFrequency.ANNUALLY);
        brokerCharges.setGstApplicableDescription("18%-amc_charges");

        BrokerChargeContext context = new BrokerChargeContext(
                "txn-018", null, BrokerName.ZERODHA,
                BrokerChargeTransactionType.AMC_CHARGES, LocalDate.of(2024, 1, 15),
                null, null, 0.0
        );
        when(brokerChargeService.getBrokerCharge(BrokerName.ZERODHA, LocalDate.of(2024, 1, 15)))
                .thenReturn(brokerCharges);
        when(userBrokerChargesRepository.save(any(UserBrokerCharges.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        UserBrokerCharges result = userBrokerChargeService.addUserBrokerChargeEntry(userMail, context);

        // Then
        ArgumentCaptor<UserBrokerCharges> captor = ArgumentCaptor.forClass(UserBrokerCharges.class);
        verify(userBrokerChargesRepository).save(captor.capture());

        UserBrokerCharges saved = captor.getValue();
        assertEquals(1000.0, saved.getAmcCharges());
        // Taxes: 18% * 1000 (amc_charges) = 180
        assertEquals(180.0, saved.getTaxes(), 0.001);
    }

    @Test
    void deleteUserBrokerCharges_success() {
        // When
        userBrokerChargeService.deleteUserBrokerCharges(userMail);

        // Then
        verify(userBrokerChargesRepository).deleteByEmail("test@example.com");
    }
}
