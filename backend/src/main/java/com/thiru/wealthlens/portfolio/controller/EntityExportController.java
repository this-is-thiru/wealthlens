package com.thiru.wealthlens.portfolio.controller;

import com.thiru.wealthlens.helper.file.FileHelper;
import com.thiru.wealthlens.helper.file.FileStream;
import com.thiru.wealthlens.portfolio.service.EntityExportService;
import com.thiru.wealthlens.shared.dto.EntityExportRequest;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import lombok.AllArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RequestMapping("/portfolio/user/{email}")
@RestController
public class EntityExportController {

    private final EntityExportService entityExportService;

    @PostMapping("/stocks/download")
    public ResponseEntity<InputStreamResource> downloadPortfolio(@PathVariable String email, @RequestBody EntityExportRequest entityExportRequest) {

        FileStream fileStream = entityExportService.export(UserMail.from(email), entityExportRequest);
        return FileHelper.sendFileAsAttachment(fileStream);
    }

}
