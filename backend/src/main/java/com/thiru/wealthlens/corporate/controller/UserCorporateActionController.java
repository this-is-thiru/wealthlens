package com.thiru.wealthlens.corporate.controller;
import com.thiru.wealthlens.corporate.dto.CorporateActionPerformDto;
import com.thiru.wealthlens.corporate.service.CorporateActionService;
import com.thiru.wealthlens.shared.web.CallerEmail;
import com.thiru.wealthlens.shared.web.MeApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@MeApi
@RestController
public class UserCorporateActionController {

    private final CorporateActionService corporateActionService;

    @PutMapping("/perform")
    public CorporateActionPerformDto performCorporateActions(@CallerEmail String email, @Valid @RequestBody CorporateActionPerformDto actionPerformDto,
                                                             @RequestParam(value = "allBrokers", required = false, defaultValue = "false") boolean allBrokers) {
        corporateActionService.performPendingCorporateActions(email, actionPerformDto, allBrokers);
        return actionPerformDto;
    }
}
