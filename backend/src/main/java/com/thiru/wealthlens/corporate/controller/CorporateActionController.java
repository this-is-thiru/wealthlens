package com.thiru.wealthlens.corporate.controller;
import com.thiru.wealthlens.corporate.dto.CorporateActionResponse;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.corporate.service.CorporateActionService;
import com.thiru.wealthlens.shared.web.MeApi;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@MeApi
@RestController
public class CorporateActionController {

    private final CorporateActionService corporateActionService;

    @GetMapping("/{id}")
    public CorporateActionResponse getCorporateAction(@PathVariable String id) {
        return CorporateActionResponse.from(corporateActionService.getCorporateActionDetails(id));
    }

    /** Every corporate action, or only those whose ids are given (W-358c3a: was {@code /all} and {@code /list}). */
    @GetMapping
    public List<CorporateActionResponse> corporateActions(@RequestParam Optional<List<String>> ids) {
        List<CorporateActionEntity> actions = ids.map(corporateActionService::getCorporateActions)
                .orElseGet(corporateActionService::getAllCorporateActions);
        return actions.stream().map(CorporateActionResponse::from).toList();
    }
}
