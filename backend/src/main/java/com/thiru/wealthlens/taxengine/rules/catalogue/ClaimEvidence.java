package com.thiru.wealthlens.taxengine.rules.catalogue;

/**
 * A document a claim rests on, with what settles it (I25): evidence nothing closes would leave a person never knowing
 * whether they hold enough.
 *
 * @param name the document
 * @param closedBy what the person must hold for it to be complete
 */
public record ClaimEvidence(String name, String closedBy) {

    public ClaimEvidence {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Claim evidence needs a name");
        }
        if (closedBy == null || closedBy.isBlank()) {
            throw new IllegalArgumentException("I25 — evidence \"" + name + "\" must name what closes it");
        }
    }
}
