package com.thiru.wealthlens.taxengine.rules.json;

import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinition;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.pack.MidYearCombination;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.registry.LegalStatus;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.PublicationStatus;
import com.thiru.wealthlens.taxengine.rules.relief.RuleRelief;
import com.thiru.wealthlens.taxengine.rules.rounding.RuleRounding;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalAxis;
import com.thiru.wealthlens.taxengine.rules.temporal.proration.CalendarMonths;
import com.thiru.wealthlens.taxengine.rules.temporal.proration.CompletedMonths;
import com.thiru.wealthlens.taxengine.rules.temporal.proration.Days;
import com.thiru.wealthlens.taxengine.rules.temporal.proration.ProrationBasis;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
                        prorationBasis(node, where),
                        ValueJson.provenance(
                                PackJson.object(node, "headProvenance", where), where + ".headProvenance")));
    }

    /** How a segment's share of the year is measured, where versions are prorated per segment (D-098). */
    private static Optional<ProrationBasis> prorationBasis(JsonNode node, String path) {
        return PackJson.optionalText(node, "prorationBasis", path).map(name -> switch (name) {
            case "DAYS" -> new Days();
            case "CALENDAR_MONTHS" -> new CalendarMonths();
            case "COMPLETED_MONTHS" -> new CompletedMonths();
            default -> throw new PackValidationException(path + ".prorationBasis must be one of"
                    + " [DAYS, CALENDAR_MONTHS, COMPLETED_MONTHS], but was \"" + name + "\"");
        });
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
                        relief(node, path),
                        rounding(node, path),
                        definition(node, path),
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
     * owned by the assessee" limits nothing by itself — or grant a relief, state a rounding or define an
     * amount and limit nothing. A rule with no cap, relief, rounding, definition or condition is a different
     * matter, and is refused: it would load, evaluate, and mean nothing.
     */
    private static com.thiru.wealthlens.taxengine.rules.cap.CapShape capOrNull(JsonNode node, String path) {
        var cap = PackJson.optionalObject(node, "cap", path);
        if (cap.isEmpty()) {
            List<com.thiru.wealthlens.taxengine.rules.condition.Condition> conditions =
                    ConditionJson.conditions(node, "conditions", path);
            if (conditions.isEmpty()
                    && PackJson.optionalObject(node, "relief", path).isEmpty()
                    && PackJson.optionalObject(node, "rounding", path).isEmpty()
                    && PackJson.optionalObject(node, "definition", path).isEmpty()) {
                throw new PackValidationException(
                        path + " has no cap, relief, rounding, definition or conditions; it would load,"
                                + " evaluate, and mean nothing");
            }
            return null;
        }
        return ShapeJson.cap(cap.get(), path + ".cap");
    }

    private static Optional<RuleRelief> relief(JsonNode node, String path) {
        return PackJson.optionalObject(node, "relief", path)
                .map(relief -> ShapeJson.relief(relief, path + ".relief"));
    }

    private static Optional<RuleDefinition> definition(JsonNode node, String path) {
        return PackJson.optionalObject(node, "definition", path)
                .map(definition -> ShapeJson.definition(definition, path + ".definition"));
    }

    private static Optional<RuleRounding> rounding(JsonNode node, String path) {
        return PackJson.optionalObject(node, "rounding", path)
                .map(rounding -> ShapeJson.rounding(rounding, path + ".rounding"));
    }
}
