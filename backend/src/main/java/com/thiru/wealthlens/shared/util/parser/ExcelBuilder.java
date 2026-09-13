package com.thiru.wealthlens.shared.util.parser;

import com.thiru.wealthlens.portfolio.dto.AssetResponse;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import com.thiru.wealthlens.shared.util.transaction.ExcelHeaders;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@Log4j2
public class ExcelBuilder {

    private static final int DEFAULT_DECIMAL_PLACES = 2;

    public static ByteArrayInputStream downloadAssets(List<AssetResponse> userStocks, boolean isTermSpecific) {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            ExcelParser.initialiseExcelSheet(workbook, ExcelHeaders.getPortfolioHeaders(), ExcelParser.ASSETS);
            Sheet sheet0 = workbook.getSheetAt(0);
            Sheet sheet1 = null;
            if (isTermSpecific) {
                ExcelParser.initialiseExcelSheet(workbook, ExcelHeaders.getTransactionQuantityHeaders(), ExcelParser.TRANSACTIONS);
                sheet1 = workbook.getSheetAt(1);
            }

            int rowCount = 1;
            int transactionRowCount = 1;
            for (AssetResponse assetResponse : userStocks) {

                Row row = sheet0.createRow(rowCount);
                row.createCell(0).setCellValue(assetResponse.getEmail());
                row.createCell(1).setCellValue(assetResponse.getStockName());
                row.createCell(2).setCellValue(assetResponse.getStockCode());
                row.createCell(3).setCellValue(getRoundedValue(assetResponse.getQuantity()));
                row.createCell(4).setCellValue(getRoundedValue(assetResponse.getTotalQuantity()));
                row.createCell(5).setCellValue(getRoundedValue(assetResponse.getPrice()));
                row.createCell(6).setCellValue(getRoundedValue(assetResponse.getTotalValue()));
                row.createCell(7).setCellValue(assetResponse.getExchangeName());
                row.createCell(8).setCellValue(assetResponse.getBrokerName().name());
                row.createCell(9).setCellValue(assetResponse.getAssetType().name());
                setDateField(row.createCell(10), assetResponse.getMaturityDate());
                row.createCell(11).setCellValue(getRoundedValue(assetResponse.getBrokerCharges()));
                row.createCell(12).setCellValue(getRoundedValue(assetResponse.getMiscCharges()));
                row.createCell(13);

                if (isTermSpecific) {
                    transactionRowCount = updateTransactionSheet(sheet1, transactionRowCount, assetResponse);
                }
                rowCount++;
            }

            workbook.write(outputStream);
            return new ByteArrayInputStream(outputStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static int updateTransactionSheet(Sheet transactionsSheet, int rowCount, AssetResponse assetResponse) {

        Map<String, Double> transactionQuantities = assetResponse.getTransactionQuantities();
        for (Map.Entry<String, Double> entry : transactionQuantities.entrySet()) {

            Row row = transactionsSheet.createRow(rowCount);
            row.createCell(0).setCellValue(assetResponse.getStockCode());
            row.createCell(1).setCellValue(assetResponse.getBrokerName().name());
            row.createCell(2).setCellValue(entry.getKey());
            row.createCell(3).setCellValue(getRoundedValue(entry.getValue()));
            rowCount++;
        }
        return rowCount;
    }

    private static void setDateField(Cell cell, LocalDate date) {
        CellStyle dateStyle = cell.getSheet().getWorkbook().createCellStyle();
        CreationHelper createHelper = cell.getSheet().getWorkbook().getCreationHelper();
        dateStyle.setDataFormat(createHelper.createDataFormat().getFormat(TCollectionUtil.DATE_FORMAT));
        cell.setCellStyle(dateStyle);
        cell.setCellValue(date);
    }

    private static double getRoundedValue(double doubleValue) {

        double scale = Math.pow(10, DEFAULT_DECIMAL_PLACES);
        return Math.round(doubleValue * scale) / scale;
    }
}
