package com.thiru.wealthlens.taxplanning.controller;

import com.thiru.wealthlens.taxplanning.document.TaxComparisonReportGenerator;
import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceCatalogueEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.PerquisitePolicyEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.TaxSlabPolicyEntity;
import com.thiru.wealthlens.taxplanning.policy.service.PolicyService;
import com.thiru.wealthlens.taxplanning.recommendation.RestructuringResult;
import com.thiru.wealthlens.taxplanning.salary.dto.SalaryProfileRequest;
import com.thiru.wealthlens.taxplanning.salary.dto.SalaryProfileResponse;
import com.thiru.wealthlens.taxplanning.salary.entity.SalaryProfileEntity;
import com.thiru.wealthlens.taxplanning.salary.entity.TaxComputationEntity;
import com.thiru.wealthlens.taxplanning.salary.service.SalaryProfileService;
import com.thiru.wealthlens.taxplanning.service.TaxComputationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tax-planning")
@Log4j2
@RequiredArgsConstructor
public class TaxPlanningController {

    private final SalaryProfileService salaryProfileService;
    private final TaxComputationService taxComputationService;
    private final PolicyService policyService;
    private final TaxComparisonReportGenerator reportGenerator;
    private final com.thiru.wealthlens.taxplanning.recommendation.RestructuringEngine restructuringEngine;

    // ========== Salary Profile Management ==========

    @PostMapping("/user/{email}/profile")
    public ResponseEntity<SalaryProfileResponse> createProfile(@PathVariable String email, @RequestBody SalaryProfileRequest request) {
        return ResponseEntity.ok(salaryProfileService.createProfile(email, request));
    }

    @GetMapping("/user/{email}/profiles")
    public ResponseEntity<List<SalaryProfileResponse>> getProfiles(@PathVariable String email) {
        return ResponseEntity.ok(salaryProfileService.getProfiles(email));
    }

    @GetMapping("/user/{email}/profile/{profileId}")
    public ResponseEntity<SalaryProfileResponse> getProfile(@PathVariable String email, @PathVariable String profileId) {
        return ResponseEntity.ok(salaryProfileService.getProfile(email, profileId));
    }

    @PutMapping("/user/{email}/profile/{profileId}")
    public ResponseEntity<SalaryProfileResponse> updateProfile(@PathVariable String email, @PathVariable String profileId, @RequestBody SalaryProfileRequest request) {
        return ResponseEntity.ok(salaryProfileService.updateProfile(email, profileId, request));
    }

    @DeleteMapping("/user/{email}/profile/{profileId}")
    public ResponseEntity<Void> deleteProfile(@PathVariable String email, @PathVariable String profileId) {
        salaryProfileService.deleteProfile(email, profileId);
        return ResponseEntity.noContent().build();
    }

    // ========== Tax Computation ==========

    @PostMapping("/user/{email}/profile/{profileId}/compute")
    public ResponseEntity<TaxComputationEntity> computeTax(@PathVariable String email, @PathVariable String profileId) {
        return ResponseEntity.ok(taxComputationService.compute(email, profileId));
    }

    // ========== Restructuring ==========

    @PostMapping("/user/{email}/profile/{profileId}/restructure")
    public ResponseEntity<RestructuringResult> restructure(@PathVariable String email, @PathVariable String profileId) {
        SalaryProfileEntity profile = salaryProfileService.getProfileEntity(email, profileId);
        return ResponseEntity.ok(restructuringEngine.restructure(profile));
    }

    // ========== Document Generation ==========

    @GetMapping("/user/{email}/profile/{profileId}/document/tax-report")
    public ResponseEntity<byte[]> generateTaxReport(@PathVariable String email, @PathVariable String profileId) {
        TaxComputationEntity computation = taxComputationService.compute(email, profileId);
        SalaryProfileEntity profile = salaryProfileService.getProfileEntity(email, profileId);
        byte[] pdf = reportGenerator.generate(computation, profile, null);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tax-report.pdf").body(pdf);
    }

    // ========== Admin Endpoints (SUPER_USER only) ==========

    @PostMapping("/admin/policies/slab")
    @PreAuthorize("hasRole('SUPER_USER')")
    public ResponseEntity<TaxSlabPolicyEntity> createSlabPolicy(@RequestBody TaxSlabPolicyEntity policy) {
        return ResponseEntity.ok(policyService.createSlabPolicy(policy));
    }

    @PostMapping("/admin/policies/perquisite")
    @PreAuthorize("hasRole('SUPER_USER')")
    public ResponseEntity<PerquisitePolicyEntity> createPerquisitePolicy(@RequestBody PerquisitePolicyEntity policy) {
        return ResponseEntity.ok(policyService.createPerquisitePolicy(policy));
    }

    @PutMapping("/admin/allowances/{code}")
    @PreAuthorize("hasRole('SUPER_USER')")
    public ResponseEntity<AllowanceCatalogueEntity> updateAllowance(@PathVariable String code, @RequestParam String taxYear, @RequestBody AllowanceCatalogueEntity allowance) {
        return ResponseEntity.ok(policyService.updateAllowance(code, taxYear, allowance));
    }

    @GetMapping("/admin/policies")
    @PreAuthorize("hasRole('SUPER_USER')")
    public ResponseEntity<Map<String, Object>> getAllPolicies(@RequestParam String taxYear) {
        Map<String, Object> result = new HashMap<>();
        result.put("taxYear", taxYear);
        result.put("slabPolicies", policyService.getSlabPolicies(taxYear));
        result.put("perquisitePolicy", policyService.getPerquisitePolicy(taxYear));
        result.put("allowanceCatalogue", policyService.getAllowanceCatalogue(taxYear));
        return ResponseEntity.ok(result);
    }
}
