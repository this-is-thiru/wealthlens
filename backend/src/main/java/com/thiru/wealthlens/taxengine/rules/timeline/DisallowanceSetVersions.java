package com.thiru.wealthlens.taxengine.rules.timeline;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.PackJson;
import com.thiru.wealthlens.taxengine.rules.json.RegistryJson;
import com.thiru.wealthlens.taxengine.rules.json.ValueJson;
import com.thiru.wealthlens.taxengine.rules.pack.DisallowanceSet;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A disallowance set, one version per span of years (D-165).
 *
 * <p>The same set rests on different provisions in different years — TY 2025-26 on 1961 s.115BAC(2)(i), TY 2026-27
 * on 2025 s.202(2)(a) — and lists only the rules each year holds, so a set file on the timeline holds
 * {@code versions}, each {@code {validity, displayName, basis, disallowedRules, provenance}}. A year's pack holds the
 * one version in force as a plain set, so nothing that reads a compiled pack sees the versions.
 */
final class DisallowanceSetVersions {

    private static final String VERSIONS = "versions";

    private DisallowanceSetVersions() {
    }

    /** The set as one year's pack holds it; empty where no version is in force in the year. */
    static Optional<JsonNode> forTaxYear(JsonNode set, TaxYearId taxYear, String path) {
        List<JsonNode> inYear = new ArrayList<>();
        for (JsonNode version : PackJson.array(set, VERSIONS, path, entry -> entry.node())) {
            if (window(version, path).overlapsTaxYear(taxYear)) {
                inYear.add(version);
            }
        }
        if (inYear.size() > 1) {
            throw new PackValidationException(path + ": " + inYear.size() + " versions are in force in "
                    + taxYear.label() + "; a set says one thing in a year");
        }
        return inYear.stream().findFirst().map(version -> projected(set, version, path));
    }

    /** Every version, each read as the set a year would hold — so a malformed version fails on reading. */
    static List<DisallowanceSet> everyVersion(JsonNode set, String path) {
        List<JsonNode> versions = PackJson.array(set, VERSIONS, path, entry -> entry.node());
        requireNoOverlap(versions, path);
        return versions.stream()
                .map(version -> RegistryJson.disallowanceSet(projected(set, version, path), path))
                .toList();
    }

    private static JsonNode projected(JsonNode set, JsonNode version, String path) {
        ObjectNode copy = (ObjectNode) version.deepCopy();
        copy.remove("validity");
        copy.put("id", PackJson.text(set, "id", path));
        return copy;
    }

    private static void requireNoOverlap(List<JsonNode> versions, String path) {
        List<DateRange> windows = versions.stream().map(version -> window(version, path)).toList();
        for (int i = 0; i < windows.size(); i++) {
            for (int j = i + 1; j < windows.size(); j++) {
                if (windows.get(i).overlaps(windows.get(j))) {
                    throw new PackValidationException(path + ": versions overlap, " + windows.get(i)
                            + " and " + windows.get(j) + "; one day may have one version");
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
