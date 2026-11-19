package com.thiru.wealthlens.taxengine.rules.definition;

import java.util.Set;

/**
 * The movable assets whose use by the employee is not valued at 10% of cost (S4 r.15(5) Table IV Sl. 7; Rules 1962
 * r.3(7)(vii); D-119).
 *
 * <p>Data because the list changes by year: laptops and computers in TY 2025-26; laptops, computers, tablets and
 * mobile phones in TY 2026-27.
 *
 * @param kinds the kinds excluded in this version
 */
public record ExcludedAssets(Set<MovableAssetKind> kinds) implements RuleDefinition {

    public ExcludedAssets {
        if (kinds == null || kinds.isEmpty() || kinds.contains(MovableAssetKind.OTHER)) {
            throw new IllegalArgumentException("An exclusion lists at least one named kind, never OTHER");
        }
        kinds = Set.copyOf(kinds);
    }

    /** Whether the use of an asset of this kind is outside the valuation. */
    public boolean excludes(MovableAssetKind kind) {
        return kinds.contains(kind);
    }
}
