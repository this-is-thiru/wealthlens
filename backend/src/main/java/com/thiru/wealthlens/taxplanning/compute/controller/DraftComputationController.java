package com.thiru.wealthlens.taxplanning.compute.controller;

import com.thiru.wealthlens.shared.web.CallerEmail;
import com.thiru.wealthlens.shared.web.MeApi;
import com.thiru.wealthlens.taxengine.api.ItrFormChoice;
import com.thiru.wealthlens.taxplanning.compute.CapitalGainsView;
import com.thiru.wealthlens.taxplanning.compute.CapitalGainsViewService;
import com.thiru.wealthlens.taxplanning.compute.ComputationListing;
import com.thiru.wealthlens.taxplanning.compute.ComputationReportService;
import com.thiru.wealthlens.taxplanning.compute.DraftCalendar;
import com.thiru.wealthlens.taxplanning.compute.DraftCalendarService;
import com.thiru.wealthlens.taxplanning.compute.DraftClaimIntegrity;
import com.thiru.wealthlens.taxplanning.compute.DraftClaimIntegrityService;
import com.thiru.wealthlens.taxplanning.compute.DraftClaims;
import com.thiru.wealthlens.taxplanning.compute.DraftClaimsService;
import com.thiru.wealthlens.taxplanning.compute.DraftCompleteness;
import com.thiru.wealthlens.taxplanning.compute.DraftCompletenessService;
import com.thiru.wealthlens.taxplanning.compute.DraftComputationService;
import com.thiru.wealthlens.taxplanning.compute.DraftComputationView;
import com.thiru.wealthlens.taxplanning.compute.DraftDocumentChecklist;
import com.thiru.wealthlens.taxplanning.compute.DraftDocumentChecklistService;
import com.thiru.wealthlens.taxplanning.compute.DraftFirstFigure;
import com.thiru.wealthlens.taxplanning.compute.DraftFirstFigureRequest;
import com.thiru.wealthlens.taxplanning.compute.DraftFirstFigureService;
import com.thiru.wealthlens.taxplanning.compute.DraftForecast;
import com.thiru.wealthlens.taxplanning.compute.DraftForecastService;
import com.thiru.wealthlens.taxplanning.compute.DraftOnboarding;
import com.thiru.wealthlens.taxplanning.compute.DraftOnboardingService;
import com.thiru.wealthlens.taxplanning.compute.DraftOptimisation;
import com.thiru.wealthlens.taxplanning.compute.DraftOptimisationRequest;
import com.thiru.wealthlens.taxplanning.compute.DraftReconciliation;
import com.thiru.wealthlens.taxplanning.compute.DraftReconciliationService;
import com.thiru.wealthlens.taxplanning.compute.DraftRegimeComparison;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Computing a draft (TS T8). */
@RequiredArgsConstructor
@MeApi
@RestController
public class DraftComputationController {

    private final DraftComputationService service;
    private final CapitalGainsViewService capitalGainsViewService;
    private final DraftForecastService forecastService;
    private final DraftReconciliationService reconciliationService;
    private final DraftCalendarService calendarService;
    private final DraftCompletenessService completenessService;
    private final DraftClaimsService claimsService;
    private final DraftClaimIntegrityService claimIntegrityService;
    private final DraftDocumentChecklistService documentChecklistService;
    private final DraftFirstFigureService firstFigureService;
    private final DraftOnboardingService onboardingService;
    private final ComputationReportService reportService;

    @PostMapping("/drafts/{draftId}/compute")
    public DraftComputationView compute(@CallerEmail String email, @PathVariable String draftId,
            @RequestBody Map<String, String> body) {
        return service.compute(email, draftId, body.get("regime"));
    }

    @PostMapping("/profiles/{profileId}/workspaces/{taxYear}/first-figure")
    public DraftFirstFigure firstFigure(@CallerEmail String email, @PathVariable String profileId,
            @PathVariable int taxYear, @RequestBody DraftFirstFigureRequest request) {
        return firstFigureService.firstFigure(email, profileId, taxYear, request);
    }

    @PostMapping("/drafts/{draftId}/compare-regimes")
    public DraftRegimeComparison compare(@CallerEmail String email, @PathVariable String draftId) {
        return service.compare(email, draftId);
    }

    @GetMapping("/drafts/{draftId}/forecast")
    public DraftForecast forecast(@CallerEmail String email, @PathVariable String draftId) {
        return forecastService.forecast(email, draftId);
    }

    @GetMapping("/drafts/{draftId}/reconciliation")
    public DraftReconciliation reconciliation(@CallerEmail String email, @PathVariable String draftId) {
        return reconciliationService.reconcile(email, draftId);
    }

    @GetMapping("/drafts/{draftId}/calendar")
    public DraftCalendar calendar(@CallerEmail String email, @PathVariable String draftId) {
        return calendarService.calendar(email, draftId);
    }

    @GetMapping("/drafts/{draftId}/completeness")
    public DraftCompleteness completeness(@CallerEmail String email, @PathVariable String draftId) {
        return completenessService.completeness(email, draftId);
    }

    @GetMapping("/drafts/{draftId}/onboarding")
    public DraftOnboarding onboarding(@CallerEmail String email, @PathVariable String draftId) {
        return onboardingService.onboarding(email, draftId);
    }

    @GetMapping("/drafts/{draftId}/claims")
    public DraftClaims claims(@CallerEmail String email, @PathVariable String draftId) {
        return claimsService.claims(email, draftId);
    }

    @GetMapping("/drafts/{draftId}/claim-integrity")
    public DraftClaimIntegrity claimIntegrity(@CallerEmail String email, @PathVariable String draftId) {
        return claimIntegrityService.claimIntegrity(email, draftId);
    }

    @GetMapping("/drafts/{draftId}/document-checklist")
    public DraftDocumentChecklist documentChecklist(@CallerEmail String email, @PathVariable String draftId) {
        return documentChecklistService.checklist(email, draftId);
    }

    @PostMapping("/drafts/{draftId}/itr-form")
    public ItrFormChoice chooseForm(@CallerEmail String email, @PathVariable String draftId,
            @RequestBody Map<String, String> body) {
        return service.chooseForm(email, draftId, body.get("regime"), body.get("returnKind"));
    }

    @PostMapping("/drafts/{draftId}/optimise")
    public DraftOptimisation optimise(@CallerEmail String email, @PathVariable String draftId,
            @RequestBody DraftOptimisationRequest request) {
        return service.optimise(email, draftId, request);
    }

    @GetMapping("/profiles/{profileId}/capital-gains/{taxYear}")
    public CapitalGainsView capitalGains(@CallerEmail String email, @PathVariable String profileId, @PathVariable int taxYear,
            @RequestParam Optional<LocalDate> asOf) {
        return capitalGainsViewService.view(email, profileId, taxYear, asOf);
    }

    @GetMapping("/computations/{receipt}")
    public DraftComputationView byReceipt(@CallerEmail String email, @PathVariable String receipt) {
        return service.byReceipt(email, receipt);
    }

    /** Every computation of a profile, newest first: the receipts to take each report by before erasure (W-123b C1a). */
    @GetMapping("/profiles/{profileId}/computations")
    public List<ComputationListing> computations(@CallerEmail String email, @PathVariable String profileId) {
        return service.computations(email, profileId);
    }

    /** The same receipt as a PDF, made on request and kept nowhere (W-123a C4a). */
    @GetMapping("/computations/{receipt}/report")
    public ResponseEntity<byte[]> report(@CallerEmail String email, @PathVariable String receipt) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + receipt + ".pdf\"")
                .body(reportService.report(email, receipt));
    }
}
