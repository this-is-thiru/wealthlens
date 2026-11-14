package com.thiru.wealthlens.taxengine.rules.pack;

import com.thiru.wealthlens.taxengine.rules.id.ReasonCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonCodes;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A year's explanations ({@code content/explanations.json}; W-116, D-671), with the one sentence they share: what raising
 * a limit that did not bind would do, whose {@code {limits}} the renderer fills with those limits' names (F37.3).
 *
 * @param otherLimits the sentence naming the limits that did not bind; required once any explanation has a limit
 * @param byRule each rule's explanation
 * @param reasons why a line's figure is what it is, one sentence per reason code the engine publishes, filling the
 *     reason's facts by name (W-327a, D-678)
 * @param regimeTerms each regime in words, for a reason that names one; plain words, never the regime's legal title
 */
public record Explanations(Optional<String> otherLimits, Map<RuleId, Explanation> byRule,
        Map<ReasonCode, String> reasons, Map<RegimeCode, String> regimeTerms) {

    public Explanations {
        if (otherLimits == null || byRule == null || reasons == null || regimeTerms == null) {
            throw new IllegalArgumentException("Explanations need otherLimits (Optional.empty() for none), byRule,"
                    + " reasons and regimeTerms (empty maps for none)");
        }
        reasons.forEach(Explanations::requireReason);
        regimeTerms.forEach((regime, term) -> Explanation.requireProse("terms.regime." + regime, term, Set.of()));
        otherLimits.ifPresent(sentence -> Explanation.requireProse("otherLimits", sentence, Set.of("{limits}")));
        if (otherLimits.isEmpty() && byRule.values().stream().anyMatch(explanation -> !explanation.byLimit().isEmpty())) {
            throw new IllegalArgumentException("An explanation names its limits, so otherLimits is required to say what"
                    + " the others would do (F37.3)");
        }
        byRule = Map.copyOf(byRule);
        reasons = Map.copyOf(reasons);
        regimeTerms = Map.copyOf(regimeTerms);
    }

    /** A code the engine publishes, worded with only the facts its reason carries. */
    private static void requireReason(ReasonCode code, String why) {
        Set<String> facts = ReasonCodes.FACTS.get(code);
        if (facts == null) {
            throw new IllegalArgumentException(code + " is not a reason code the engine publishes: "
                    + ReasonCodes.FACTS.keySet());
        }
        Explanation.requireProse("reasons." + code, why,
                facts.stream().map(fact -> "{" + fact + "}").collect(Collectors.toSet()));
    }

    /** A pack with no explanations: every line shows its rule id (F37.2). */
    public static Explanations none() {
        return new Explanations(Optional.empty(), Map.of(), Map.of(), Map.of());
    }
}
