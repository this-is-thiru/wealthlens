package com.thiru.wealthlens.portfolio.service.export.processor;

import com.thiru.wealthlens.helper.file.FileType;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.service.export.processor.model.AbstractExcelWorkbookProcessor;
import com.thiru.wealthlens.portfolio.service.export.writer.TransactionUploadTemplateWriter;
import com.thiru.wealthlens.portfolio.service.export.writer.model.ExcelWorkbookWriter;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.time.LocalDate;
import java.util.List;
import org.springframework.core.env.Environment;

public class TransactionUploadTemplateProcessor extends AbstractExcelWorkbookProcessor<TransactionEntity> {

    private static final String ASSET_EXCEL_FILE_NAME = "transactions-template";
    private static final FileType FILE_TYPE = FileType.XLSX;

    private final List<String> columnFields;

    public TransactionUploadTemplateProcessor(UserMail userMail, List<String> columnFields, Environment env) {
        super(userMail, ASSET_EXCEL_FILE_NAME, FILE_TYPE, env);
        this.columnFields = columnFields;
    }

    @Override
    protected ExcelWorkbookWriter<TransactionEntity> workbookWriter() {
        return new TransactionUploadTemplateWriter("TRANSACTIONS", columnFields);
    }

    @Override
    protected List<TransactionEntity> entities() {
        TransactionEntity transactionEntity = new TransactionEntity();
        transactionEntity.setEmail("email@gmail.com");
        transactionEntity.setStockCode("STOCK_CODE");
        transactionEntity.setStockName("Stock Name");
        transactionEntity.setExchangeName("NSE");
        transactionEntity.setBrokerName(BrokerName.ZERODHA);
        transactionEntity.setAssetType(AssetType.EQUITY);
        transactionEntity.setMaturityDate(LocalDate.now());
        transactionEntity.setTransactionType(TransactionType.BUY);
        transactionEntity.setPrice(0.0);
        transactionEntity.setQuantity(0.0);
        transactionEntity.setBrokerCharges(0.0);
        transactionEntity.setMiscCharges(0.0);
        transactionEntity.setTransactionDate(LocalDate.now());
        transactionEntity.setComment("comments");
        return List.of(transactionEntity);
    }

    @Override
    protected String fileName() {
        return ASSET_EXCEL_FILE_NAME + FILE_TYPE.getExtension();
    }
}
