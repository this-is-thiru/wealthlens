package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;

/**
 * The amount is the total of one head's entered lines that feed this rule — "salary includes wages; annuity or
 * pension; …" (S2 s.16) read against the registry, which places each component under its head and names the
 * rules it feeds (D-089, D-091, D-093). A head also holds lines that are not what the rule defines — professional
 * tax is under "Salaries" but is paid out of salary — so the head alone does not decide what is counted.
 *
 * @param head the head whose lines are totalled
 */
public record HeadTotal(HeadCode head) implements RuleDefinition {

    public HeadTotal {
        if (head == null) {
            throw new IllegalArgumentException("A head total needs its head");
        }
    }
}
