package com.thiru.wealthlens.helper.controller;

import com.thiru.wealthlens.helper.file.FileHelper;
import com.thiru.wealthlens.helper.file.FileStream;
import com.thiru.wealthlens.portfolio.service.EntityExportService;
import com.thiru.wealthlens.shared.dto.EntityExportRequest;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.util.transaction.ExcelHeaders;
import com.thiru.wealthlens.shared.web.PublicApi;
import lombok.AllArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@PublicApi
@RestController
public class HelperController {

    private final EntityExportService entityExportService;

    @GetMapping
    public ResponseEntity<InputStreamResource> getTemplate() {

        EntityExportRequest entityExportRequest = new EntityExportRequest();
        entityExportRequest.setEntityName("transactions-template");
        FileStream fileStream = entityExportService.export(UserMail.from(null), entityExportRequest);

        return FileHelper.sendFileAsAttachment(fileStream);
    }

    @GetMapping("/fields")
    public ResponseEntity<String[]> getTemplateFields() {

        String[] templateFields = ExcelHeaders.getTransactionHeaders();
        return ResponseEntity.ok(templateFields);
    }
}
