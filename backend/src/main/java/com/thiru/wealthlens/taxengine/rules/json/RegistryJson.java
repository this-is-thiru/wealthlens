package com.thiru.wealthlens.taxengine.rules.json;

import com.thiru.wealthlens.taxengine.rules.component.CashNature;
import com.thiru.wealthlens.taxengine.rules.component.ComponentDefinition;
import com.thiru.wealthlens.taxengine.rules.component.ComponentKind;
import com.thiru.wealthlens.taxengine.rules.component.ComponentLifecycle;
import com.thiru.wealthlens.taxengine.rules.component.DeprecationPolicy;
import com.thiru.wealthlens.taxengine.rules.component.SalaryBaseInclusion;
import com.thiru.wealthlens.taxengine.rules.component.TaxTreatment;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.pack.ConditionSet;
import com.thiru.wealthlens.taxengine.rules.pack.DisallowanceSet;
import com.thiru.wealthlens.taxengine.rules.regime.ConditionSetId;
import com.thiru.wealthlens.taxengine.rules.regime.DisallowanceSetId;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * The component registry, and the shared disallowance and condition sets.
 */
public final class RegistryJson {

    private RegistryJson() {
    }

    // ------------------------------------------------------------ components

    /** Every component in {@code exec/components.json}, keyed by code. */
    public static Map<ComponentCode, ComponentDefinition> components(JsonNode root, String path) {
        Map<ComponentCode, ComponentDefinition> byCode = new LinkedHashMap<>();
        for (ComponentDefinition definition :
                PackJson.arrayOrEmpty(root, "components", path, e -> component(e.node(), e.path()))) {
            if (byCode.put(definition.code(), definition) != null) {
                throw new PackValidationException(
                        path + " defines component " + definition.code() + " twice");
            }
        }
        return byCode;
    }

    /** One component, from its own file on the timeline or an entry of a year's registry (D-089). */
    public static ComponentDefinition component(JsonNode node, String path) {
        ComponentCode code = new ComponentCode(PackJson.text(node, "code", path));
        String where = path + " (" + code + ")";

        return PackJson.construct(where, () ->
        new ComponentDefinition(
                        code,
                        PackJson.text(node, "displayName", where),
                        node.get("head") == null
                                ? Optional.empty()
                                : Optional.of(PackJson.enumValue(node, "head", where, HeadCode.class)),
                        PackJson.enumValue(node, "kind", where, ComponentKind.class),
                        PackJson.enumValue(node, "cashNature", where, CashNature.class),
                        PackJson.enumValue(node, "treatment", where, TaxTreatment.class),
                        ruleIds(node, "feedsInto", where),
                        salaryBaseFor(node, where),
                        node.get("informationalOnly") != null && PackJson.bool(node, "informationalOnly", where),
                        lifecycle(PackJson.object(node, "lifecycle", where), where + ".lifecycle"),
                        ValueJson.provenance(
                                PackJson.object(node, "provenance", where), where + ".provenance")));
    }

    static ComponentLifecycle lifecycle(JsonNode node, String path) {
        return PackJson.construct(path, () ->
        new ComponentLifecycle(
                        ValueJson.dateRange(PackJson.object(node, "validity", path), path + ".validity"),
                        PackJson.optionalText(node, "supersededBy", path).map(ComponentCode::new),
                        deprecation(
                                PackJson.object(node, "onWithdrawal", path), path + ".onWithdrawal")));
    }

    static DeprecationPolicy deprecation(JsonNode node, String path) {
        String tag = PackJson.tag(node, path, "RequireUserCorrection", "AutoMigrate", "HistoricalOnly");
        return switch (tag) {
            case "RequireUserCorrection" -> new DeprecationPolicy.RequireUserCorrection(
                    PackJson.text(node, "guidance", path));
            case "AutoMigrate" -> new DeprecationPolicy.AutoMigrate(
                    new ComponentCode(PackJson.text(node, "to", path)),
                    Rate.percent(PackJson.text(node, "conversionFactor", path)));
            case "HistoricalOnly" -> new DeprecationPolicy.HistoricalOnly();
            default -> throw new PackValidationException(
                    path + " has unhandled type \"" + tag + "\"");
        };
    }

    // ------------------------------------------------------------------ sets

    /** Shared disallowance sets — the s.205(1) pattern. */
    public static Map<DisallowanceSetId, DisallowanceSet> disallowanceSets(
            JsonNode root, String path) {
        Map<DisallowanceSetId, DisallowanceSet> byId = new LinkedHashMap<>();
        for (DisallowanceSet set : PackJson.arrayOrEmpty(
                root, "disallowanceSets", path, e -> disallowanceSet(e.node(), e.path()))) {
            if (byId.put(set.id(), set) != null) {
                throw new PackValidationException(path + " defines " + set.id() + " twice");
            }
        }
        return byId;
    }

    public static DisallowanceSet disallowanceSet(JsonNode node, String path) {
        DisallowanceSetId id = new DisallowanceSetId(PackJson.text(node, "id", path));
        String where = path + " (" + id + ")";

        return PackJson.construct(where, () ->
        new DisallowanceSet(
                        id,
                        PackJson.text(node, "displayName", where),
                        ruleIds(node, "disallowedRules", where),
                        new RuleId(PackJson.text(node, "basis", where)),
                        ValueJson.provenance(
                                PackJson.object(node, "provenance", where), where + ".provenance")));
    }

    /** Shared condition sets — the s.205(2) pattern. */
    public static Map<ConditionSetId, ConditionSet> conditionSets(JsonNode root, String path) {
        Map<ConditionSetId, ConditionSet> byId = new LinkedHashMap<>();
        for (ConditionSet set : PackJson.arrayOrEmpty(
                root, "conditionSets", path, e -> conditionSet(e.node(), e.path()))) {
            if (byId.put(set.id(), set) != null) {
                throw new PackValidationException(path + " defines " + set.id() + " twice");
            }
        }
        return byId;
    }

    static ConditionSet conditionSet(JsonNode node, String path) {
        ConditionSetId id = new ConditionSetId(PackJson.text(node, "id", path));
        String where = path + " (" + id + ")";

        return PackJson.construct(where, () ->
        new ConditionSet(
                        id,
                        PackJson.text(node, "displayName", where),
                        ConditionJson.conditions(node, "conditions", where),
                        new RuleId(PackJson.text(node, "basis", where)),
                        ValueJson.provenance(
                                PackJson.object(node, "provenance", where), where + ".provenance")));
    }

    private static Set<SalaryBaseInclusion> salaryBaseFor(JsonNode node, String path) {
        return new LinkedHashSet<>(PackJson.arrayOrEmpty(node, "salaryBaseFor", path, e -> new SalaryBaseInclusion(
                new RuleId(PackJson.text(e.node(), "rule", e.path())),
                PackJson.bool(e.node(), "onlyWhereTermsProvide", e.path()))));
    }

    private static Set<RuleId> ruleIds(JsonNode node, String field, String path) {
        return new LinkedHashSet<>(
                PackJson.arrayOrEmpty(node, field, path, e -> new RuleId(e.node().asString())));
    }
}
