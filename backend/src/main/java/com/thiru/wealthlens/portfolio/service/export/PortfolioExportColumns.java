package com.thiru.wealthlens.portfolio.service.export;

import com.thiru.wealthlens.portfolio.dto.AssetResponse;
import com.thiru.wealthlens.portfolio.dto.charges.AssetCharges;
import com.thiru.wealthlens.portfolio.dto.charges.ChargeNote;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.shared.util.parser.ExcelColumn;
import com.thiru.wealthlens.shared.util.transaction.ExcelHeaders;
import java.util.List;
import java.util.function.Function;

/**
 * Every exported column, declared once.
 *
 * <p>Two endpoints download a portfolio — the fixed holding-type export and the selective one — and
 * they used to run on separate frameworks with separate column lists. The computed charge columns
 * reached one of them, so two downloads of the same holdings disagreed about what data a holding
 * has. One list means that cannot recur.
 *
 * <p><b>The {@code field} names are API.</b> They are what a caller puts in
 * {@code EntityExportRequest.selectedColumns}, so they are kept exactly as the superseded writers
 * spelled them. The headings beside them can be reworded; these cannot, without breaking callers.
 */
public final class PortfolioExportColumns {

    private PortfolioExportColumns() {
    }

    /**
     * A holding, as both portfolio downloads present it.
     *
     * <p>The computed charges sit beside the user-entered ones rather than replacing them. They are
     * different figures — one is what the broker was believed to have charged, the other what the
     * engine worked out — and Phase B exists so the two can be compared.
     */
    public static final List<ExcelColumn<AssetResponse>> ASSETS = List.of(
            ExcelColumn.text("email", ExcelHeaders.EMAIL, AssetResponse::getEmail),
            ExcelColumn.text("stockName", ExcelHeaders.STOCK_NAME, AssetResponse::getStockName),
            ExcelColumn.text("stockCode", ExcelHeaders.STOCK_CODE, AssetResponse::getStockCode),
            ExcelColumn.quantity("quantity", ExcelHeaders.QUANTITY, AssetResponse::getQuantity),
            ExcelColumn.quantity("totalQuantity", ExcelHeaders.TOTAL_QUANTITY, AssetResponse::getTotalQuantity),
            ExcelColumn.money("price", ExcelHeaders.PRICE, AssetResponse::getPrice),
            ExcelColumn.money("totalValue", ExcelHeaders.TOTAL_VALUE, AssetResponse::getTotalValue),
            ExcelColumn.text("exchangeName", ExcelHeaders.EXCHANGE_NAME, AssetResponse::getExchangeName),
            ExcelColumn.text("brokerName", ExcelHeaders.BROKER_NAME, asset -> name(asset.getBrokerName())),
            ExcelColumn.text("assetType", ExcelHeaders.ASSET_TYPE, asset -> name(asset.getAssetType())),
            ExcelColumn.date("maturityDate", ExcelHeaders.MATURITY_DATE, AssetResponse::getMaturityDate),
            ExcelColumn.money("brokerCharges", ExcelHeaders.BROKER_CHARGES, AssetResponse::getBrokerCharges),
            ExcelColumn.money("miscCharges", ExcelHeaders.MISC_CHARGES, AssetResponse::getMiscCharges),
            ExcelColumn.money("computedBuyCharges", ExcelHeaders.COMPUTED_BUY_CHARGES, side(AssetCharges::getBuy)),
            ExcelColumn.money("computedSellCharges", ExcelHeaders.COMPUTED_SELL_CHARGES, side(AssetCharges::getSell)),
            ExcelColumn.money("totalComputedCharges", ExcelHeaders.TOTAL_COMPUTED_CHARGES,
                    asset -> asset.getCharges() == null ? 0.0 : asset.getCharges().getTotal()));

    /** The per-date breakdown behind a holding, on its own sheet. */
    public static final List<ExcelColumn<TransactionQuantity>> TRANSACTION_QUANTITIES = List.of(
            ExcelColumn.text("stockCode", ExcelHeaders.STOCK_CODE, TransactionQuantity::stockCode),
            ExcelColumn.text("brokerName", ExcelHeaders.BROKER_NAME, TransactionQuantity::brokerName),
            ExcelColumn.text("transactionDate", ExcelHeaders.TRANSACTION_DATE, TransactionQuantity::transactionDate),
            ExcelColumn.quantity("quantity", ExcelHeaders.QUANTITY, TransactionQuantity::quantity));

