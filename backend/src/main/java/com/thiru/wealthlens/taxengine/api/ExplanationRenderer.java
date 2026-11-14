package com.thiru.wealthlens.taxengine.api;

import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeValue;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.Explanation;
import com.thiru.wealthlens.taxengine.rules.pack.Explanations;
import com.thiru.wealthlens.taxengine.rules.reason.Reason;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonFact;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A trace line in plain language (PRD F37; W-116, D-671): the rule's {@code what}, its figures filled from the line, then
 * why — the sentence for the limit the line's {@code limitedBy} names, with the year's sentence naming the limits that did
 * not bind (F37.3), else the sentence for the line's reason with its facts in words (W-327a, D-678), else
 * {@code limitedBy} as it stands. A rule with no explanation, or a {@code what} naming a figure the
 * line does not hold in money, shows the rule id: never a blank (F37.2). Every word comes from the pack's content.
 */
final class ExplanationRenderer {

    private ExplanationRenderer() {
    }

    static String explain(TraceLine line, Explanations explanations) {
        Explanation explanation = explanations.byRule().get(line.rule());
        if (explanation == null) {
            return line.rule().value();
        }
        Optional<String> what = filled(explanation.what(), money(line.in()), money(line.out()));
        if (what.isEmpty()) {
            return line.rule().value();
        }
        Optional<String> bound = line.limitedBy().flatMap(limitedBy -> limitSentence(explanation, explanations, limitedBy));
        Optional<String> why = bound.or(() -> line.reason().flatMap(reason -> reasonSentence(reason, explanations)))
                .or(line::limitedBy);
        return why.map(sentence -> what.get() + " " + sentence).orElse(what.get());
    }

    /**
     * The reason's sentence with each fact in words, or empty where the content words no such code, or a fact it names
     * has no word: then the line shows {@code limitedBy}, never a placeholder.
     */
    private static Optional<String> reasonSentence(Reason reason, Explanations explanations) {
        String why = explanations.reasons().get(reason.code());
        if (why == null) {
            return Optional.empty();
        }
        for (Map.Entry<String, ReasonFact> fact : reason.facts().entrySet()) {
            Optional<String> words = switch (fact.getValue()) {
                case ReasonFact.RegimeFact regime -> Optional.ofNullable(explanations.regimeTerms().get(regime.regime()));
            };
            if (words.isEmpty()) {
                return Optional.empty();
            }
            why = why.replace("{" + fact.getKey() + "}", words.get());
        }
        return Optional.of(why);
    }

    /** The figure a value holds, where it is money. */
    static Optional<Money> money(NodeValue value) {
        return value instanceof MoneyValue money ? Optional.of(money.amount()) : Optional.empty();
    }

    private static Optional<String> filled(String template, Optional<Money> in, Optional<Money> out) {
        if (template.contains("{in}") && in.isEmpty() || template.contains("{out}") && out.isEmpty()) {
            return Optional.empty();
        }
        String text = template;
        if (in.isPresent()) {
            text = text.replace("{in}", in.get().inIndianNotation());
        }
        if (out.isPresent()) {
            text = text.replace("{out}", out.get().inIndianNotation());
        }
        return Optional.of(text);
    }

    /**
     * The sentence for the longest limit name {@code limitedBy} starts with, since a node may add a clause after it
     * (s.134), with the sentence naming the others; empty where it names no limit.
     */
    private static Optional<String> limitSentence(Explanation explanation, Explanations explanations, String limitedBy) {
        Optional<String> bound = explanation.byLimit().keySet().stream()
                .filter(limitedBy::startsWith)
                .max(Comparator.comparingInt(String::length));
        if (bound.isEmpty()) {
            return Optional.empty();
        }
        String others = explanation.byLimit().keySet().stream().filter(limit -> !limit.equals(bound.get())).sorted()
                .collect(Collectors.joining(", "));
        String sentence = explanation.byLimit().get(bound.get());
        return Optional.of(others.isEmpty() ? sentence
                : sentence + " " + explanations.otherLimits().orElseThrow().replace("{limits}", others));
    }
}
