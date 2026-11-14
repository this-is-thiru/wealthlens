package com.thiru.wealthlens.taxengine.rules.json;

import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.id.ReasonCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.pack.Explanation;
import com.thiru.wealthlens.taxengine.rules.pack.Explanations;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;

/** {@code content/explanations.json}, read into {@link Explanations} (W-116, D-671). */
public final class ExplanationJson {

    private ExplanationJson() {
    }

    /** The file's shared sentence and every explanation, keyed by rule; a rule explained twice is refused. */
    public static Explanations explanations(JsonNode root, String path) {
        Map<RuleId, Explanation> byRule = new LinkedHashMap<>();
        for (Explanation explanation : PackJson.arrayOrEmpty(root, "explanations", path,
                element -> explanation(element.node(), element.path()))) {
            if (byRule.put(explanation.rule(), explanation) != null) {
                throw new PackValidationException(path + " explains " + explanation.rule() + " twice");
            }
        }
        Map<ReasonCode, String> reasons = reasons(root, path);
        return PackJson.construct(path, () -> new Explanations(PackJson.optionalText(root, "otherLimits", path), byRule,
                reasons, terms(root, path, "regime", RegimeCode::new), terms(root, path, "head", HeadCode::valueOf),
                terms(root, path, "capitalGainsPart", CapitalGainsPart::new),
                terms(root, path, "sttState", Function.identity())));
    }

    /** One map of {@code terms}: each code, read by {@code code}, to its words; absent is empty. */
    private static <K> Map<K, String> terms(JsonNode root, String path, String kind, Function<String, K> code) {
        Map<K, String> byCode = new LinkedHashMap<>();
        String where = path + ".terms." + kind;
        PackJson.optionalObject(root, "terms", path).flatMap(terms -> PackJson.optionalObject(terms, kind, path + ".terms"))
                .ifPresent(words -> words.properties().forEach(entry -> byCode.put(
                        PackJson.construct(where, () -> code.apply(entry.getKey())),
                        PackJson.text(words, entry.getKey(), where))));
        return byCode;
    }

    /** Each reason code's sentence (W-327a, D-678); a code worded twice is refused. */
    private static Map<ReasonCode, String> reasons(JsonNode root, String path) {
        Map<ReasonCode, String> byCode = new LinkedHashMap<>();
        PackJson.arrayOrEmpty(root, "reasons", path, element -> {
            ReasonCode code = PackJson.construct(element.path(),
                    () -> new ReasonCode(PackJson.text(element.node(), "code", element.path())));
            if (byCode.put(code, PackJson.text(element.node(), "why", element.path() + " (" + code + ")")) != null) {
                throw new PackValidationException(path + " words the reason " + code + " twice");
            }
            return code;
        });
        return byCode;
    }

    /**
     * The rules that never name a trace line of their own, each with the reason (W-325d, D-672 C2a): read for a value
     * inside another rule's line, a condition only, refusing, naming only a notice or checked at build time. A build fact
     * beside the sentences it completes, so a year can be checked to explain or list every rule; nothing at run time reads
     * it. A rule listed twice, or a blank reason, is refused.
     */
    public static Map<RuleId, String> notALine(JsonNode root, String path) {
        Map<RuleId, String> byRule = new LinkedHashMap<>();
        PackJson.arrayOrEmpty(root, "notALine", path, element -> {
            RuleId rule = PackJson.construct(element.path(),
                    () -> new RuleId(PackJson.text(element.node(), "rule", element.path())));
            if (byRule.put(rule, PackJson.text(element.node(), "reason", element.path() + " (" + rule + ")")) != null) {
                throw new PackValidationException(path + " lists " + rule + " in notALine twice");
            }
            return rule;
        });
        return Map.copyOf(byRule);
    }

    /** One rule's explanation. */
    public static Explanation explanation(JsonNode node, String path) {
        RuleId rule = PackJson.construct(path, () -> new RuleId(PackJson.text(node, "rule", path)));
        String where = path + " (" + rule + ")";
        Map<String, String> byLimit = new LinkedHashMap<>();
        PackJson.optionalObject(node, "byLimit", where).ifPresent(limits -> limits.properties()
                .forEach(limit -> byLimit.put(limit.getKey(), PackJson.text(limits, limit.getKey(), where + ".byLimit"))));
        return PackJson.construct(where, () -> new Explanation(rule, PackJson.text(node, "what", where), byLimit));
    }
}
