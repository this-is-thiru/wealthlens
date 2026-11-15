package com.thiru.wealthlens.taxengine.rules.pack;

import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ReasonCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonCodes;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A year's explanations ({@code content/explanations.json}; W-116, D-671), with the one sentence they share: what raising
 * a limit that did not bind would do, whose {@code {limits}} the renderer fills with those limits' names (F37.3).
 *
 * @param otherLimits the sentence naming the limits that did not bind; required once any explanation has a limit
 * @param byRule each rule's explanation
 * @param reasons why a line's figure is what it is, one sentence per reason code the engine publishes, filling the
 *     reason's facts by name (W-327a, D-678)
 * @param terms the words for the codes a reason names
 * @param headroom what would change a figure, one sentence per input worth naming as a lever, with {@code {more}} and
 *     {@code {next}} (W-326b, D-697); an input with none says nothing (03-advisory A4.4)
 * @param evidence what a claim's evidence says, by sentence code, filling {@code {amount}} or {@code {months}} (W-119c
 *     C4a): the words for an amount allowed against what documents support, never a judgement of risk ({@code DR 12} C7)
 */
public record Explanations(Optional<String> otherLimits, Map<RuleId, Explanation> byRule,
        Map<ReasonCode, String> reasons, ExplanationTerms terms, Map<BaseId, String> headroom, Map<String, String> evidence) {

    private static final Pattern EVIDENCE_CODE = Pattern.compile("[A-Z][A-Z_]{1,63}");

    public Explanations {
        if (otherLimits == null || byRule == null || reasons == null || terms == null || headroom == null
                || evidence == null) {
            throw new IllegalArgumentException("Explanations need otherLimits (Optional.empty() for none), byRule,"
                    + " reasons, terms, headroom and evidence (empty for none)");
        }
        evidence.forEach((code, sentence) -> {
            if (!EVIDENCE_CODE.matcher(code).matches()) {
                throw new IllegalArgumentException("evidence." + code + ": a sentence code is upper-case with underscores");
            }
            Explanation.requireProse("evidence." + code, sentence, Set.of("{amount}", "{months}"));
        });
        reasons.forEach(Explanations::requireReason);
        headroom.forEach((input, sentence) -> Explanation.requireProse("headroom." + input.value(), sentence,
                Set.of("{more}", "{next}")));
        otherLimits.ifPresent(sentence -> Explanation.requireProse("otherLimits", sentence, Set.of("{limits}")));
        if (otherLimits.isEmpty() && byRule.values().stream().anyMatch(explanation -> !explanation.byLimit().isEmpty())) {
            throw new IllegalArgumentException("An explanation names its limits, so otherLimits is required to say what"
                    + " the others would do (F37.3)");
        }
        byRule = Map.copyOf(byRule);
        reasons = Map.copyOf(reasons);
        headroom = Map.copyOf(headroom);
        evidence = Map.copyOf(evidence);
    }

    /** Each regime in words. */
    public Map<RegimeCode, String> regimeTerms() {
        return terms.regime();
    }

    /** Each head of income in words. */
    public Map<HeadCode, String> headTerms() {
        return terms.head();
    }

    /** Each capital-gains part in words. */
    public Map<CapitalGainsPart, String> partTerms() {
        return terms.capitalGainsPart();
    }

    /** Each STT state in words. */
    public Map<String, String> sttStateTerms() {
        return terms.sttState();
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
        return new Explanations(Optional.empty(), Map.of(), Map.of(), ExplanationTerms.none(), Map.of(), Map.of());
    }
}
