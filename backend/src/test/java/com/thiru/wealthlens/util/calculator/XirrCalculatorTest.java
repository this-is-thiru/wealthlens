package com.thiru.wealthlens.util.calculator;

import static org.junit.jupiter.api.Assertions.*;

import com.thiru.wealthlens.portfolio.dto.analytics.CashFlow;
import com.thiru.wealthlens.shared.util.calculator.XirrCalculator;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class XirrCalculatorTest {

    @Test
    void shouldMatchExpectedXirrForKnownCashFlows() {
        // Known case: -1000 on 2023-01-01, +1200 on 2024-01-01
        // Expected XIRR ≈ 20%
        List<CashFlow> flows = List.of(
            new CashFlow(-1000.0, LocalDate.of(2023, 1, 1)),
            new CashFlow(1200.0, LocalDate.of(2024, 1, 1))
        );
        double xirr = XirrCalculator.calculate(flows);
        assertEquals(0.20, xirr, 0.01);
    }

    @Test
    void shouldHandleMultipleCashFlows() {
        // SIP-like scenario
        List<CashFlow> flows = List.of(
            new CashFlow(-1000.0, LocalDate.of(2023, 1, 1)),
            new CashFlow(-1000.0, LocalDate.of(2023, 2, 1)),
            new CashFlow(-1000.0, LocalDate.of(2023, 3, 1)),
            new CashFlow(3500.0, LocalDate.of(2023, 4, 1))
        );
        double xirr = XirrCalculator.calculate(flows);
        assertTrue(xirr > 0);
    }

    @Test
    void shouldThrowForSingleCashFlow() {
        List<CashFlow> flows = List.of(
            new CashFlow(-1000.0, LocalDate.of(2023, 1, 1))
        );
        assertThrows(IllegalArgumentException.class, () -> XirrCalculator.calculate(flows));
    }

    @Test
    void shouldThrowForAllSameSign() {
        List<CashFlow> flows = List.of(
            new CashFlow(-1000.0, LocalDate.of(2023, 1, 1)),
            new CashFlow(-500.0, LocalDate.of(2023, 2, 1))
        );
        assertThrows(IllegalArgumentException.class, () -> XirrCalculator.calculate(flows));
    }

    @Test
    void shouldHandlePositiveOnlyCashFlows() {
        List<CashFlow> flows = List.of(
            new CashFlow(1000.0, LocalDate.of(2023, 1, 1)),
            new CashFlow(500.0, LocalDate.of(2023, 2, 1))
        );
        assertThrows(IllegalArgumentException.class, () -> XirrCalculator.calculate(flows));
    }

    @Test
    void annualizedReturn_shouldReturnZeroForInvalidInputs() {
        assertEquals(0.0, XirrCalculator.annualizedReturn(1000.0, 0.0, 365.0));
        assertEquals(0.0, XirrCalculator.annualizedReturn(1000.0, -100.0, 365.0));
        assertEquals(0.0, XirrCalculator.annualizedReturn(1000.0, 1000.0, 0.0));
    }

    @Test
    void annualizedReturn_shouldCalculateCorrectly() {
        // 1000 invested, 1100 returned in 365 days = 10% return
        double result = XirrCalculator.annualizedReturn(1100.0, 1000.0, 365.0);
        assertEquals(0.10, result, 0.001);
    }
}
