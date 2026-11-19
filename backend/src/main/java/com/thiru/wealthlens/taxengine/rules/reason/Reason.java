package com.thiru.wealthlens.taxengine.rules.reason;

import com.thiru.wealthlens.taxengine.rules.id.ReasonCode;
import java.util.Map;

/**
 * Why a trace line's figure is what it is, published by the engine as facts (F37.3; W-327a, D-678). The words are
 * content: the pack's sentence for the code fills the facts by name, so re-wording changes no trace line (I27), and
 * {@code limitedBy} stays the audit text beside it.
 *
 * @param code what kind of reason; one of {@link ReasonCodes#FACTS}
 * @param facts the facts the code carries, by name; exactly the names {@link ReasonCodes#FACTS} lists for it
 */
public record Reason(ReasonCode code, Map<String, ReasonFact> facts) {

    public Reason {
        if (code == null || facts == null) {
            throw new IllegalArgumentException("A reason needs its code and its facts");
        }
        if (!ReasonCodes.FACTS.containsKey(code)) {
            throw new IllegalArgumentException(code + " is not a reason code the engine publishes; add it to ReasonCodes");
        }
        if (!facts.keySet().equals(ReasonCodes.FACTS.get(code))) {
            throw new IllegalArgumentException(code + " carries the facts " + ReasonCodes.FACTS.get(code) + ", not "
                    + facts.keySet());
        }
        facts = Map.copyOf(facts);
    }
}