    /** A recorded trade, for the transaction download. */
    public static final List<ExcelColumn<TransactionEntity>> TRANSACTIONS = List.of(
            ExcelColumn.text("email", ExcelHeaders.EMAIL, TransactionEntity::getEmail),
            ExcelColumn.text("stockCode", ExcelHeaders.STOCK_CODE, TransactionEntity::getStockCode),
            ExcelColumn.text("stockName", ExcelHeaders.STOCK_NAME, TransactionEntity::getStockName),
            ExcelColumn.text("assetType", ExcelHeaders.ASSET_TYPE, txn -> name(txn.getAssetType())),
            ExcelColumn.text("exchangeName", ExcelHeaders.EXCHANGE_NAME, TransactionEntity::getExchangeName),
            ExcelColumn.text("brokerName", ExcelHeaders.BROKER_NAME, txn -> name(txn.getBrokerName())),
            ExcelColumn.text("transactionType", ExcelHeaders.TRANSACTION_TYPE, txn -> name(txn.getTransactionType())),
            ExcelColumn.quantity("quantity", ExcelHeaders.QUANTITY, TransactionEntity::getQuantity),
            ExcelColumn.money("price", ExcelHeaders.PRICE, TransactionEntity::getPrice),
            ExcelColumn.money("totalValue", ExcelHeaders.TOTAL_VALUE, TransactionEntity::getTotalValue),
            ExcelColumn.date("maturityDate", ExcelHeaders.MATURITY_DATE, TransactionEntity::getMaturityDate),
            ExcelColumn.money("brokerCharges", ExcelHeaders.BROKER_CHARGES, TransactionEntity::getBrokerCharges),
            ExcelColumn.money("miscCharges", ExcelHeaders.MISC_CHARGES, TransactionEntity::getMiscCharges),
            ExcelColumn.date("transactionDate", ExcelHeaders.TRANSACTION_DATE, TransactionEntity::getTransactionDate));

    /**
     * The upload template.
     *
     * <p>Deliberately not {@link #TRANSACTIONS}: the template must carry only what the upload
     * parser reads back, so it omits {@code totalValue} — which is derived — and carries
     * {@code comments}, which the parser accepts and the export does not produce.
     */
    public static final List<ExcelColumn<TransactionEntity>> TRANSACTION_TEMPLATE = List.of(
            ExcelColumn.text("email", ExcelHeaders.EMAIL, TransactionEntity::getEmail),
            ExcelColumn.text("stockCode", ExcelHeaders.STOCK_CODE, TransactionEntity::getStockCode),
            ExcelColumn.text("stockName", ExcelHeaders.STOCK_NAME, TransactionEntity::getStockName),
            ExcelColumn.text("assetType", ExcelHeaders.ASSET_TYPE, txn -> name(txn.getAssetType())),
            ExcelColumn.text("exchangeName", ExcelHeaders.EXCHANGE_NAME, TransactionEntity::getExchangeName),
            ExcelColumn.text("brokerName", ExcelHeaders.BROKER_NAME, txn -> name(txn.getBrokerName())),
            ExcelColumn.text("transactionType", ExcelHeaders.TRANSACTION_TYPE, txn -> name(txn.getTransactionType())),
            ExcelColumn.quantity("quantity", ExcelHeaders.QUANTITY, TransactionEntity::getQuantity),
            ExcelColumn.money("price", ExcelHeaders.PRICE, TransactionEntity::getPrice),
            ExcelColumn.date("transactionDate", ExcelHeaders.TRANSACTION_DATE, TransactionEntity::getTransactionDate),
            ExcelColumn.date("maturityDate", ExcelHeaders.MATURITY_DATE, TransactionEntity::getMaturityDate),
            ExcelColumn.money("brokerCharges", ExcelHeaders.BROKER_CHARGES, TransactionEntity::getBrokerCharges),
            ExcelColumn.money("miscCharges", ExcelHeaders.MISC_CHARGES, TransactionEntity::getMiscCharges),
            ExcelColumn.text("comments", ExcelHeaders.COMMENTS, TransactionEntity::getComment));

    /** One holding's quantity on one transaction date, flattened for the second sheet. */
    public record TransactionQuantity(String stockCode, String brokerName, String transactionDate,
                                      Double quantity) {
    }

    /**
     * One side of a holding's computed charges, as a figure rather than an absence.
     *
     * <p>The response distinguishes "never priced" from "charged nothing" by leaving the note null,
     * which matters to an API caller deciding what to display. A spreadsheet column cannot carry
     * that distinction, and a blank cell in a money column reads as data loss, so both become zero.
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

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
