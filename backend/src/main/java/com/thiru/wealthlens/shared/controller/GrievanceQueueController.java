package com.thiru.wealthlens.shared.controller;

import com.thiru.wealthlens.shared.privacy.GrievanceReceipt;
import com.thiru.wealthlens.shared.privacy.GrievanceService;
import com.thiru.wealthlens.shared.privacy.GrievanceStatement;
import com.thiru.wealthlens.shared.web.AdminApi;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The owner's queue of grievances and their answers (split from {@code GrievanceController}, D-855 C6a). */
@RequiredArgsConstructor
@AdminApi
@RequestMapping("/grievances")
@RestController
public class GrievanceQueueController {

    private final GrievanceService service;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_USER')")
    public List<GrievanceReceipt> unanswered() {
        return service.unanswered();
    }

    @PostMapping("/{grievanceId}/answer")
    @PreAuthorize("hasRole('SUPER_USER')")
    public GrievanceReceipt answer(@PathVariable String grievanceId, @RequestBody GrievanceStatement statement) {
        return service.answer(grievanceId, statement);
    }
}
