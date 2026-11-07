package com.thiru.wealthlens.taxengine.rules.timeline;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * What between two compiled packs a reviewer should read first ({@code DR 15} C4, {@code DR 18} V7; W-139). Each finding is a
 * fact the reviewer judges, not a refusal: a rule may rightly vanish, and a version may rightly repeat its instrument.
 *
 * <p>A version is compared with its <b>predecessor</b>: for each version the later pack holds and the earlier does not, the
 * latest version before it in either pack. So the year boundary (TY N's last version, TY N+1's first) is read once.
 */
public final class PackLint {

    private static final String ABSENT_IN_THIS_ACT = "ABSENT_IN_THIS_ACT";

    private PackLint() {
    }

    /** One thing to read. */
    public sealed interface Finding {

        /** A number moved ten times or more either way: a decimal slip reads like this (C4). */
        record TenfoldChange(RuleId rule, String versions, String field, String before, String after) implements Finding {}

        /** A rule the earlier pack holds and the later does not, with no provision saying the Act dropped it (C4). */
        record UnexplainedDrop(RuleId rule) implements Finding {}

        /** A version naming the same instrument and source as its predecessor: a copy that was never re-read (C4, H-2). */
        record CopiedVersion(RuleId rule, String earlier, String later) implements Finding {}

        /** A version whose definition changed type, or whose list gained or lost an entry: a clause added or dropped (V7). */
        record ShapeChanged(RuleId rule, String earlier, String later, String field, String before, String after)
                implements Finding {}
    }

    public static List<Finding> between(CompiledPack before, CompiledPack after) {
        Map<String, JsonNode> oldRules = PackDiff.rulesOf(before);
        Map<String, JsonNode> newRules = PackDiff.rulesOf(after);
        List<Finding> findings = new ArrayList<>();
        for (Map.Entry<String, JsonNode> rule : oldRules.entrySet()) {
            if (!newRules.containsKey(rule.getKey()) && !saysAbsentFromAnAct(rule.getValue())) {
                findings.add(new Finding.UnexplainedDrop(new RuleId(rule.getKey())));
            }
        }
        for (Map.Entry<String, JsonNode> rule : newRules.entrySet()) {
            if (oldRules.containsKey(rule.getKey())) {
                lintVersions(new RuleId(rule.getKey()), PackDiff.versionsOf(oldRules.get(rule.getKey())),
                        PackDiff.versionsOf(rule.getValue()), findings);
            }
        }
        return List.copyOf(findings);
    }

