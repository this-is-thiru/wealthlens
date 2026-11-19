package com.thiru.wealthlens.shared.controller;

import com.thiru.wealthlens.shared.privacy.GrievanceReceipt;
import com.thiru.wealthlens.shared.privacy.GrievanceService;
import com.thiru.wealthlens.shared.privacy.GrievanceStatement;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Grievance redressal (Digital Personal Data Protection Act, 2023 s.13; W-237b): a user raises and reads their own
 * grievances; a {@code SUPER_USER} reads the unanswered queue and answers.
 */
@RestController
@RequestMapping("/privacy/grievances")
@RequiredArgsConstructor
public class GrievanceController {

    private final GrievanceService service;

    @PostMapping("/user/{email}")
    @ResponseStatus(HttpStatus.CREATED)
    public GrievanceReceipt raise(@PathVariable String email, @RequestBody GrievanceStatement statement) {
        return service.raise(email, statement);
    }

    @GetMapping("/user/{email}")
    public List<GrievanceReceipt> raisedBy(@PathVariable String email) {
        return service.raisedBy(email);
    }

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
