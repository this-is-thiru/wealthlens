package com.thiru.wealthlens.corporate.controller;
import com.thiru.wealthlens.corporate.dto.CorporateActionPerformDto;
import com.thiru.wealthlens.corporate.service.CorporateActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/corporate-action/user/{email}")
@RestController
public class UserCorporateActionController {

    private final CorporateActionService corporateActionService;

    @PutMapping("/perform")
    public CorporateActionPerformDto performCorporateActions(@PathVariable String email, @RequestBody CorporateActionPerformDto actionPerformDto,
                                                             @RequestParam(value = "allBrokers", required = false, defaultValue = "false") boolean allBrokers) {
        corporateActionService.performPendingCorporateActions(email, actionPerformDto, allBrokers);
        return actionPerformDto;
    }
}
