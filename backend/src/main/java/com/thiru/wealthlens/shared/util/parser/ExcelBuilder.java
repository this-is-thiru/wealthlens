package com.thiru.wealthlens.shared.util.parser;

import com.thiru.wealthlens.portfolio.dto.AssetResponse;
import com.thiru.wealthlens.portfolio.dto.charges.AssetCharges;
import com.thiru.wealthlens.portfolio.dto.charges.ChargeNote;
import com.thiru.wealthlens.shared.util.transaction.ExcelHeaders;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@Log4j2
public class ExcelBuilder {

    /**
     * The portfolio sheet, column by column, in order.
     *
     * <p>This list <b>is</b> the published format. A user builds formulas against these columns, so
     * changing it changes someone else's spreadsheet — which is why it is written down in one place
     * and asserted by {@code ExcelBuilderTest} rather than emerging from whatever fields a DTO
     * happens to have.
     *
     * <p>The computed charges sit beside the user-entered ones rather than replacing them. They are
     * different figures — one is what the broker was believed to have charged, the other what the
     * engine worked out — and Phase B exists precisely so the two can be compared.
     */
    private static final List<ExcelColumn<AssetResponse>> PORTFOLIO_COLUMNS = List.of(
            ExcelColumn.text(ExcelHeaders.EMAIL, AssetResponse::getEmail),
            ExcelColumn.text(ExcelHeaders.STOCK_NAME, AssetResponse::getStockName),
            ExcelColumn.text(ExcelHeaders.STOCK_CODE, AssetResponse::getStockCode),
            ExcelColumn.quantity(ExcelHeaders.QUANTITY, AssetResponse::getQuantity),
            ExcelColumn.quantity(ExcelHeaders.TOTAL_QUANTITY, AssetResponse::getTotalQuantity),
            ExcelColumn.money(ExcelHeaders.PRICE, AssetResponse::getPrice),
            ExcelColumn.money(ExcelHeaders.TOTAL_VALUE, AssetResponse::getTotalValue),
            ExcelColumn.text(ExcelHeaders.EXCHANGE_NAME, AssetResponse::getExchangeName),
            ExcelColumn.text(ExcelHeaders.BROKER_NAME, asset -> asset.getBrokerName().name()),
            ExcelColumn.text(ExcelHeaders.ASSET_TYPE, asset -> asset.getAssetType().name()),
            ExcelColumn.date(ExcelHeaders.MATURITY_DATE, AssetResponse::getMaturityDate),
            ExcelColumn.money(ExcelHeaders.BROKER_CHARGES, AssetResponse::getBrokerCharges),
            ExcelColumn.money(ExcelHeaders.MISC_CHARGES, AssetResponse::getMiscCharges),
            ExcelColumn.money(ExcelHeaders.COMPUTED_BUY_CHARGES, side(AssetCharges::getBuy)),
            ExcelColumn.money(ExcelHeaders.COMPUTED_SELL_CHARGES, side(AssetCharges::getSell)),
            ExcelColumn.money(ExcelHeaders.TOTAL_COMPUTED_CHARGES,
                    asset -> asset.getCharges() == null ? 0.0 : asset.getCharges().getTotal()));

    /** The half-month sheet: one row per transaction date behind a holding. */
    private static final List<ExcelColumn<TransactionQuantity>> TRANSACTION_QUANTITY_COLUMNS = List.of(
            ExcelColumn.text(ExcelHeaders.STOCK_CODE, TransactionQuantity::stockCode),
            ExcelColumn.text(ExcelHeaders.BROKER_NAME, TransactionQuantity::brokerName),
            ExcelColumn.text(ExcelHeaders.TRANSACTION_DATE, TransactionQuantity::transactionDate),
            ExcelColumn.quantity(ExcelHeaders.QUANTITY, TransactionQuantity::quantity));

    /** One holding's quantity on one transaction date, flattened for the second sheet. */
    private record TransactionQuantity(String stockCode, String brokerName, String transactionDate,
                                       Double quantity) {
    }

    public static ByteArrayInputStream downloadAssets(List<AssetResponse> userStocks, boolean isTermSpecific) {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            ExcelColumn.write(workbook, ExcelParser.ASSETS, PORTFOLIO_COLUMNS, userStocks);
            if (isTermSpecific) {
                ExcelColumn.write(workbook, ExcelParser.TRANSACTIONS, TRANSACTION_QUANTITY_COLUMNS,
                        transactionQuantities(userStocks));
            }

            workbook.write(outputStream);
            return new ByteArrayInputStream(outputStream.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Could not write the portfolio export workbook", e);
        }
    }

    private static List<TransactionQuantity> transactionQuantities(List<AssetResponse> userStocks) {
        List<TransactionQuantity> rows = new ArrayList<>();
        for (AssetResponse asset : userStocks) {
            Map<String, Double> quantities = asset.getTransactionQuantities();
            if (quantities == null) {
                continue;
            }
            quantities.forEach((date, quantity) -> rows.add(new TransactionQuantity(
                    asset.getStockCode(), asset.getBrokerName().name(), date, quantity)));
        }
        return rows;
    }

    /**
     * One side of a holding's computed charges, as a figure rather than an absence.
     *
     * <p>The response distinguishes "never priced" from "charged nothing" by leaving the note null,
     * which matters to an API caller deciding what to display. A spreadsheet column cannot carry
     * that distinction, and a blank cell in a money column reads as data loss, so both become zero
     * here.
     */
    private static Function<AssetResponse, Double> side(Function<AssetCharges, ChargeNote> sideOf) {
        return asset -> {
            AssetCharges charges = asset.getCharges();
            if (charges == null) {
                return 0.0;
            }
            ChargeNote note = sideOf.apply(charges);
            return note == null ? 0.0 : note.getTotal();
        };
    }
}
