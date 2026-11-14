package com.thiru.wealthlens.taxengine.rules.pack;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A year's explanations ({@code content/explanations.json}; W-116, D-671), with the one sentence they share: what raising
 * a limit that did not bind would do, whose {@code {limits}} the renderer fills with those limits' names (F37.3).
 *
 * @param otherLimits the sentence naming the limits that did not bind; required once any explanation has a limit
 * @param byRule each rule's explanation
 */
public record Explanations(Optional<String> otherLimits, Map<RuleId, Explanation> byRule) {

    public Explanations {
        if (otherLimits == null || byRule == null) {
            throw new IllegalArgumentException("Explanations need otherLimits (Optional.empty() for none) and byRule");
        }
        otherLimits.ifPresent(sentence -> Explanation.requireProse("otherLimits", sentence, Set.of("{limits}")));
        if (otherLimits.isEmpty() && byRule.values().stream().anyMatch(explanation -> !explanation.byLimit().isEmpty())) {
            throw new IllegalArgumentException("An explanation names its limits, so otherLimits is required to say what"
                    + " the others would do (F37.3)");
        }
        byRule = Map.copyOf(byRule);
    }

    /** A pack with no explanations: every line shows its rule id (F37.2). */
    public static Explanations none() {
        return new Explanations(Optional.empty(), Map.of());
    }
}
