package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.helper.file.FileStream;
import com.thiru.wealthlens.portfolio.service.export.processor.AssetExcelWorkbookProcessor;
import com.thiru.wealthlens.portfolio.service.export.processor.TransactionExcelWorkbookProcessor;
import com.thiru.wealthlens.portfolio.service.export.processor.TransactionUploadTemplateProcessor;
import com.thiru.wealthlens.portfolio.service.export.processor.model.ExcelWorkbookProcessor;
import com.thiru.wealthlens.shared.dto.EntityExportRequest;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import lombok.AllArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class EntityExportService {

    private final Environment env;
    private final PortfolioService portfolioService;
    private final TransactionService transactionService;

    public FileStream export(UserMail userMail, EntityExportRequest exportRequest) {
        String entityName = exportRequest.getEntityName();
        return switch (entityName) {
            case "assets" -> portfolioExport(userMail, exportRequest);
            case "transactions" -> transactionExport(userMail, exportRequest);
            case "transactions-template" -> transactionTemplate(userMail, exportRequest);
            default -> throw new IllegalArgumentException("Invalid entity name: " + entityName);
        };
    }

    private FileStream portfolioExport(UserMail userMail, EntityExportRequest exportRequest) {
        ExcelWorkbookProcessor processor = new AssetExcelWorkbookProcessor(userMail, exportRequest, portfolioService, env);
        return processor.fileStream();
    }

    private FileStream transactionExport(UserMail userMail, EntityExportRequest exportRequest) {
        ExcelWorkbookProcessor processor = new TransactionExcelWorkbookProcessor(userMail, exportRequest, transactionService, env);
        return processor.fileStream();
    }

    private FileStream transactionTemplate(UserMail userMail, EntityExportRequest exportRequest) {
        ExcelWorkbookProcessor processor = new TransactionUploadTemplateProcessor(userMail, exportRequest.getSelectedColumns(), env);
        return processor.fileStream();
    }
}
