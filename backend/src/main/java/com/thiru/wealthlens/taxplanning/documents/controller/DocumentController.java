package com.thiru.wealthlens.taxplanning.documents.controller;

import com.thiru.wealthlens.taxplanning.documents.ConfirmationRequest;
import com.thiru.wealthlens.taxplanning.documents.DocumentService;
import com.thiru.wealthlens.taxplanning.documents.DocumentView;
import com.thiru.wealthlens.taxplanning.documents.ParseView;
import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentType;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Uploaded certificates (TS T8; W-131d1). {@code UserValidator}, asked by {@code AuthConfig}'s access rule, refuses a caller whose token is not the path's email ({@code DR 14} O2). */
@RequiredArgsConstructor
@RequestMapping("/tax/user/{email}/documents")
@RestController
public class DocumentController {

    private final DocumentService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentView upload(@PathVariable String email, @RequestParam("file") MultipartFile file, @RequestParam String profileId,
            @RequestParam int taxYear, @RequestParam DocumentType documentType) throws IOException {
        return service.upload(email, profileId, taxYear, documentType, file.getBytes());
    }

    @GetMapping("/{documentId}/parse")
    public ParseView parse(@PathVariable String email, @PathVariable String documentId) {
        return service.parse(email, documentId);
    }

    @PostMapping("/{documentId}/confirm")
    public ParseView confirm(@PathVariable String email, @PathVariable String documentId, @RequestBody ConfirmationRequest request) {
        return service.confirm(email, documentId, request);
    }
}
