package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.regime.DisallowanceSetId;

/**
 * The provision a regime's disallowance set rests on — S2 s.202(2)(a)'s "without any exemption or deduction under"
 * (D-165). A rule row, so the reason has what a row has: a version per year (1961 s.115BAC(2)(i) before the 2025 Act)
 * and its provenance, both cited wherever a disallowed rule is explained. It defines no amount; it says which set its
 * words are the reason for, so the set and its basis cannot point past each other.
 *
 * @param set the disallowance set this provision is the reason for
 */
public record DisallowanceBasis(DisallowanceSetId set) implements RuleDefinition {

    public DisallowanceBasis {
        if (set == null) {
            throw new IllegalArgumentException("A disallowance basis must name the set it is the reason for");
        }
    }
}
