package com.thiru.wealthlens.shared.controller;

import com.thiru.wealthlens.shared.privacy.BoardExtension;
import com.thiru.wealthlens.shared.privacy.BreachCase;
import com.thiru.wealthlens.shared.privacy.BreachEmails;
import com.thiru.wealthlens.shared.privacy.BreachNoticeRequest;
import com.thiru.wealthlens.shared.privacy.BreachNoticeService;
import com.thiru.wealthlens.shared.privacy.BreachReport;
import com.thiru.wealthlens.shared.privacy.UserBreachNotice;
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
 * Breach intimation (Digital Personal Data Protection Act, 2023 s.8(6); Rules r.7; W-237c): a {@code SUPER_USER} posts a
 * notice and records each step; an affected user reads their notices in their account.
 */
@RestController
@RequestMapping("/privacy/breach-notices")
@RequiredArgsConstructor
public class BreachNoticeController {

    private final BreachNoticeService service;

    @PostMapping
    @PreAuthorize("hasRole('SUPER_USER')")
    @ResponseStatus(HttpStatus.CREATED)
    public BreachCase post(@RequestBody BreachNoticeRequest request) {
        return service.post(request);
    }

    @PostMapping("/{noticeId}/board-intimated")
    @PreAuthorize("hasRole('SUPER_USER')")
    public BreachCase boardFirstIntimated(@PathVariable String noticeId) {
        return service.boardFirstIntimated(noticeId);
    }

    @PostMapping("/{noticeId}/board-report-sent")
    @PreAuthorize("hasRole('SUPER_USER')")
    public BreachCase boardReportSent(@PathVariable String noticeId) {
        return service.boardReportSent(noticeId);
    }

    @PostMapping("/{noticeId}/board-extension")
    @PreAuthorize("hasRole('SUPER_USER')")
    public BreachCase boardExtension(@PathVariable String noticeId, @RequestBody BoardExtension extension) {
        return service.boardExtension(noticeId, extension);
    }

    @PostMapping("/{noticeId}/emailed")
    @PreAuthorize("hasRole('SUPER_USER')")
    public BreachReport emailed(@PathVariable String noticeId, @RequestBody BreachEmails emailed) {
        return service.emailed(noticeId, emailed);
    }

    @GetMapping("/{noticeId}/report")
    @PreAuthorize("hasRole('SUPER_USER')")
    public BreachReport report(@PathVariable String noticeId) {
        return service.report(noticeId);
    }

    @GetMapping("/user/{email}")
    public List<UserBreachNotice> noticesFor(@PathVariable String email) {
        return service.noticesFor(email);
    }
}
