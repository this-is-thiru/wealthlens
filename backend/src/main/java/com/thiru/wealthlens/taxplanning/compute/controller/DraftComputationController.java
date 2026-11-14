package com.thiru.wealthlens.taxplanning.compute.controller;

import com.thiru.wealthlens.taxengine.api.ItrFormChoice;
import com.thiru.wealthlens.taxengine.api.RegimeComparison;
import com.thiru.wealthlens.taxplanning.compute.CapitalGainsView;
import com.thiru.wealthlens.taxplanning.compute.CapitalGainsViewService;
import com.thiru.wealthlens.taxplanning.compute.DraftCalendar;
import com.thiru.wealthlens.taxplanning.compute.DraftCalendarService;
import com.thiru.wealthlens.taxplanning.compute.DraftComputationService;
import com.thiru.wealthlens.taxplanning.compute.DraftComputationView;
import com.thiru.wealthlens.taxplanning.compute.DraftForecast;
import com.thiru.wealthlens.taxplanning.compute.DraftForecastService;
import com.thiru.wealthlens.taxplanning.compute.DraftOptimisation;
import com.thiru.wealthlens.taxplanning.compute.DraftOptimisationRequest;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Computing a draft (TS T8). */
@RequiredArgsConstructor
@RequestMapping("/tax/user/{email}")
@RestController
public class DraftComputationController {

    private final DraftComputationService service;
    private final CapitalGainsViewService capitalGainsViewService;
    private final DraftForecastService forecastService;
    private final DraftCalendarService calendarService;

    @PostMapping("/drafts/{draftId}/compute")
    public DraftComputationView compute(@PathVariable String email, @PathVariable String draftId,
            @RequestBody Map<String, String> body) {
        return service.compute(email, draftId, body.get("regime"));
    }

    @PostMapping("/drafts/{draftId}/compare-regimes")
    public RegimeComparison compare(@PathVariable String email, @PathVariable String draftId) {
        return service.compare(email, draftId);
    }

    @GetMapping("/drafts/{draftId}/forecast")
    public DraftForecast forecast(@PathVariable String email, @PathVariable String draftId) {
        return forecastService.forecast(email, draftId);
    }

    @GetMapping("/drafts/{draftId}/calendar")
    public DraftCalendar calendar(@PathVariable String email, @PathVariable String draftId) {
        return calendarService.calendar(email, draftId);
    }

    @PostMapping("/drafts/{draftId}/itr-form")
    public ItrFormChoice chooseForm(@PathVariable String email, @PathVariable String draftId,
            @RequestBody Map<String, String> body) {
        return service.chooseForm(email, draftId, body.get("regime"), body.get("returnKind"));
    }

    @PostMapping("/drafts/{draftId}/optimise")
    public DraftOptimisation optimise(@PathVariable String email, @PathVariable String draftId,
            @RequestBody DraftOptimisationRequest request) {
        return service.optimise(email, draftId, request);
    }

    @GetMapping("/profiles/{profileId}/capital-gains/{taxYear}")
    public CapitalGainsView capitalGains(@PathVariable String email, @PathVariable String profileId, @PathVariable int taxYear,
            @RequestParam Optional<LocalDate> asOf) {
        return capitalGainsViewService.view(email, profileId, taxYear, asOf);
    }

    @GetMapping("/computations/{receipt}")
    public DraftComputationView byReceipt(@PathVariable String email, @PathVariable String receipt) {
        return service.byReceipt(email, receipt);
    }
}
