package com.thiru.wealthlens.portfolio.service.export.processor;

import com.thiru.wealthlens.helper.file.FileType;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.service.TransactionService;
import com.thiru.wealthlens.portfolio.service.export.processor.model.AbstractExcelWorkbookProcessor;
import com.thiru.wealthlens.portfolio.service.export.writer.TransactionExcelWorkbookWriter;
import com.thiru.wealthlens.portfolio.service.export.writer.model.ExcelWorkbookWriter;
import com.thiru.wealthlens.shared.dto.EntityExportRequest;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.util.List;
import org.springframework.core.env.Environment;

public class TransactionExcelWorkbookProcessor extends AbstractExcelWorkbookProcessor<TransactionEntity> {

    private static final String ASSET_EXCEL_FILE_NAME = "transaction-";
    private static final FileType FILE_TYPE = FileType.XLSX;

    private final TransactionService transactionService;
    private final List<String> columnFields;

    public TransactionExcelWorkbookProcessor(UserMail userMail, EntityExportRequest exportRequest, TransactionService transactionService, Environment env) {
        super(userMail, ASSET_EXCEL_FILE_NAME, FILE_TYPE, env);
        this.columnFields = exportRequest.getSelectedColumns();
        this.transactionService = transactionService;
    }

    @Override
    protected ExcelWorkbookWriter<TransactionEntity> workbookWriter() {
        return new TransactionExcelWorkbookWriter("TRANSACTIONS", columnFields);
    }

    @Override
    protected List<TransactionEntity> entities() {
        return transactionService.getUserTransactions(getUserMail());
    }
}
