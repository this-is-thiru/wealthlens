package com.thiru.wealthlens.taxplanning.compute.controller;

import com.thiru.wealthlens.taxengine.api.RegimeComparison;
import com.thiru.wealthlens.taxplanning.compute.DraftComputationService;
import com.thiru.wealthlens.taxplanning.compute.DraftComputationView;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Computing a draft (TS T8). */
@RequiredArgsConstructor
@RequestMapping("/tax/user/{email}")
@RestController
public class DraftComputationController {

    private final DraftComputationService service;

    @PostMapping("/drafts/{draftId}/compute")
    public DraftComputationView compute(@PathVariable String email, @PathVariable String draftId,
            @RequestBody Map<String, String> body) {
        return service.compute(email, draftId, body.get("regime"));
    }

    @PostMapping("/drafts/{draftId}/compare-regimes")
    public RegimeComparison compare(@PathVariable String email, @PathVariable String draftId) {
        return service.compare(email, draftId);
    }

    @GetMapping("/computations/{receipt}")
    public DraftComputationView byReceipt(@PathVariable String email, @PathVariable String receipt) {
        return service.byReceipt(email, receipt);
    }
}
