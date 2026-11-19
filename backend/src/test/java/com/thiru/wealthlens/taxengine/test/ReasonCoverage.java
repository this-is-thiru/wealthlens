package com.thiru.wealthlens.taxengine.test;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.pack.Explanation;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The coverage pin for F37's "why" (W-327e3, D-693): every line a node writes reads in words. A line whose rule the
 * shipped content explains, and which carries {@code limitedBy}, must either start with one of that rule's worded
 * limits (the renderer matches a limit by prefix, {@code ExplanationRenderer.limitSentence}) or carry a reason;
 * otherwise a user would read the engine's own text. {@link NodeHarness} and the golden runner call it
 * on every result, so a new site without a reason fails the test that reaches it. A rule no shipped year explains (a
 * test's own fixture) shows its rule id instead (F37.2), and is not checked.
 */
public final class ReasonCoverage {

    private ReasonCoverage() {
    }

    /**
     * Fails on the first line the content cannot word.
     *
     * @throws AssertionError naming the line's rule and its text
     */
    public static void require(List<TraceLine> lines) {
        for (TraceLine line : lines) {
            Set<String> worded = Shipped.WORDED_LIMITS.get(line.rule());
            if (worded == null || line.reason().isPresent() || line.limitedBy().isEmpty()
                    || worded.stream().anyMatch(line.limitedBy().get()::startsWith)) {
                continue;
            }
            throw new AssertionError("A " + line.rule().value() + " line reads \"" + line.limitedBy().get()
                    + "\", which is not a limit its content words, and it carries no reason (W-327e3): give the site"
                    + " that writes it a reason");
        }
    }

    /** The shipped content's worded limits by rule, over every shipped year; read once, on first use. */
    private static final class Shipped {

        private static final Map<RuleId, Set<String>> WORDED_LIMITS = load();

        private static Map<RuleId, Set<String>> load() {
            Map<RuleId, Set<String>> worded = new HashMap<>();
            for (TaxYearId year : ClasspathRulePackProvider.shippedYears("rulepacks")) {
                Map<RuleId, Explanation> byRule = new ClasspathRulePackProvider("rulepacks", Set.of(year),
                        new JsonPackAssembler(LockedInstruments.of(year))).load(year).explanations().byRule();
                byRule.forEach((rule, explanation) -> worded.computeIfAbsent(rule, ignored -> new HashSet<>())
                        .addAll(explanation.byLimit().keySet()));
            }
            return Map.copyOf(worded);
        }
    }
}
