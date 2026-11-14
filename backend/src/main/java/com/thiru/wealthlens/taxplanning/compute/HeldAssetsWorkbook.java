package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.shared.util.parser.ExcelColumn;
import com.thiru.wealthlens.shared.util.parser.ExcelSheet;
import com.thiru.wealthlens.shared.util.parser.ExcelWorkbooks;
import java.io.ByteArrayInputStream;
import java.util.List;

/**
 * The held-assets view as a workbook, as the removed holdings-by-term export was (W-262): the lots on one sheet and each
 * stock's quantity by term on another, column for column what the JSON answer holds.
 */
public final class HeldAssetsWorkbook {

    static final String LOTS_SHEET = "LOTS";
    static final String TOTALS_SHEET = "TOTALS";

    private static final List<ExcelColumn<HeldAssetsView.Lot>> LOT_COLUMNS = List.of(
            ExcelColumn.text("stockCode", "STOCK CODE", HeldAssetsView.Lot::stockCode),
            ExcelColumn.text("broker", "BROKER", HeldAssetsView.Lot::broker),
            ExcelColumn.text("accountHolder", "ACCOUNT HOLDER", HeldAssetsView.Lot::accountHolder),
            ExcelColumn.quantity("quantity", "QUANTITY", HeldAssetsView.Lot::quantity),
            ExcelColumn.date("acquiredOn", "ACQUIRED ON", HeldAssetsView.Lot::acquiredOn),
            ExcelColumn.text("term", "TERM", HeldAssetsView.Lot::term),
            ExcelColumn.text("termRow", "RULE ROW", HeldAssetsView.Lot::termRow),
            new ExcelColumn<>("monthsRead", "HOLDING PERIOD (MONTHS)", HeldAssetsView.Lot::monthsRead),
            ExcelColumn.date("longTermFrom", "LONG TERM FROM", HeldAssetsView.Lot::longTermFrom),
            ExcelColumn.text("note", "NOTE", HeldAssetsView.Lot::note));

    private static final List<ExcelColumn<HeldAssetsView.StockTotal>> TOTAL_COLUMNS = List.of(
            ExcelColumn.text("stockCode", "STOCK CODE", HeldAssetsView.StockTotal::stockCode),
            ExcelColumn.quantity("longTermQuantity", "LONG TERM QUANTITY", HeldAssetsView.StockTotal::longTermQuantity),
            ExcelColumn.quantity("shortTermQuantity", "SHORT TERM QUANTITY", HeldAssetsView.StockTotal::shortTermQuantity),
            ExcelColumn.quantity("quantityWithoutTerm", "QUANTITY WITHOUT TERM", HeldAssetsView.StockTotal::quantityWithoutTerm));

    private HeldAssetsWorkbook() {
    }

    public static ByteArrayInputStream of(HeldAssetsView view) {
        return ExcelWorkbooks.toStream(List.of(new ExcelSheet<>(LOTS_SHEET, LOT_COLUMNS, view.lots()),
                new ExcelSheet<>(TOTALS_SHEET, TOTAL_COLUMNS, view.totals())));
    }
}
