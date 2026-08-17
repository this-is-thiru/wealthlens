package com.thiru.wealthlens.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.thiru.wealthlens.brokercharges.dto.enums.AmcChargeFrequency;
import com.thiru.wealthlens.brokercharges.dto.enums.BrokerageAggregatorType;
import com.thiru.wealthlens.brokercharges.dto.helper.BrokerageChargesDto;
import com.thiru.wealthlens.brokercharges.dto.request.BrokerChargesRequest;
import com.thiru.wealthlens.brokercharges.entity.BrokerCharges;
import com.thiru.wealthlens.brokercharges.repository.BrokerChargesRepository;
import com.thiru.wealthlens.brokercharges.service.BrokerChargeService;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BrokerChargeServiceTest {

    @Mock
    private BrokerChargesRepository brokerChargesRepository;

    @InjectMocks
    private BrokerChargeService brokerChargeService;

    @Test
    void addBrokerCharge_whenNoExistingActiveTemplate_success() {
        // Given
        BrokerChargesRequest request = createBrokerChargesRequest();
        when(brokerChargesRepository.findActiveBrokerChargesOnDate(any(BrokerName.class), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(brokerChargesRepository.save(any(BrokerCharges.class)))
                .thenAnswer(invocation -> {
                    BrokerCharges entity = invocation.getArgument(0);
                    entity.setId("test-id-123");
                    return entity;
                });

        // When
        String result = brokerChargeService.addBrokerCharge(request);

        // Then
        assertTrue(result.contains("test-id-123"));
        assertTrue(result.contains("added successfully"));
        verify(brokerChargesRepository).save(any(BrokerCharges.class));
    }

    @Test
    void addBrokerCharge_whenDuplicateActiveRange_throwsBadRequest() {
        // Given
        BrokerChargesRequest request = createBrokerChargesRequest();
        BrokerCharges existingTemplate = new BrokerCharges();
        existingTemplate.setId("existing-id");
        when(brokerChargesRepository.findActiveBrokerChargesOnDate(any(BrokerName.class), any(LocalDate.class)))
                .thenReturn(Optional.of(existingTemplate));

        // When & Then
        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> brokerChargeService.addBrokerCharge(request));
        assertTrue(exception.getMessage().contains("Broker charges already exist"));
        assertTrue(exception.getMessage().contains("existing-id"));
        verify(brokerChargesRepository, never()).save(any());
    }

    @Test
    void getBrokerCharge_whenActiveTemplateExists_returnsEntity() {
        // Given
        BrokerCharges expectedEntity = new BrokerCharges();
        expectedEntity.setBrokerName(BrokerName.UPSTOX);
        when(brokerChargesRepository.findActiveBrokerChargesOnDate(eq(BrokerName.UPSTOX), any(LocalDate.class)))
                .thenReturn(Optional.of(expectedEntity));

        // When
        BrokerCharges result = brokerChargeService.getBrokerCharge(BrokerName.UPSTOX, LocalDate.now());

        // Then
        assertNotNull(result);
        assertEquals(BrokerName.UPSTOX, result.getBrokerName());
    }

    @Test
    void getBrokerCharge_whenNoActiveTemplate_returnsNull() {
        // Given
        when(brokerChargesRepository.findActiveBrokerChargesOnDate(any(BrokerName.class), any(LocalDate.class)))
                .thenReturn(Optional.empty());

        // When
        BrokerCharges result = brokerChargeService.getBrokerCharge(BrokerName.ZERODHA, LocalDate.now());

        // Then
        assertNull(result);
    }

    @Test
    void getBrokerCharges_whenFound_returnsEntity() {
        // Given
        BrokerCharges expectedEntity = new BrokerCharges();
        expectedEntity.setId("test-id");
        expectedEntity.setBrokerName(BrokerName.FYERS);
        when(brokerChargesRepository.findById("test-id"))
                .thenReturn(Optional.of(expectedEntity));

        // When
        BrokerCharges result = brokerChargeService.getBrokerCharges("test-id");

        // Then
        assertNotNull(result);
        assertEquals("test-id", result.getId());
        assertEquals(BrokerName.FYERS, result.getBrokerName());
    }

    @Test
    void getBrokerCharges_whenNotFound_throwsBadRequest() {
        // Given
        when(brokerChargesRepository.findById("non-existent-id"))
                .thenReturn(Optional.empty());

        // When & Then
        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> brokerChargeService.getBrokerCharges("non-existent-id"));
        assertTrue(exception.getMessage().contains("not found"));
        assertTrue(exception.getMessage().contains("non-existent-id"));
    }

    @Test
    void changeEndDate_success() {
        // Given
        BrokerCharges existingEntity = new BrokerCharges();
        existingEntity.setId("test-id");
        when(brokerChargesRepository.findById("test-id"))
                .thenReturn(Optional.of(existingEntity));
        when(brokerChargesRepository.save(any(BrokerCharges.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LocalDate newEndDate = LocalDate.of(2025, 12, 31);

        // When
        String result = brokerChargeService.changeEndDate("test-id", newEndDate);

        // Then
        assertTrue(result.contains("test-id"));
        assertTrue(result.contains("updated successfully"));
        assertEquals(newEndDate, existingEntity.getEndDate());
        verify(brokerChargesRepository).save(existingEntity);
    }

    @Test
    void changeEndDate_notFound_throwsBadRequest() {
        // Given
        when(brokerChargesRepository.findById("non-existent-id"))
                .thenReturn(Optional.empty());

        // When & Then
        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> brokerChargeService.changeEndDate("non-existent-id", LocalDate.now()));
        assertTrue(exception.getMessage().contains("not found"));
        assertTrue(exception.getMessage().contains("non-existent-id"));
        verify(brokerChargesRepository, never()).save(any());
    }

    @Test
    void addBrokerCharge_toEntity_mapsAmcChargeFrequency() {
        // Given
        BrokerChargesRequest request = createBrokerChargesRequest();
        request.setAmcChargesFrequency(AmcChargeFrequency.QUARTERLY);

        when(brokerChargesRepository.findActiveBrokerChargesOnDate(any(BrokerName.class), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(brokerChargesRepository.save(any(BrokerCharges.class)))
                .thenAnswer(invocation -> {
                    BrokerCharges entity = invocation.getArgument(0);
                    entity.setId("test-id-123");
                    return entity;
                });

        ArgumentCaptor<BrokerCharges> captor = ArgumentCaptor.forClass(BrokerCharges.class);

        // When
        brokerChargeService.addBrokerCharge(request);

        // Then
        verify(brokerChargesRepository).save(captor.capture());
        BrokerCharges savedEntity = captor.getValue();
        assertEquals(AmcChargeFrequency.QUARTERLY, savedEntity.getAmcChargeFrequency());
        assertEquals(request.getBrokerName(), savedEntity.getBrokerName());
        assertEquals(request.getStartDate(), savedEntity.getStartDate());
        assertEquals(request.getAmcChargesAnnually(), savedEntity.getAmcChargesAnnually());
    }

    private BrokerChargesRequest createBrokerChargesRequest() {
        BrokerChargesRequest request = new BrokerChargesRequest();
        request.setBrokerName(BrokerName.UPSTOX);
        request.setStartDate(LocalDate.of(2024, 1, 1));
        request.setStatus(EntityStatus.ACTIVE);
        request.setAccountOpeningCharges(100.0);
        request.setAmcChargesAnnually(500.0);
        request.setAmcChargesFrequency(AmcChargeFrequency.ANNUALLY);
        request.setBrokerageCharges(new BrokerageChargesDto(0.1, 20.0, BrokerageAggregatorType.MIN, 10.0, 50.0));
        request.setDpChargesPerScrip(10.0);
        request.setStt(0.05);
        request.setSebiCharges(0.001);
        request.setStampDuty(0.01);
        request.setGstApplicableDescription("18%-brokerage,18%-dp_charges");
        return request;
    }
}
