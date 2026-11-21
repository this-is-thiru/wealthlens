package com.thiru.wealthlens.corporate.controller;
import com.thiru.wealthlens.corporate.dto.CorporateActionDto;
import com.thiru.wealthlens.corporate.dto.CorporateActionResponse;
import com.thiru.wealthlens.corporate.service.CorporateActionService;
import com.thiru.wealthlens.shared.web.MeApi;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/list")
    public ResponseEntity<List<CorporateActionDto>> getCorporateActions(@RequestParam List<String> ids) {

        List<CorporateActionDto> actions = corporateActionService.getCorporateActions(ids);
        return ResponseEntity.ok(actions);
    }

    @GetMapping("/all")
    public List<CorporateActionResponse> getAllCorporateActions() {
        return corporateActionService.getAllCorporateActions().stream().map(CorporateActionResponse::from).toList();
    }
}
