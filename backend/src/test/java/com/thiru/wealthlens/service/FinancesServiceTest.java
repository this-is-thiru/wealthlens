package com.thiru.wealthlens.service;

import static org.junit.jupiter.api.Assertions.*;

import com.thiru.wealthlens.finance.dto.FinanceRequest;
import com.thiru.wealthlens.finance.dto.FinanceResponse;
import com.thiru.wealthlens.finance.dto.enums.CalculationType;
import com.thiru.wealthlens.finance.service.FinancesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinancesServiceTest {

    @InjectMocks
    private FinancesService financesService;

    @Test
    void calculateEmi_when1Lac12Percent12Mo_returns8885() {
        // Given
        FinanceRequest request = new FinanceRequest(
                CalculationType.EMI, 100000, 12, 12, 0, 0);

        // When
        FinanceResponse response = financesService.getFinanceResponse(request);

        // Then
        assertEquals(8885, response.getEmiAmount());
    }

    @Test
    void calculateEmi_whenZeroInterest_returnsCorrect() {
        // Given
        FinanceRequest request = new FinanceRequest(
                CalculationType.EMI, 100000, 0, 12, 0, 0);

        // When
        FinanceResponse response = financesService.getFinanceResponse(request);

        // Then
        assertEquals(8334, response.getEmiAmount());
    }

    @Test
    void calculateEmi_whenNegativePrincipal_throws() {
        // Given
        FinanceRequest request = new FinanceRequest(
                CalculationType.EMI, -100000, 12, 12, 0, 0);

        // When / Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> financesService.getFinanceResponse(request));
        assertEquals("Principal amount must be non-negative", exception.getMessage());
    }

    @Test
    void calculateTotalAmount_when1Lac12Percent12Mo_returnsCorrect() {
        // Given
        FinanceRequest request = new FinanceRequest(
                CalculationType.TOTAL_AMOUNT, 100000, 12, 12, 0, 0);

        // When
        FinanceResponse response = financesService.getFinanceResponse(request);

        // Then
        assertEquals(106618.55, response.getFinalAmount(), 0.01);
    }

    @Test
    void calculateTotalAmount_whenZeroInterest_returnsPrincipal() {
        // Given
        FinanceRequest request = new FinanceRequest(
                CalculationType.TOTAL_AMOUNT, 100000, 0, 12, 0, 0);

        // When
        FinanceResponse response = financesService.getFinanceResponse(request);

        // Then
        assertEquals(100000, response.getFinalAmount(), 0.01);
    }

    @Test
    void calculatePrincipal_whenEmi8885Rate12Time12_returns100000() {
        // Given
        FinanceRequest request = new FinanceRequest(
                CalculationType.PRINCIPAL_AMOUNT, 0, 12, 12, 0, 8885);

        // When
        FinanceResponse response = financesService.getFinanceResponse(request);

        // Then
        // EMI=8885 is ceil of exact EMI=8884.88, so principal is slightly off: ~100001.36
        assertEquals(100000.0, response.getPrincipalAmount(), 2.0);
    }

    @Test
    void calculatePrincipal_whenZeroInterest_returnsCorrect() {
        // Given
        FinanceRequest request = new FinanceRequest(
                CalculationType.PRINCIPAL_AMOUNT, 0, 0, 12, 0, 8334);

        // When
        FinanceResponse response = financesService.getFinanceResponse(request);

        // Then
        assertEquals(100008.0, response.getPrincipalAmount(), 1.0);
    }

    @Test
    void calculateTimePeriod_whenP1LacEmi8885Rate12_returns12() {
        // Given
        FinanceRequest request = new FinanceRequest(
                CalculationType.TIME_PERIOD, 100000, 12, 0, 0, 8885);

        // When
        FinanceResponse response = financesService.getFinanceResponse(request);

        // Then
        assertEquals(12, response.getTimePeriod());
    }

    @Test
    void calculateTimePeriod_whenZeroInterest_returnsCorrect() {
        // Given
        FinanceRequest request = new FinanceRequest(
                CalculationType.TIME_PERIOD, 100000, 0, 0, 0, 8334);

        // When
        FinanceResponse response = financesService.getFinanceResponse(request);

        // Then
        assertEquals(12, response.getTimePeriod());
    }

    @Test
    void calculateTimePeriod_whenEmiTooLow_throws() {
        // Given
        FinanceRequest request = new FinanceRequest(
                CalculationType.TIME_PERIOD, 100000, 12, 0, 0, 900);

        // When / Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> financesService.getFinanceResponse(request));
        assertEquals("EMI must be greater than principal times monthly rate", exception.getMessage());
    }

    @Test
    void calculate_whenNone_throws() {
        // Given
        FinanceRequest request = new FinanceRequest(
                CalculationType.NONE, 0, 0, 0, 0, 0);

        // When / Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> financesService.getFinanceResponse(request));
        assertEquals("Invalid calculation type", exception.getMessage());
    }

    @Test
    void calculate_whenRateOfInterest_returnsPassthrough() {
        // Given
        FinanceRequest request = new FinanceRequest(
                CalculationType.RATE_OF_INTEREST, 0, 15.5, 0, 0, 0);

        // When
        FinanceResponse response = financesService.getFinanceResponse(request);

        // Then
        assertEquals(15.5, response.getRateOfInterest());
    }
}
