package com.thiru.wealthlens.taxengine.api;

import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeValue;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.Explanation;
import com.thiru.wealthlens.taxengine.rules.pack.Explanations;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A trace line in plain language (PRD F37; W-116, D-671): the rule's {@code what}, its figures filled from the line, then
 * why — the sentence for the limit the line's {@code limitedBy} names, with the year's sentence naming the limits that did
 * not bind (F37.3), else {@code limitedBy} as it stands. A rule with no explanation, or a {@code what} naming a figure the
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
        return line.limitedBy().map(limitedBy -> what.get() + " " + why(explanation, explanations, limitedBy))
                .orElse(what.get());
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

    /** The longest limit name {@code limitedBy} starts with, since a node may add a clause after it (s.134). */
    private static String why(Explanation explanation, Explanations explanations, String limitedBy) {
        Optional<String> bound = explanation.byLimit().keySet().stream()
                .filter(limitedBy::startsWith)
                .max(Comparator.comparingInt(String::length));
        if (bound.isEmpty()) {
            return limitedBy;
        }
        String others = explanation.byLimit().keySet().stream().filter(limit -> !limit.equals(bound.get())).sorted()
                .collect(Collectors.joining(", "));
        String sentence = explanation.byLimit().get(bound.get());
        return others.isEmpty() ? sentence
                : sentence + " " + explanations.otherLimits().orElseThrow().replace("{limits}", others);
    }
}
