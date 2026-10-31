package com.thiru.wealthlens.taxengine.rules.timeline;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * What changes between two compiled rule packs: the publish gate's diff (D-037, D-057), so a reviewer
 * signs off a list of changed rules, versions and values rather than a guess. The same year before and
 * after an edit, or year N against N+1 for the cross-year report {@code W-139} builds on.
 *
 * <p>Read from the compiled, canonical {@code law/rates.json}, so one walk covers every field of every
 * rule shape, and an edited note — dropped by the canonical form — is not a change. A version is
 * identified by its start date, so closing it reads as a changed end date rather than a version
 * removed and another added.
 *
 * @param findings sorted by rule id, then version, then field
 */
public record PackDiff(TaxYearId from, TaxYearId to, List<Finding> findings) {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String RULE_LEVEL = "(rule)";
    private static final String ABSENT = "(absent)";

    public PackDiff {
        findings = List.copyOf(findings);
    }

    /** One difference. {@code W-139} adds its lint findings, and a magnitude on {@code ValueChanged}. */
    public sealed interface Finding {

        record RuleAdded(RuleId rule) implements Finding {}

        record RuleRemoved(RuleId rule) implements Finding {}

        /** @param version the version's start date, or {@code pending} */
        record VersionAdded(RuleId rule, String version) implements Finding {}

        /** @param version the version's start date, or {@code pending} */
        record VersionRemoved(RuleId rule, String version) implements Finding {}

        /**
         * @param version the version's start date, {@code pending}, or {@code (rule)} for a field of the
         *     rule itself
         * @param field a path such as {@code cap.bands[1].rate}
         * @param before the old value, or {@code (absent)}
         * @param after the new value, or {@code (absent)}
         */
        record ValueChanged(RuleId rule, String version, String field, String before, String after)
                implements Finding {}
    }

    public static PackDiff between(CompiledPack before, CompiledPack after) {
        Map<String, JsonNode> oldRules = rulesOf(before);
        Map<String, JsonNode> newRules = rulesOf(after);
        TreeSet<String> ids = new TreeSet<>(oldRules.keySet());
        ids.addAll(newRules.keySet());

        List<Finding> findings = new ArrayList<>();
        for (String id : ids) {
            RuleId rule = new RuleId(id);
            JsonNode was = oldRules.get(id);
            JsonNode is = newRules.get(id);
            if (was == null) {
                findings.add(new Finding.RuleAdded(rule));
            } else if (is == null) {
                findings.add(new Finding.RuleRemoved(rule));
            } else {
                compareRule(rule, was, is, findings);
            }
        }
        return new PackDiff(before.taxYear(), after.taxYear(), findings);
    }

    private static void compareRule(RuleId rule, JsonNode was, JsonNode is, List<Finding> findings) {
        TreeSet<String> fields = fieldNames(was, is);
        fields.remove("versions");
        for (String field : fields) {
            compare(rule, RULE_LEVEL, field, was.get(field), is.get(field), findings);
        }
        Map<String, JsonNode> oldVersions = versionsOf(was);
        Map<String, JsonNode> newVersions = versionsOf(is);
        TreeSet<String> keys = new TreeSet<>(oldVersions.keySet());
        keys.addAll(newVersions.keySet());
        for (String key : keys) {
            JsonNode old = oldVersions.get(key);
            JsonNode now = newVersions.get(key);
            if (old == null) {
                findings.add(new Finding.VersionAdded(rule, key));
            } else if (now == null) {
                findings.add(new Finding.VersionRemoved(rule, key));
            } else {
                for (String field : fieldNames(old, now)) {
                    compare(rule, key, field, old.get(field), now.get(field), findings);
                }
            }
        }
    }

    /** Walks objects by field and arrays by index; anything else is compared as a leaf. */
    private static void compare(
            RuleId rule, String version, String path, JsonNode was, JsonNode is, List<Finding> findings) {
        if (was != null && is != null && was.isObject() && is.isObject()) {
            for (String field : fieldNames(was, is)) {
                compare(rule, version, path + "." + field, was.get(field), is.get(field), findings);
            }
        } else if (was != null && is != null && was.isArray() && is.isArray()) {
            for (int i = 0; i < Math.max(was.size(), is.size()); i++) {
                compare(rule, version, path + "[" + i + "]", was.get(i), is.get(i), findings);
            }
        } else {
            String before = text(was);
            String after = text(is);
            if (!before.equals(after)) {
                findings.add(new Finding.ValueChanged(rule, version, path, before, after));
            }
        }
    }

    private static String text(JsonNode node) {
        if (node == null) {
            return ABSENT;
        }
        return node.isValueNode() ? node.asString() : MAPPER.writeValueAsString(node);
    }

    private static TreeSet<String> fieldNames(JsonNode was, JsonNode is) {
        TreeSet<String> names = new TreeSet<>(was.propertyNames());
        names.addAll(is.propertyNames());
        return names;
    }

    private static Map<String, JsonNode> rulesOf(CompiledPack pack) {
        Map<String, JsonNode> rules = new TreeMap<>();
        MAPPER.readTree(pack.files().get("law/rates.json")).path("rules")
                .forEach(rule -> rules.put(rule.path("ruleId").asString(), rule));
        return rules;
    }

    /** A version's identity: its start date, or {@code pending}, which has none. */
    private static Map<String, JsonNode> versionsOf(JsonNode rule) {
        Map<String, JsonNode> versions = new TreeMap<>();
        for (JsonNode version : rule.path("versions")) {
            JsonNode validity = version.path("validity");
            String key = switch (validity.path("type").asString()) {
                case "Retrospective" -> validity.path("deemedEffectiveFrom").asString();
                case "PendingNotification" -> "pending";
                default -> validity.path("from").asString();
            };
            versions.put(key, version);
        }
        return versions;
    }
}
