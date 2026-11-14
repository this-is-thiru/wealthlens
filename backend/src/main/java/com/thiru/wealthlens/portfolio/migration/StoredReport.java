package com.thiru.wealthlens.portfolio.migration;

import com.thiru.wealthlens.shared.util.money.TMoney;
import java.util.LinkedHashSet;
import java.util.List;
import org.bson.Document;

/**
 * Sums two stored {@code FinancialReport} documents figure by figure — at the year, each month and each half —
 * as {@code ProfitAndLossService} would have accumulated them into one (W-160d3, D-369).
 */
final class StoredReport {

    private static final List<String> FIGURES =
            List.of("purchase_amount", "sell_amount", "profit", "misc_charges", "brokerage");
    private static final String MONTHS = "capital_gains";
    private static final List<String> HALVES = List.of("first_half_report", "second_half_report");

    private StoredReport() {
    }

    /** The sum of the two reports, either of which may be absent; {@code null} when both are. */
    static Document sum(Document left, Document right) {
        if (left == null) {
            return right == null ? null : copyOf(right);
        }
        if (right == null) {
            return copyOf(left);
        }
        Document year = figures(left, right);
        Document leftMonths = monthsOf(left);
        Document rightMonths = monthsOf(right);
        Document months = new Document();
        for (String month : union(leftMonths, rightMonths)) {
            months.put(month, sumMonth(leftMonths.get(month, Document.class), rightMonths.get(month, Document.class)));
        }
        year.put(MONTHS, months);
        return year;
    }

    private static Document sumMonth(Document left, Document right) {
        if (left == null || right == null) {
            return copyOf(left == null ? right : left);
        }
        Document month = figures(left, right);
        month.put("month", left.get("month"));
        for (String half : HALVES) {
            Document leftHalf = left.get(half, Document.class);
            Document rightHalf = right.get(half, Document.class);
            if (leftHalf != null || rightHalf != null) {
                month.put(half, leftHalf == null || rightHalf == null
                        ? copyOf(leftHalf == null ? rightHalf : leftHalf) : figures(leftHalf, rightHalf));
            }
        }
        return month;
    }

    private static Document figures(Document left, Document right) {
        Document sum = new Document();
        FIGURES.forEach(figure -> sum.put(figure, TMoney.add(amountOf(left, figure), amountOf(right, figure))));
        return sum;
    }

    private static double amountOf(Document report, String figure) {
        Object amount = report.get(figure);
        return amount instanceof Number number ? number.doubleValue() : 0;
    }

    private static Document monthsOf(Document report) {
        Document months = report.get(MONTHS, Document.class);
        return months == null ? new Document() : months;
    }

    private static List<String> union(Document left, Document right) {
        LinkedHashSet<String> keys = new LinkedHashSet<>(left.keySet());
        keys.addAll(right.keySet());
        return List.copyOf(keys);
    }

    private static Document copyOf(Document report) {
        return Document.parse(report.toJson());
    }
}
