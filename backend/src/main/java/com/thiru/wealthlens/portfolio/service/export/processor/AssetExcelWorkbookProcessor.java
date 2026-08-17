package com.thiru.wealthlens.portfolio.service.export.processor;

import com.thiru.wealthlens.helper.file.FileType;
import com.thiru.wealthlens.portfolio.dto.AssetResponse;
import com.thiru.wealthlens.portfolio.service.PortfolioService;
import com.thiru.wealthlens.portfolio.service.export.processor.model.AbstractExcelWorkbookProcessor;
import com.thiru.wealthlens.portfolio.service.export.writer.AssetExcelWorkbookWriter;
import com.thiru.wealthlens.portfolio.service.export.writer.model.ExcelWorkbookWriter;
import com.thiru.wealthlens.shared.dto.EntityExportRequest;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.entity.query.QueryFilter;
import java.util.List;
import org.springframework.core.env.Environment;

public class AssetExcelWorkbookProcessor extends AbstractExcelWorkbookProcessor<AssetResponse> {

    private static final String ASSET_EXCEL_FILE_NAME = "portfolio-";
    private static final FileType FILE_TYPE = FileType.XLSX;

    private final PortfolioService portfolioService;
    private final List<String> columnFields;
    private final List<QueryFilter> queryFilters;

    public AssetExcelWorkbookProcessor(UserMail userMail, EntityExportRequest exportRequest, PortfolioService portfolioService, Environment env) {
        super(userMail, ASSET_EXCEL_FILE_NAME, FILE_TYPE, env);
        this.columnFields = exportRequest.getSelectedColumns();
        this.queryFilters = exportRequest.getQueryFilters();
        this.portfolioService = portfolioService;
    }

    @Override
    protected ExcelWorkbookWriter<AssetResponse> workbookWriter() {
        return new AssetExcelWorkbookWriter("ASSETS", columnFields);
    }

    @Override
    protected List<AssetResponse> entities() {
        return portfolioService.getExportEntities(getUserMail() , queryFilters);
    }
}
