package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.helper.file.FileStream;
import com.thiru.wealthlens.helper.file.FileType;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.service.export.PortfolioExcelExporter;
import com.thiru.wealthlens.shared.dto.EntityExportRequest;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.shared.util.time.TLocalDateTime;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Component;

/**
 * The selective downloads behind {@code POST /portfolio/user/{email}/stocks/download}.
 *
 * <p>This was three processor classes over an abstract base, each pairing a filename with an
 * entity fetch and a writer. With the writers gone the base class had nothing left to abstract, so
 * the three are the three branches below.
 */
@Component
@AllArgsConstructor
public class EntityExportService {

    private static final String ENV_UAT = "uat";
    private static final String ENV_PROD = "prod";
    private static final String FILE_NAME_DATE_FORMAT = "dd-MMMM-yyyy--hh-mm-ss-a";

    private final Environment env;
    private final PortfolioService portfolioService;
    private final TransactionService transactionService;

    public FileStream export(UserMail userMail, EntityExportRequest exportRequest) {
        String entityName = exportRequest.getEntityName();
        List<String> columns = exportRequest.getSelectedColumns();

        return switch (entityName) {
            case "assets" -> stream("portfolio-", PortfolioExcelExporter.assets(
                    portfolioService.getExportEntities(userMail, exportRequest.getQueryFilters()), columns, false));
            case "transactions" -> stream("transaction-", PortfolioExcelExporter.transactions(
                    transactionService.getUserTransactions(userMail), columns));
            // The template's name is fixed: it is a blank form, not a dated extract of anything, and
            // a timestamped one re-downloaded looks like a different document.
            case "transactions-template" -> FileStream.from("transactions-template" + FileType.XLSX.getExtension(),
                    new InputStreamResource(PortfolioExcelExporter.transactionTemplate(List.of(templateRow()), columns)),
                    FileType.XLSX);
            default -> throw new BadRequestException("Invalid entity name: " + entityName
                    + ". Expected one of assets, transactions, transactions-template");
        };
    }

    /**
     * A dated filename, prefixed outside production.
     *
     * <p>The prefix exists so a spreadsheet pulled from a test environment is identifiable as one
     * once it is sitting in somebody's downloads folder beside the real thing.
     */
    private FileStream stream(String namePrefix, ByteArrayInputStream workbook) {
        boolean isProdEnv = Set.of(env.getActiveProfiles()).contains(ENV_PROD);
        String fileName = (isProdEnv ? namePrefix : ENV_UAT + "-" + namePrefix)
                + TLocalDateTime.format(LocalDateTime.now(), FILE_NAME_DATE_FORMAT)
                + FileType.XLSX.getExtension();
        return FileStream.from(fileName, new InputStreamResource(workbook), FileType.XLSX);
    }

    /** One filled row, so the template shows the shape of every column rather than only its name. */
    private static TransactionEntity templateRow() {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setEmail("email@gmail.com");
        transaction.setStockCode("STOCK_CODE");
        transaction.setStockName("Stock Name");
        transaction.setExchangeName("NSE");
        transaction.setBrokerName(BrokerName.ZERODHA);
        transaction.setAssetType(AssetType.EQUITY);
        transaction.setMaturityDate(LocalDate.now());
        transaction.setTransactionType(TransactionType.BUY);
        transaction.setPrice(0.0);
        transaction.setQuantity(0.0);
        transaction.setBrokerCharges(0.0);
        transaction.setMiscCharges(0.0);
        transaction.setTransactionDate(LocalDate.now());
        transaction.setComment("comments");
        return transaction;
    }
}
