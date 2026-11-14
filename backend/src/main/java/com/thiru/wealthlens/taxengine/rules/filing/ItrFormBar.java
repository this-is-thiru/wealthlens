package com.thiru.wealthlens.taxengine.rules.filing;

import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;

/**
 * One thing that disqualifies a person from a form — r.164(3)'s "this rule shall not apply to …" clauses (W-090b,
 * D-521 C4a). Each bar carries its own provenance so that a refusal can name the clause that refused and whether that
 * clause is verified, instead of doubting the whole form.
 *
 * @param reason what to tell the person when this bar refuses them
 * @param condition holds when the person is barred
 * @param provenance the clause, and how well it was checked
 */
public record ItrFormBar(String reason, Condition condition, Provenance provenance) {

    public ItrFormBar {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A form's bar must state its reason; it reaches the person it refuses");
        }
        if (condition == null || provenance == null) {
            throw new IllegalArgumentException("The bar \"" + reason + "\" must carry its condition and its source");
        }
    }
}
