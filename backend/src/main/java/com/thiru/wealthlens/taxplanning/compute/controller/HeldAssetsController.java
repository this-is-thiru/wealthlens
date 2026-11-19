package com.thiru.wealthlens.taxplanning.compute.controller;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxplanning.compute.HeldAssetsView;
import com.thiru.wealthlens.taxplanning.compute.HeldAssetsViewService;
import com.thiru.wealthlens.taxplanning.compute.HeldAssetsWorkbook;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The held lots by term, as JSON and as a workbook (W-262). */
@RequiredArgsConstructor
@RequestMapping("/tax/user/{email}/profiles/{profileId}/held-assets")
@RestController
public class HeldAssetsController {

    private static final MediaType WORKBOOK = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final HeldAssetsViewService service;

    @GetMapping
    public HeldAssetsView heldAssets(@PathVariable String email, @PathVariable String profileId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Optional<LocalDate> asOf,
            @RequestParam Optional<CapitalGainsInput.Term> term) {
        return service.view(email, profileId, asOf, term);
    }

    @GetMapping("/excel")
    public ResponseEntity<InputStreamResource> heldAssetsWorkbook(@PathVariable String email, @PathVariable String profileId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Optional<LocalDate> asOf,
            @RequestParam Optional<CapitalGainsInput.Term> term) {
        HeldAssetsView view = service.view(email, profileId, asOf, term);
        return ResponseEntity.ok().contentType(WORKBOOK)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=held-assets-" + view.asOf() + ".xlsx")
                .body(new InputStreamResource(HeldAssetsWorkbook.of(view)));
    }
}
