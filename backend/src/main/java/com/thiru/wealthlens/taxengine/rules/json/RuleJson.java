package com.thiru.wealthlens.taxengine.rules.json;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.pack.MidYearCombination;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.registry.LegalStatus;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.PublicationStatus;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalAxis;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * A rule: a stable id, the clock it is read against, and its versions in order.
 *
 * <p>Reads {@code law/rates.json} and every other law file of the same shape. The ordering inside
 * {@link #version} matters in one place: {@code provenance} is read before {@code validity},
 * because a {@code PendingNotification} validity carries the provenance of the interim position it
 * names.
 */
public final class RuleJson {

    private RuleJson() {
    }

    /** Every rule in one law file, keyed by id. */
    public static Map<RuleId, RuleRow> rules(JsonNode root, String path) {
        Map<RuleId, RuleRow> byId = new LinkedHashMap<>();
        for (RuleRow row : PackJson.array(root, "rules", path, e -> rule(e.node(), e.path()))) {
            RuleRow existing = byId.put(row.id(), row);
            if (existing != null) {
                throw new PackValidationException(
                        path + " defines rule " + row.id() + " twice; the second would silently win");
            }
        }
        return byId;
    }

    /** One rule row. */
    public static RuleRow rule(JsonNode node, String path) {
        RuleId id = new RuleId(PackJson.text(node, "ruleId", path));
        String where = path + " (" + id + ")";

        MidYearCombination combination =
                PackJson.enumValue(node, "midYearCombination", where, MidYearCombination.class);
        RuleId combinationRule = PackJson.optionalText(node, "midYearCombinationRule", where)
                .map(RuleId::new)
                .orElse(null);

        return PackJson.construct(where, () ->
        new RuleRow(
                        id,
                        PackJson.enumValue(node, "axis", where, TemporalAxis.class),
                        PackJson.array(node, "versions", where, e -> version(e.node(), e.path())),
                        combination,
                        combinationRule,
                        ValueJson.provenance(
                                PackJson.object(node, "headProvenance", where), where + ".headProvenance")));
    }

    /** One version of a rule. */
    public static RuleVersion version(JsonNode node, String path) {
        Provenance provenance =
                ValueJson.provenance(PackJson.object(node, "provenance", path), path + ".provenance");

        return PackJson.construct(path, () ->
        new RuleVersion(
                        ShapeJson.validity(
                                PackJson.object(node, "validity", path), path + ".validity", provenance),
                        ShapeJson.regimes(PackJson.object(node, "regimes", path), path + ".regimes"),
                        capOrNull(node, path),
                        ConditionJson.conditions(node, "conditions", path),
                        ValueJson.provision(
                                PackJson.object(node, "provision1961", path), path + ".provision1961"),
                        ValueJson.provision(
                                PackJson.object(node, "provision2025", path), path + ".provision2025"),
                        ValueJson.amendment(
                                PackJson.object(node, "introducedBy", path), path + ".introducedBy"),
                        PackJson.enumValue(node, "legalStatus", path, LegalStatus.class),
                        PackJson.enumValue(node, "publicationStatus", path, PublicationStatus.class),
                        provenance));
    }

    /**
     * The cap, which may legitimately be absent.
     *
     * <p>A rule can state a condition and no amount — {@code SAL-HRA-002}'s "the accommodation is not
     * owned by the assessee" limits nothing by itself. A rule with neither a cap nor a condition is
     * a different matter, and is refused: it would load, evaluate, and mean nothing.
     */
    private static com.thiru.wealthlens.taxengine.rules.cap.CapShape capOrNull(JsonNode node, String path) {
        var cap = PackJson.optionalObject(node, "cap", path);
        if (cap.isEmpty()) {
            List<com.thiru.wealthlens.taxengine.rules.condition.Condition> conditions =
                    ConditionJson.conditions(node, "conditions", path);
            if (conditions.isEmpty()) {
                throw new PackValidationException(
                        path + " has neither a cap nor any conditions; it would load, evaluate, and"
                                + " mean nothing");
            }
            return null;
        }
        return ShapeJson.cap(cap.get(), path + ".cap");
    }
}
