package com.thiru.wealthlens.portfolio.service.export;

import com.thiru.wealthlens.portfolio.dto.AssetResponse;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.service.export.PortfolioExportColumns.TransactionQuantity;
import com.thiru.wealthlens.shared.util.parser.ExcelColumn;
import com.thiru.wealthlens.shared.util.parser.ExcelSheet;
import com.thiru.wealthlens.shared.util.parser.ExcelWorkbooks;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The portfolio's Excel downloads.
 *
 * <p>Every export the application produces now goes through here and out via {@link ExcelWorkbooks},
 * replacing the two mechanisms that used to do it separately — {@code ExcelBuilder} for the fixed
 * holding-type download and {@code export/writer/**} for the selective one.
 */
public final class PortfolioExcelExporter {

    private static final String ASSETS_SHEET = "ASSETS";
    private static final String TRANSACTIONS_SHEET = "TRANSACTIONS";

    private PortfolioExcelExporter() {
    }

    /**
     * The holdings, optionally with the per-date breakdown on a second sheet.
     *
     * @param selectedColumns the caller's chosen columns, or empty for all of them
     */
    public static ByteArrayInputStream assets(List<AssetResponse> assets, List<String> selectedColumns,
                                              boolean includeTransactionQuantities) {
        List<ExcelSheet<?>> sheets = new ArrayList<>();
        sheets.add(new ExcelSheet<>(ASSETS_SHEET,
                ExcelColumn.select(PortfolioExportColumns.ASSETS, selectedColumns), assets));

        if (includeTransactionQuantities) {
            sheets.add(new ExcelSheet<>(TRANSACTIONS_SHEET,
                    PortfolioExportColumns.TRANSACTION_QUANTITIES, transactionQuantities(assets)));
        }
        return ExcelWorkbooks.toStream(sheets);
    }

    public static ByteArrayInputStream transactions(List<TransactionEntity> transactions,
                                                    List<String> selectedColumns) {
        return ExcelWorkbooks.toStream(List.of(new ExcelSheet<>(TRANSACTIONS_SHEET,
                ExcelColumn.select(PortfolioExportColumns.TRANSACTIONS, selectedColumns), transactions)));
    }

    /** The upload template: the columns the parser reads, and one row showing the shape. */
    public static ByteArrayInputStream transactionTemplate(List<TransactionEntity> sampleRows,
                                                           List<String> selectedColumns) {
        return ExcelWorkbooks.toStream(List.of(new ExcelSheet<>(TRANSACTIONS_SHEET,
                ExcelColumn.select(PortfolioExportColumns.TRANSACTION_TEMPLATE, selectedColumns), sampleRows)));
    }

    /**
     * Flattens each holding's per-date quantities into rows.
     *
     * <p>A holding with no breakdown contributes none rather than failing the export: the map is
     * absent for any holding the portfolio view did not group, and losing the other hundred
     * holdings over one of them is the wrong trade.
     */
    private static List<TransactionQuantity> transactionQuantities(List<AssetResponse> assets) {
        List<TransactionQuantity> rows = new ArrayList<>();
        for (AssetResponse asset : assets) {
            Map<String, Double> quantities = asset.getTransactionQuantities();
            if (quantities == null) {
                continue;
            }
            quantities.forEach((date, quantity) -> rows.add(new TransactionQuantity(
                    asset.getStockCode(),
                    asset.getBrokerName() == null ? null : asset.getBrokerName().name(),
                    date, quantity)));
        }
        return rows;
    }
}
