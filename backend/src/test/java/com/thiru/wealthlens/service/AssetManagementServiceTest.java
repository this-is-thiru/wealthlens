package com.thiru.wealthlens.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.dto.enums.AmcChargeFrequency;
import com.thiru.wealthlens.brokercharges.dto.request.AssetManagementDetailsRequest;
import com.thiru.wealthlens.brokercharges.entity.UserBrokerCharges;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.entity.AssetManagementDetails;
import com.thiru.wealthlens.portfolio.repository.AssetManagementRepository;
import com.thiru.wealthlens.portfolio.service.AssetManagementService;
import com.thiru.wealthlens.portfolio.service.ProfitAndLossService;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetManagementServiceTest {

    @Mock
    private AssetManagementRepository assetManagementRepository;

    @Mock
    private ProfitAndLossService profitAndLossService;

    @InjectMocks
    private AssetManagementService assetManagementService;

    @Test
    void imposeAmcCharges_noOverdueAccounts_nothingProcessed() {
        // Given
        when(assetManagementRepository.findEntriesToUpdateAmcCharges(any(AmcChargeFrequency.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        // When
        assetManagementService.imposeAmcCharges();

        // Then
        verify(assetManagementRepository).findEntriesToUpdateAmcCharges(eq(AmcChargeFrequency.QUARTERLY), any());
        verify(assetManagementRepository).findEntriesToUpdateAmcCharges(eq(AmcChargeFrequency.ANNUALLY), any());
        verify(assetManagementRepository, never()).save(any());
        verify(profitAndLossService, never()).updateProfitAndLossWithAmcCharges(any(), any());
    }

    @Test
    void imposeAmcCharges_quarterlyOverdue_processed() {
        // Given
        AssetManagementDetails quarterlyAccount = new AssetManagementDetails();
        quarterlyAccount.setId("detail-id-1");
        quarterlyAccount.setEmail("user@example.com");
        quarterlyAccount.setBrokerName(BrokerName.ZERODHA);
        quarterlyAccount.setLastAmcChargesDeductedOn(LocalDate.now().minusDays(95));
        quarterlyAccount.setAmcChargesEvents(new java.util.ArrayList<>());

        when(assetManagementRepository.findEntriesToUpdateAmcCharges(eq(AmcChargeFrequency.QUARTERLY), any()))
                .thenReturn(List.of(quarterlyAccount));
        when(assetManagementRepository.findEntriesToUpdateAmcCharges(eq(AmcChargeFrequency.ANNUALLY), any()))
                .thenReturn(Collections.emptyList());

        UserBrokerCharges userBrokerCharges = new UserBrokerCharges();
        userBrokerCharges.setId("charges-id-1");
        userBrokerCharges.setAmcCharges(100.0);
        userBrokerCharges.setTaxes(18.0);
        when(profitAndLossService.updateProfitAndLossWithAmcCharges(any(), any())).thenReturn(userBrokerCharges);

        // When
        assetManagementService.imposeAmcCharges();

        // Then
        ArgumentCaptor<AssetManagementDetails> captor = ArgumentCaptor.forClass(AssetManagementDetails.class);
        verify(assetManagementRepository).save(captor.capture());

        AssetManagementDetails saved = captor.getValue();
        assertEquals(LocalDate.now().minusDays(95).plusDays(91), saved.getLastAmcChargesDeductedOn());
        assertEquals(1, saved.getAmcChargesEvents().size());
        assertEquals("charges-id-1", saved.getAmcChargesEvents().get(0).userChargesId());
        assertEquals(118.0, saved.getAmcChargesEvents().get(0).deductionAmount());
    }

    @Test
    void imposeAmcCharges_annuallyOverdue_processed() {
        // Given
        AssetManagementDetails annualAccount = new AssetManagementDetails();
        annualAccount.setId("detail-id-2");
        annualAccount.setEmail("user@example.com");
        annualAccount.setBrokerName(BrokerName.ZERODHA);
        annualAccount.setAmcChargesFrequency(AmcChargeFrequency.ANNUALLY);
        annualAccount.setLastAmcChargesDeductedOn(LocalDate.now().minusYears(1).minusDays(5));
        annualAccount.setAmcChargesEvents(new java.util.ArrayList<>());

        when(assetManagementRepository.findEntriesToUpdateAmcCharges(eq(AmcChargeFrequency.QUARTERLY), any()))
                .thenReturn(Collections.emptyList());
        when(assetManagementRepository.findEntriesToUpdateAmcCharges(eq(AmcChargeFrequency.ANNUALLY), any()))
                .thenReturn(List.of(annualAccount));

        UserBrokerCharges userBrokerCharges = new UserBrokerCharges();
        userBrokerCharges.setId("charges-id-2");
        userBrokerCharges.setAmcCharges(200.0);
        userBrokerCharges.setTaxes(36.0);
        when(profitAndLossService.updateProfitAndLossWithAmcCharges(any(), any())).thenReturn(userBrokerCharges);

        // When
        assetManagementService.imposeAmcCharges();

        // Then
        ArgumentCaptor<AssetManagementDetails> captor = ArgumentCaptor.forClass(AssetManagementDetails.class);
        verify(assetManagementRepository).save(captor.capture());

        AssetManagementDetails saved = captor.getValue();
        assertEquals(LocalDate.now().minusYears(1).minusDays(5).plusYears(1), saved.getLastAmcChargesDeductedOn());
        assertEquals(1, saved.getAmcChargesEvents().size());
        assertEquals("charges-id-2", saved.getAmcChargesEvents().get(0).userChargesId());
    }

    @Test
    void imposeAmcCharges_notYetDue_skipped() {
        // Given - account with last deduction 60 days ago (not yet due for quarterly which requires 91 days)
        AssetManagementDetails recentAccount = new AssetManagementDetails();
        recentAccount.setId("detail-id-3");
        recentAccount.setEmail("user@example.com");
        recentAccount.setBrokerName(BrokerName.ZERODHA);
        recentAccount.setLastAmcChargesDeductedOn(LocalDate.now().minusDays(60));

        when(assetManagementRepository.findEntriesToUpdateAmcCharges(eq(AmcChargeFrequency.QUARTERLY), any()))
                .thenReturn(Collections.emptyList());
        when(assetManagementRepository.findEntriesToUpdateAmcCharges(eq(AmcChargeFrequency.ANNUALLY), any()))
                .thenReturn(Collections.emptyList());

        // When
        assetManagementService.imposeAmcCharges();

        // Then
        verify(assetManagementRepository, never()).save(any());
        verify(profitAndLossService, never()).updateProfitAndLossWithAmcCharges(any(), any());
    }

    @Test
    void addAssetManagementEntry_new_creates() {
        // Given
        UserMail userMail = UserMail.from("new@example.com");
        AssetManagementDetailsRequest request = new AssetManagementDetailsRequest();
        request.setBrokerName(BrokerName.ZERODHA);
        request.setAccountOpeningCharges(200.0);
        request.setTaxOnAccountOpeningCharges(36.0);
        request.setLastAmcChargesDeductedOn(LocalDate.of(2024, 1, 1));
        request.setAmcChargesFrequency(AmcChargeFrequency.ANNUALLY);

        when(assetManagementRepository.findByEmailAndBrokerName("new@example.com", BrokerName.ZERODHA))
                .thenReturn(Optional.empty());
        when(assetManagementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        assetManagementService.addAssetManagementEntry(userMail, request);

        // Then
        ArgumentCaptor<AssetManagementDetails> captor = ArgumentCaptor.forClass(AssetManagementDetails.class);
        verify(assetManagementRepository).save(captor.capture());

        AssetManagementDetails saved = captor.getValue();
        assertEquals("new@example.com", saved.getEmail());
        assertEquals(BrokerName.ZERODHA, saved.getBrokerName());
        assertEquals(200.0, saved.getAccountOpeningCharges());
        assertEquals(36.0, saved.getTaxOnAccountOpeningCharges());
        assertEquals(LocalDate.of(2024, 1, 1), saved.getLastAmcChargesDeductedOn());
        assertEquals(AmcChargeFrequency.ANNUALLY, saved.getAmcChargesFrequency());
        assertNotNull(saved.getAmcChargesEvents());
        assertTrue(saved.getAmcChargesEvents().isEmpty());
    }

    @Test
    void addAssetManagementEntry_existing_updates() {
        // Given
        UserMail userMail = UserMail.from("existing@example.com");
        AssetManagementDetailsRequest request = new AssetManagementDetailsRequest();
        request.setBrokerName(BrokerName.ZERODHA);
        request.setAccountOpeningCharges(300.0);
        request.setTaxOnAccountOpeningCharges(54.0);
        request.setLastAmcChargesDeductedOn(LocalDate.of(2024, 6, 1));
        request.setAmcChargesFrequency(AmcChargeFrequency.QUARTERLY);

        AssetManagementDetails existing = new AssetManagementDetails();
        existing.setId("existing-id-123");
        existing.setEmail("existing@example.com");
        existing.setBrokerName(BrokerName.ZERODHA);

        when(assetManagementRepository.findByEmailAndBrokerName("existing@example.com", BrokerName.ZERODHA))
                .thenReturn(Optional.of(existing));
        when(assetManagementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        assetManagementService.addAssetManagementEntry(userMail, request);

        // Then
        ArgumentCaptor<AssetManagementDetails> captor = ArgumentCaptor.forClass(AssetManagementDetails.class);
        verify(assetManagementRepository).save(captor.capture());

        AssetManagementDetails saved = captor.getValue();
        assertEquals("existing-id-123", saved.getId());
        assertEquals("existing@example.com", saved.getEmail());
        assertEquals(BrokerName.ZERODHA, saved.getBrokerName());
        assertEquals(300.0, saved.getAccountOpeningCharges());
        assertEquals(54.0, saved.getTaxOnAccountOpeningCharges());
        assertEquals(LocalDate.of(2024, 6, 1), saved.getLastAmcChargesDeductedOn());
        assertEquals(AmcChargeFrequency.QUARTERLY, saved.getAmcChargesFrequency());
    }

    @Test
    void addAssetManagementEntry_nullLastAmcDate_throws() {
        // Given
        UserMail userMail = UserMail.from("user@example.com");
        AssetManagementDetailsRequest request = new AssetManagementDetailsRequest();
        request.setBrokerName(BrokerName.ZERODHA);
        request.setLastAmcChargesDeductedOn(null);

        // When / Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> assetManagementService.addAssetManagementEntry(userMail, request)
        );

        assertEquals("Last Amc Charges Deducted On cannot be null", exception.getMessage());
        verify(assetManagementRepository, never()).save(any());
    }

    @Test
    void getAssetManagementDetails_success() {
        // Given
        UserMail userMail = UserMail.from("user@example.com");
        AssetManagementDetails detail1 = new AssetManagementDetails();
        detail1.setId("id-1");
        detail1.setEmail("user@example.com");
        detail1.setBrokerName(BrokerName.ZERODHA);

        AssetManagementDetails detail2 = new AssetManagementDetails();
        detail2.setId("id-2");
        detail2.setEmail("user@example.com");
        detail2.setBrokerName(BrokerName.UPSTOX);

        when(assetManagementRepository.findByEmail("user@example.com"))
                .thenReturn(List.of(detail1, detail2));

        // When
        List<AssetManagementDetails> result = assetManagementService.getAssetManagementDetails(userMail);

        // Then
        assertEquals(2, result.size());
        verify(assetManagementRepository).findByEmail("user@example.com");
    }
}
