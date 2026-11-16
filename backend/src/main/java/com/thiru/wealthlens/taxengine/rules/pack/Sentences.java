package com.thiru.wealthlens.taxengine.rules.pack;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The year's sentences kept by code rather than by rule, one map per reader (W-123a C5): grouped so that a new reader
 * adds a map here, not a component to {@link Explanations}. Each sentence states no figure (SC-2) and fills only the
 * placeholders its map permits.
 *
 * @param evidence what a claim's evidence says, filling {@code {amount}} or {@code {months}} (W-119c C4a): the words for
 *     an amount allowed against what documents support, never a judgement of risk ({@code DR 12} C7)
 * @param claims what is said of a claim made, filling nothing (W-121 C3a)
 * @param report the computation report's headings, notes and disclaimer (W-123a, PRD F10), filling {@code {act}},
 *     {@code {financeAct}}, {@code {rules}}, {@code {taxYear}}, {@code {receipt}}, {@code {computedUnder}} or
 *     {@code {current}}
 * @param computation what a computation says of itself, filling nothing: that it is coarse (W-122b, D-720)
 */
public record Sentences(Map<String, String> evidence, Map<String, String> claims, Map<String, String> report,
        Map<String, String> computation) {

    /** No sentences. */
    public static final Sentences NONE = new Sentences(Map.of(), Map.of(), Map.of(), Map.of());

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z_]{1,63}");

    private static final Set<String> REPORT_PLACEHOLDERS = Set.of("{act}", "{financeAct}", "{rules}", "{taxYear}",
            "{receipt}", "{computedUnder}", "{current}");

    public Sentences {
        if (evidence == null || claims == null || report == null || computation == null) {
            throw new IllegalArgumentException("Sentences need evidence, claims, report and computation (empty for none)");
        }
        require("evidence", evidence, Set.of("{amount}", "{months}"));
        require("claims", claims, Set.of());
        require("report", report, REPORT_PLACEHOLDERS);
        require("computation", computation, Set.of());
        computation = Map.copyOf(computation);
        evidence = Map.copyOf(evidence);
        claims = Map.copyOf(claims);
        report = Map.copyOf(report);
    }

    private static void require(String section, Map<String, String> sentences, Set<String> permitted) {
        sentences.forEach((code, sentence) -> {
            if (!CODE.matcher(code).matches()) {
                throw new IllegalArgumentException(section + "." + code + ": a sentence code is upper-case with underscores");
            }
            Explanation.requireProse(section + "." + code, sentence, permitted);
        });
    }
}
