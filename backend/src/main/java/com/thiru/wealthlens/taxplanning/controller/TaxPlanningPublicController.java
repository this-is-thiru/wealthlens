package com.thiru.wealthlens.taxplanning.controller;

import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceCatalogueEntity;
import com.thiru.wealthlens.taxplanning.policy.service.PolicyService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tax-planning/public")
@Log4j2
@RequiredArgsConstructor
public class TaxPlanningPublicController {

    private final PolicyService policyService;

    @GetMapping("/allowances")
    public ResponseEntity<List<AllowanceCatalogueEntity>> getAllowances(
            @RequestParam String taxYear,
            @RequestParam(required = false) RegimeType regime) {
        List<AllowanceCatalogueEntity> result = regime != null
                ? policyService.getAllowanceCatalogue(taxYear, regime)
                : policyService.getAllowanceCatalogue(taxYear);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/allowances/{code}")
    public ResponseEntity<AllowanceCatalogueEntity> getAllowance(
            @PathVariable String code,
            @RequestParam String taxYear,
            @RequestParam(required = false) RegimeType regime) {
        List<AllowanceCatalogueEntity> catalogue = regime != null
                ? policyService.getAllowanceCatalogue(taxYear, regime)
                : policyService.getAllowanceCatalogue(taxYear);
        return catalogue.stream()
                .filter(a -> a.getCode().equalsIgnoreCase(code))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
