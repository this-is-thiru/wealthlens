package com.thiru.wealthlens.taxplanning.workspace.controller;

import com.thiru.wealthlens.shared.web.CallerEmail;
import com.thiru.wealthlens.shared.web.MeApi;
import com.thiru.wealthlens.taxplanning.workspace.DraftConflictException;
import com.thiru.wealthlens.taxplanning.workspace.importing.SalaryImportDecisions;
import com.thiru.wealthlens.taxplanning.workspace.importing.SalaryImportPreview;
import com.thiru.wealthlens.taxplanning.workspace.importing.SalaryImportService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** A salary workbook previewed against a draft (F28; W-133a), then applied as one employment with {@code If-Match} (W-133b). */
@RequiredArgsConstructor
@MeApi
@RequestMapping("/drafts/{draftId}/salary-import")
@RestController
public class SalaryImportController {

    private final SalaryImportService salaryImports;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SalaryImportPreview preview(@CallerEmail String email, @PathVariable String draftId, @RequestParam("file") MultipartFile file)
            throws IOException {
        return salaryImports.preview(email, draftId, file.getBytes());
    }

    @PostMapping(path = "/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> apply(@CallerEmail String email, @PathVariable String draftId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch, @RequestPart("file") MultipartFile file,
            @RequestPart("decisions") SalaryImportDecisions decisions) throws IOException {
        try {
            return DraftController.tagged(salaryImports.apply(email, draftId, DraftController.revisionIn(ifMatch), file.getBytes(), decisions));
        } catch (DraftConflictException e) {
            return DraftController.conflict(e.conflict());
        }
    }
}
