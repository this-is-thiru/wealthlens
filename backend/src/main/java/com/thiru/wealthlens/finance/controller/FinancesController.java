package com.thiru.wealthlens.finance.controller;

import com.thiru.wealthlens.finance.dto.FinanceRequest;
import com.thiru.wealthlens.finance.dto.FinanceResponse;
import com.thiru.wealthlens.finance.dto.InterestRateResponse;
import com.thiru.wealthlens.finance.dto.StepUpSIPRequest;
import com.thiru.wealthlens.finance.service.FinancesService;
import com.thiru.wealthlens.finance.service.StepUpSIPCalculator;
import com.thiru.wealthlens.shared.web.PublicApi;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@PublicApi
@RestController
@AllArgsConstructor
public class FinancesController {

    private final FinancesService financesService;
    private final StepUpSIPCalculator sipCalculator;

    @PostMapping("/calculate")
    public FinanceResponse calculate(@Valid @RequestBody FinanceRequest financeRequest) {
        return financesService.getFinanceResponse(financeRequest);
    }

    @PostMapping("/interest-rate")
    public InterestRateResponse calculateInterestRate(@Valid @RequestBody StepUpSIPRequest financeRequest) {
        return sipCalculator.calculateRate(financeRequest);
    }
}