    private static boolean saysAbsentFromAnAct(JsonNode rule) {
        for (JsonNode version : rule.path("versions")) {
            for (JsonNode field : version) {
                if (ABSENT_IN_THIS_ACT.equals(field.path("presence").asString(""))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void lintVersions(
            RuleId rule, Map<String, JsonNode> was, Map<String, JsonNode> is, List<Finding> findings) {
        TreeMap<String, JsonNode> all = new TreeMap<>(was);
        all.putAll(is);
        for (Map.Entry<String, JsonNode> version : is.entrySet()) {
            String key = version.getKey();
            if (was.containsKey(key)) {
                tenfold(rule, key, "", was.get(key), version.getValue(), findings);
                continue;
            }
            Map.Entry<String, JsonNode> predecessor = all.lowerEntry(key);
            if (predecessor != null) {
                lintSuccession(rule, predecessor.getKey(), predecessor.getValue(), key, version.getValue(), findings);
            }
        }
    }

    private static void lintSuccession(
            RuleId rule, String earlier, JsonNode old, String later, JsonNode now, List<Finding> findings) {
        if (Objects.equals(old.get("introducedBy"), now.get("introducedBy"))
                && Objects.equals(old.path("provenance").get("sourceUrl"), now.path("provenance").get("sourceUrl"))) {
            findings.add(new Finding.CopiedVersion(rule, earlier, later));
        }
        String type = old.path("definition").path("type").asString("");
        String newType = now.path("definition").path("type").asString("");
        if (!type.equals(newType)) {
            findings.add(new Finding.ShapeChanged(rule, earlier, later, "definition.type", type, newType));
        }
        listSizes(rule, earlier, later, "", old, now, findings);
        tenfold(rule, earlier + " → " + later, "", old, now, findings);
    }

    /** Every list present in both versions at the same path whose length differs. */
    private static void listSizes(RuleId rule, String earlier, String later, String path, JsonNode old, JsonNode now,
            List<Finding> findings) {
        if (old.isArray() && now.isArray() && old.size() != now.size()) {
            findings.add(new Finding.ShapeChanged(rule, earlier, later, path, old.size() + " entries", now.size() + " entries"));
        }
        children(path, old, now).forEach(child -> listSizes(rule, earlier, later, child.path(), child.earlierNode(),
                child.laterNode(), findings));
    }

    private static void tenfold(
            RuleId rule, String versions, String path, JsonNode old, JsonNode now, List<Finding> findings) {
        if (old.isValueNode() && now.isValueNode() && isTenfold(PackDiff.text(old), PackDiff.text(now))) {
            findings.add(new Finding.TenfoldChange(rule, versions, path, PackDiff.text(old), PackDiff.text(now)));
        }
        children(path, old, now).forEach(child -> tenfold(rule, versions, child.path(), child.earlierNode(), child.laterNode(), findings));
    }

    /** One position both versions hold. */
    private record Child(String path, JsonNode earlierNode, JsonNode laterNode) {
    }

    /** The fields, or the indices, both nodes hold; none when they are not both objects or both lists. */
    private static List<Child> children(String path, JsonNode old, JsonNode now) {
        List<Child> children = new ArrayList<>();
        if (old.isObject() && now.isObject()) {
            for (String field : PackDiff.fieldNames(old, now)) {
                if (old.has(field) && now.has(field)) {
                    children.add(new Child(join(path, field), old.get(field), now.get(field)));
                }
            }
        } else if (old.isArray() && now.isArray()) {
            for (int i = 0; i < Math.min(old.size(), now.size()); i++) {
                children.add(new Child(path + "[" + i + "]", old.get(i), now.get(i)));
            }
        }
        return children;
    }

    /**
     * Ten times or more either way, between two non-zero decimal numbers; anything else — text, dates, zero — is not. Exact:
     * each text is read as digits and a count of decimal places and compared by cross-multiplying, since this is a typo
     * check over untyped pack text, not money or rate arithmetic (owner, W-139 build).
     */
    static boolean isTenfold(String before, String after) {
        Optional<Decimal> was = Decimal.parse(before);
        Optional<Decimal> is = Decimal.parse(after);
        if (was.isEmpty() || is.isEmpty() || was.get().digits().signum() == 0 || is.get().digits().signum() == 0) {
            return false;
        }
        return was.get().isAtLeastTenTimes(is.get()) || is.get().isAtLeastTenTimes(was.get());
    }

    /** A decimal text's magnitude: {@code digits} ÷ 10^{@code places}, sign dropped. */
    private record Decimal(BigInteger digits, int places) {

        private static final Pattern NUMBER = Pattern.compile("-?(\\d+)(?:\\.(\\d+))?");

        static Optional<Decimal> parse(String text) {
            Matcher number = NUMBER.matcher(text);
            if (!number.matches()) {
                return Optional.empty();
            }
            String fraction = number.group(2) == null ? "" : number.group(2);
            return Optional.of(new Decimal(new BigInteger(number.group(1) + fraction), fraction.length()));
        }

        /** this ≥ 10 × other, as digits × 10^other.places ≥ 10 × other.digits × 10^places. */
        boolean isAtLeastTenTimes(Decimal other) {
            BigInteger left = digits.multiply(BigInteger.TEN.pow(other.places));
            BigInteger right = other.digits.multiply(BigInteger.TEN.pow(places + 1));
            return left.compareTo(right) >= 0;
        }
    }

    private static String join(String path, String field) {
        return path.isEmpty() ? field : path + "." + field;
    }
}
