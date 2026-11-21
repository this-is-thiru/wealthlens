package com.thiru.wealthlens.shared.controller;

import com.thiru.wealthlens.shared.privacy.GrievanceReceipt;
import com.thiru.wealthlens.shared.privacy.GrievanceService;
import com.thiru.wealthlens.shared.privacy.GrievanceStatement;
import com.thiru.wealthlens.shared.web.CallerEmail;
import com.thiru.wealthlens.shared.web.MeApi;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Grievance redressal (Digital Personal Data Protection Act, 2023 s.13; W-237b): a user raises and reads their own
 * grievances; a {@code SUPER_USER} reads the unanswered queue and answers.
 */
@MeApi
@RequestMapping("/grievances")
@RestController
@RequiredArgsConstructor
public class GrievanceController {

    private final GrievanceService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GrievanceReceipt raise(@CallerEmail String email, @RequestBody GrievanceStatement statement) {
        return service.raise(email, statement);
    }

    @GetMapping
    public List<GrievanceReceipt> raisedBy(@CallerEmail String email) {
        return service.raisedBy(email);
    }
}
