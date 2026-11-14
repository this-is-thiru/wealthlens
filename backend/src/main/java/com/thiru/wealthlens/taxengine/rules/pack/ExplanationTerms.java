package com.thiru.wealthlens.taxengine.rules.pack;

import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import java.util.Map;
import java.util.Set;

/**
 * The content's words for the codes a reason names ({@code terms} in {@code content/explanations.json}), grouped so a
 * year's explanations stay within seven parts (W-326b, D-697).
 *
 * @param regime each regime in words, for a reason that names one; plain words, never the regime's legal title
 * @param head each head of income in words (W-327d1, D-687)
 * @param capitalGainsPart each capital-gains part in words (W-327d1, D-687)
 * @param sttState each STT state a row of {@code RS-STT-CHARGEABLE} names, in words (W-327d2, D-688)
 */
public record ExplanationTerms(Map<RegimeCode, String> regime, Map<HeadCode, String> head,
        Map<CapitalGainsPart, String> capitalGainsPart, Map<String, String> sttState) {

    public ExplanationTerms {
        if (regime == null || head == null || capitalGainsPart == null || sttState == null) {
            throw new IllegalArgumentException("Explanation terms need every map (empty maps for none)");
        }
        regime.forEach((code, term) -> Explanation.requireProse("terms.regime." + code, term, Set.of()));
        head.forEach((code, term) -> Explanation.requireProse("terms.head." + code, term, Set.of()));
        capitalGainsPart.forEach((part, term) -> Explanation.requireProse("terms.capitalGainsPart." + part.value(), term,
                Set.of()));
        sttState.forEach((state, term) -> Explanation.requireProse("terms.sttState." + state, term, Set.of()));
        regime = Map.copyOf(regime);
        head = Map.copyOf(head);
        capitalGainsPart = Map.copyOf(capitalGainsPart);
        sttState = Map.copyOf(sttState);
    }

    /** No words for any code. */
    public static ExplanationTerms none() {
        return new ExplanationTerms(Map.of(), Map.of(), Map.of(), Map.of());
    }
}
