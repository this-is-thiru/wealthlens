package com.thiru.wealthlens.taxengine.golden;

import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * One {@code DR 08} case as a file: the facts it states and the figure it expects, copied, never
 * computed (D-083).
 *
 * @param ageAtYearEnd the completed age on the tax year's last day, where the case states one
 * @param expectedPayable the case's "Expected" — rounded, so compared with {@code round.netpayable}
 */
record GoldenCase(
        String id,
        String source,
        RegimeCode regime,
        Money totalIncome,
        Optional<Integer> ageAtYearEnd,
        List<TaxYearId> taxYears,
        Money expectedPayable) {

    static GoldenCase load(String resource) {
        return GoldenFiles.load(resource, GoldenCase::of);
    }

    static List<GoldenCase> loadAll(String directory) {
        return GoldenFiles.loadAll(directory, GoldenCase::of);
    }

    private static GoldenCase of(JsonNode node) {
        List<TaxYearId> years = new ArrayList<>();
        node.get("taxYears").forEach(year -> years.add(taxYear(year.asString())));
        return new GoldenCase(
                node.get("id").asString(),
                node.get("source").asString(),
                new RegimeCode(node.get("regime").asString()),
                Money.of(node.get("totalIncome").asString()),
                node.has("ageAtYearEnd") ? Optional.of(node.get("ageAtYearEnd").asInt()) : Optional.empty(),
                List.copyOf(years),
                Money.of(node.get("expected").get("payable").asString()));
    }

    static TaxYearId taxYear(String label) {
        TaxYearId year = TaxYearId.of(Integer.parseInt(label.substring(0, 4)));
        if (!year.label().equals(label)) {
            throw new IllegalArgumentException("\"" + label + "\" is not a tax year label like 2026-27");
        }
        return year;
    }

    @Override
    public String toString() {
        return id;
    }
}
