package com.thiru.wealthlens.taxengine.rules.timeline;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.PackJson;
import com.thiru.wealthlens.taxengine.rules.json.ValueJson;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A regime's election, one version per span of years (D-133).
 *
 * <p>The same regime is chosen under different provisions in different years — TY 2025-26 under 1961 s.115BAC(6)
 * with the s.139(1) due date, TY 2026-27 under 2025 s.202(4) with s.263(1) — so a regime file on the timeline holds
 * {@code elections}, each {@code {validity, protocol}}. A year's pack holds the one protocol in force as its
 * {@code election}, so nothing that reads a compiled pack sees the versions.
 */
final class RegimeElections {

    private static final String ELECTIONS = "elections";

    private RegimeElections() {
    }

    /** The regime file as one year's pack holds it: the year's protocol as {@code election}. */
    static JsonNode forTaxYear(JsonNode regime, TaxYearId taxYear, String path) {
        List<JsonNode> inYear = new ArrayList<>();
        for (JsonNode version : PackJson.array(regime, ELECTIONS, path, entry -> entry.node())) {
            if (window(version, path).overlapsTaxYear(taxYear)) {
                inYear.add(version);
            }
        }
        if (inYear.size() != 1) {
            throw new PackValidationException(path + ": " + inYear.size() + " election versions are in force in "
                    + taxYear.label() + "; a regime is chosen one way in a year");
        }
        ObjectNode projected = (ObjectNode) regime.deepCopy();
        projected.remove(ELECTIONS);
        projected.set("election", inYear.getFirst().get("protocol"));
        return projected;
    }

    /** Every version, each as a regime file with that one election — for reading each protocol once. */
    static List<JsonNode> everyVersion(JsonNode regime, String path) {
        List<JsonNode> versions = PackJson.array(regime, ELECTIONS, path, entry -> entry.node());
        requireNoOverlap(versions, path);
        List<JsonNode> projected = new ArrayList<>();
        for (JsonNode version : versions) {
            ObjectNode copy = (ObjectNode) regime.deepCopy();
            copy.remove(ELECTIONS);
            copy.set("election", PackJson.object(version, "protocol", path));
            projected.add(copy);
        }
        return projected;
    }

    private static void requireNoOverlap(List<JsonNode> versions, String path) {
        List<DateRange> windows = versions.stream().map(version -> window(version, path)).toList();
        for (int i = 0; i < windows.size(); i++) {
            for (int j = i + 1; j < windows.size(); j++) {
                if (windows.get(i).overlaps(windows.get(j))) {
                    throw new PackValidationException(path + ": election versions overlap, " + windows.get(i)
                            + " and " + windows.get(j) + "; one day may have one election");
                }
            }
        }
    }

    private static DateRange window(JsonNode version, String path) {
        JsonNode validity = PackJson.object(version, "validity", path);
        PackJson.tag(validity, path + ".validity", "Between");
        return ValueJson.dateRange(validity, path + ".validity");
    }
}
