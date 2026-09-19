package com.thiru.wealthlens.taxrules.temporal;

/**
 * Where a rule lives in one Act.
 *
 * <p>A rule version carries two of these, one per Act, because the product computes two tax years
 * governed by different statutes and a user asking &ldquo;which section is this?&rdquo; deserves the
 * one that applied to their year.
 *
 * @param instrumentCitation the Act, e.g. {@code "Income-tax Act, 2025"}
 * @param reference the provision within it, e.g. {@code "s.19(1) Table Sl. 2(a)"}
 * @param presence whether it exists there, is absent, or has not been checked
 */
public record Provision(String instrumentCitation, String reference, Presence presence) {

    public Provision {
        if (presence == null) {
            throw new IllegalArgumentException("A Provision must state its Presence");
        }
        if (presence == Presence.PRESENT && (reference == null || reference.isBlank())) {
            throw new IllegalArgumentException(
                    "A provision marked PRESENT must say where: " + instrumentCitation);
        }
    }

    /** A provision that exists and has been read. */
    public static Provision present(String instrumentCitation, String reference) {
        return new Provision(instrumentCitation, reference, Presence.PRESENT);
    }

    /** A positive finding that this Act has no counterpart. */
    public static Provision absent(String instrumentCitation) {
        return new Provision(instrumentCitation, null, Presence.ABSENT_IN_THIS_ACT);
    }

    /** Nobody has opened this Act for this rule. */
    public static Provision notYetVerified(String instrumentCitation) {
        return new Provision(instrumentCitation, null, Presence.NOT_YET_VERIFIED);
    }
}
