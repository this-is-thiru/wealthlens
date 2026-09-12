package com.thiru.wealthlens.brokercharges.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.entity.ChargeCatalogueEntity;
import com.thiru.wealthlens.brokercharges.repository.ChargeCatalogueRepository;
import com.thiru.wealthlens.testsupport.MoneyAssert;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ChargeDeductibilityServiceTest {

    @Mock
    private ChargeCatalogueRepository chargeCatalogueRepository;

    @InjectMocks
    private ChargeDeductibilityService chargeDeductibilityService;

    private void catalogue(Map<String, Boolean> declared) {
        when(chargeCatalogueRepository.findByCode(anyString())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            if (!declared.containsKey(code)) {
                return Optional.empty();
            }
            ChargeCatalogueEntity entry = new ChargeCatalogueEntity();
            entry.setCode(code);
            entry.setDeductibleForCapitalGains(declared.get(code));
            return Optional.of(entry);
        });
    }

    @Test
    @DisplayName("STT is excluded, and on a delivery sell it is the largest charge there is")
    void deductibleTotal_whenBreakdownContainsStt_excludesIt() {
        // Given -- roughly a real 1,00,000 delivery sell
        catalogue(Map.of("BROKERAGE", true, "STT", false, "GST", true));
        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("BROKERAGE", 20.0);
        breakdown.put("STT", 100.0);
        breakdown.put("GST", 3.6);

        // When
        double deductible = chargeDeductibilityService.deductibleTotal(breakdown);

        // Then -- 23.60 of 123.60, so treating the lot as deductible would overstate it fivefold
        MoneyAssert.assertMoney(23.6, deductible);
    }

    @Test
    @DisplayName("a code the catalogue does not carry is excluded rather than assumed deductible")
    void deductibleTotal_whenCodeIsUnknown_excludesIt() {
        // Given
        catalogue(Map.of("BROKERAGE", true));
        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("BROKERAGE", 20.0);
        breakdown.put("MYSTERY_LEVY", 500.0);

        // When
        double deductible = chargeDeductibilityService.deductibleTotal(breakdown);

        // Then
        MoneyAssert.assertMoney(20.0, deductible);
    }

    @Test
    @DisplayName("an empty or absent breakdown is zero, not a failure")
    void deductibleTotal_whenNothingToSum_isZero() {
        // Given / When / Then
        MoneyAssert.assertMoney(0.0, chargeDeductibilityService.deductibleTotal(null));
        MoneyAssert.assertMoney(0.0, chargeDeductibilityService.deductibleTotal(Map.of()));
    }
}
