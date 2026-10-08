package com.thiru.wealthlens.taxengine.rules.definition;

/**
 * How many houses the owner may specify as having a nil annual value — S2 s.21(7)(a), "shall apply only in respect of
 * two of such houses as specified by the assessee"; 1961 s.23(4)(a) (D-155). Not a cap: the houses beyond it are not
 * limited to some amount, they are valued as if let, so it is a definition the node applies to the houses entered.
 *
 * @param houses the most houses the owner may specify
 */
public record OwnResidenceHouses(int houses) implements RuleDefinition {

    public OwnResidenceHouses {
        if (houses < 1) {
            throw new IllegalArgumentException("At least one house may be an own residence, but the row says " + houses);
        }
    }
}
