package com.thiru.wealthlens.taxengine.rules.catalogue;

/**
 * A document a claim rests on, with what settles it (I25): evidence nothing closes would leave a person never knowing
 * whether they hold enough.
 *
 * @param name the document
 * @param closedBy what the person must hold for it to be complete
 * @param from who provides it: the person, or one the entry asks (W-120 C2a)
 * @param recordedAs what the product keeps that says whether it is held
 */
public record ClaimEvidence(String name, String closedBy, ClaimActor from, EvidenceRecord recordedAs) {
    public ClaimEvidence {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Claim evidence needs a name");
        }
        if (closedBy == null || closedBy.isBlank()) {
            throw new IllegalArgumentException("I25 — evidence \"" + name + "\" must name what closes it");
        }
        if (from == null || recordedAs == null) {
            throw new IllegalArgumentException("Claim evidence \"" + name + "\" needs from and recordedAs");
        }
    }
}
