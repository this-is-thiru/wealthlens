package com.thiru.wealthlens.taxengine.rules.json;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.pack.Explanation;
import com.thiru.wealthlens.taxengine.rules.pack.Explanations;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.LinkedHashMap;
import java.util.Map;
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
        return PackJson.construct(path, () -> new Explanations(PackJson.optionalText(root, "otherLimits", path), byRule));
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
