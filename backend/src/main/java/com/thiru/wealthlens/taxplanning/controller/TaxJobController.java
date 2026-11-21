package com.thiru.wealthlens.taxplanning.controller;

import com.thiru.wealthlens.shared.web.AdminApi;
import com.thiru.wealthlens.taxplanning.jobs.JobRunReceipt;
import com.thiru.wealthlens.taxplanning.jobs.TaxJobDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Runs one tax job on request — the owner's, so {@code SUPER_USER} only (W-336): while the app is local, nothing is scheduled. */
@AdminApi
@RestController
@RequiredArgsConstructor
public class TaxJobController {

    private final TaxJobDirectory jobs;

    @PostMapping("/jobs/{name}/run")
    @PreAuthorize("hasRole('SUPER_USER')")
    public ResponseEntity<JobRunReceipt> run(@PathVariable String name) {
        return ResponseEntity.ok(jobs.run(name));
    }
}
