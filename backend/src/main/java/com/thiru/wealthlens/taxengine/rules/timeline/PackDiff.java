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
 * <p>Read from the compiled, canonical {@code law/rates.json} and {@code law/regimes.json}, so one walk covers every
 * field of every rule shape, regime and disallowance set, and an edited note — dropped by the canonical form — is not a
 * change. A version is
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

    /** One difference. What a reviewer should look at first among them is {@link PackLint}'s (W-139). */
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

        /**
         * A regime's or a disallowance set's field changed (W-139); a whole entry added or removed reads as its field
         * {@code (entry)} going from or to {@code (absent)}.
         *
         * @param entry e.g. {@code regime NEW_TAX_REGIME} or {@code disallowance set DIS-NEW-TAX-REGIME-NO-SETOFF}
         */
        record RegimeValueChanged(String entry, String field, String before, String after) implements Finding {}
    }

    /** Builds the finding for one changed leaf. */
    @FunctionalInterface
    private interface LeafChange {
        Finding of(String path, String before, String after);
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
        compareRegimeEntries(before, after, "regimes", "code", "regime ", findings);
        compareRegimeEntries(before, after, "disallowanceSets", "id", "disallowance set ", findings);
        return new PackDiff(before.taxYear(), after.taxYear(), findings);
    }

    private static void compareRegimeEntries(
            CompiledPack before, CompiledPack after, String list, String key, String label, List<Finding> findings) {
        Map<String, JsonNode> was = entriesOf(before, list, key);
        Map<String, JsonNode> is = entriesOf(after, list, key);
        TreeSet<String> names = new TreeSet<>(was.keySet());
        names.addAll(is.keySet());
        for (String name : names) {
            String entry = label + name;
            if (!was.containsKey(name) || !is.containsKey(name)) {
                findings.add(new Finding.RegimeValueChanged(entry, "(entry)", was.containsKey(name) ? "(present)" : ABSENT,
                        is.containsKey(name) ? "(present)" : ABSENT));
            } else {
                for (String field : fieldNames(was.get(name), is.get(name))) {
                    compare(field, was.get(name).get(field), is.get(name).get(field), findings,
                            (path, old, now) -> new Finding.RegimeValueChanged(entry, path, old, now));
                }
            }
        }
    }

    private static Map<String, JsonNode> entriesOf(CompiledPack pack, String list, String key) {
        Map<String, JsonNode> entries = new TreeMap<>();
        MAPPER.readTree(pack.files().get("law/regimes.json")).path(list)
                .forEach(entry -> entries.put(entry.path(key).asString(), entry));
        return entries;
    }

    private static void compareRule(RuleId rule, JsonNode was, JsonNode is, List<Finding> findings) {
        TreeSet<String> fields = fieldNames(was, is);
        fields.remove("versions");
        for (String field : fields) {
            compare(field, was.get(field), is.get(field), findings,
                    (path, old, now) -> new Finding.ValueChanged(rule, RULE_LEVEL, path, old, now));
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
                    compare(field, old.get(field), now.get(field), findings,
                            (path, before, after) -> new Finding.ValueChanged(rule, key, path, before, after));
                }
            }
        }
    }

    /** Every changed leaf between two versions of one rule, labelled {@code version} (W-139a). */
    static List<Finding> versionChanges(RuleId rule, String version, JsonNode was, JsonNode is) {
        List<Finding> findings = new ArrayList<>();
        for (String field : fieldNames(was, is)) {
            compare(field, was.get(field), is.get(field), findings,
                    (path, before, after) -> new Finding.ValueChanged(rule, version, path, before, after));
        }
        return findings;
    }

    /** Walks objects by field and arrays by index; anything else is compared as a leaf. */
    private static void compare(String path, JsonNode was, JsonNode is, List<Finding> findings, LeafChange change) {
        if (was != null && is != null && was.isObject() && is.isObject()) {
            for (String field : fieldNames(was, is)) {
                compare(path + "." + field, was.get(field), is.get(field), findings, change);
            }
        } else if (was != null && is != null && was.isArray() && is.isArray()) {
            for (int i = 0; i < Math.max(was.size(), is.size()); i++) {
                compare(path + "[" + i + "]", was.get(i), is.get(i), findings, change);
            }
        } else {
            String before = text(was);
            String after = text(is);
            if (!before.equals(after)) {
                findings.add(change.of(path, before, after));
            }
        }
    }

    static String text(JsonNode node) {
        if (node == null) {
            return ABSENT;
        }
        return node.isValueNode() ? node.asString() : MAPPER.writeValueAsString(node);
    }

    static TreeSet<String> fieldNames(JsonNode was, JsonNode is) {
        TreeSet<String> names = new TreeSet<>(was.propertyNames());
        names.addAll(is.propertyNames());
        return names;
    }

    static Map<String, JsonNode> rulesOf(CompiledPack pack) {
        Map<String, JsonNode> rules = new TreeMap<>();
        MAPPER.readTree(pack.files().get("law/rates.json")).path("rules")
                .forEach(rule -> rules.put(rule.path("ruleId").asString(), rule));
        return rules;
    }

    /** A version's identity: its start date, or {@code pending}, which has none. */
    static Map<String, JsonNode> versionsOf(JsonNode rule) {
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
