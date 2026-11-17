package com.thiru.wealthlens.taxplanning.documents.parser;

import com.thiru.wealthlens.taxengine.api.CertificateLayout;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedColumn;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedTable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Part B as the Gazette lays it out (pp.2274 – 2275, W-126c2b): each table's title over all its columns, a group heading
 * over its columns with the others spanning both header rows, a row per printed row number, then the Total row with its
 * merged cells. Amounts are set to the right of their cells, as issuers set them; the rest are centred.
 */
final class PartBFixture {

    private PartBFixture() {
    }

    static String html(CertificateLayout layout, Map<String, String> values, Set<String> omitted) {
        StringBuilder html = new StringBuilder("<p class=\"h\">PART B</p>");
        layout.tables().stream().filter(table -> !omitted.contains(table.path())).forEach(table -> table(table, values, omitted, html));
        return html.toString();
    }

    private static void table(PrintedTable table, Map<String, String> values, Set<String> omitted, StringBuilder html) {
        html.append("<table class=\"b\"><tr><td colspan=\"").append(table.columns().size()).append("\"><b>")
                .append(String.join("<br/>", table.title())).append("</b></td></tr>");
        headings(table, html);
        for (int row = 1; values.containsKey(table.path() + ".row" + row + "." + table.columns().get(0).key()); row++) {
            html.append("<tr>");
            for (PrintedColumn column : table.columns()) {
                cell(column, 1, values.getOrDefault(table.path() + ".row" + row + "." + column.key(), ""), html);
            }
            html.append("</tr>");
        }
        if (!omitted.contains(table.path() + ".total")) {
            html.append("<tr><td>").append(table.totalLabel()).append("</td>");
            for (List<String> cell : table.totalCells()) {
                PrintedColumn first = table.columns().stream().filter(column -> column.key().equals(cell.get(0))).findFirst().orElseThrow();
                cell(first, cell.size(), values.getOrDefault(table.path() + ".total." + cell.get(0), ""), html);
            }
            html.append("</tr>");
        }
        html.append("</table>");
    }

    private static void headings(PrintedTable table, StringBuilder html) {
        boolean grouped = table.columns().stream().anyMatch(column -> column.group().isPresent());
        html.append("<tr>");
        Optional<String> open = Optional.empty();
        for (PrintedColumn column : table.columns()) {
            if (column.group().isEmpty()) {
                html.append("<td").append(grouped ? " rowspan=\"2\"" : "").append(">").append(column.heading()).append("</td>");
            } else if (!column.group().equals(open)) {
                open = column.group();
                long width = table.columns().stream().filter(other -> other.group().equals(column.group())).count();
                html.append("<td colspan=\"").append(width).append("\">").append(column.group().get()).append("</td>");
            }
        }
        html.append("</tr>");
        if (grouped) {
            html.append("<tr>");
            table.columns().stream().filter(column -> column.group().isPresent())
                    .forEach(column -> html.append("<td>").append(column.heading()).append("</td>"));
            html.append("</tr>");
        }
    }

    private static void cell(PrintedColumn column, int span, String value, StringBuilder html) {
        html.append("<td colspan=\"").append(span).append("\"").append("AMOUNT".equals(column.kind()) ? " class=\"a\"" : "")
                .append(">").append(value).append("</td>");
    }
}
