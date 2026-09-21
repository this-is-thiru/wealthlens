package com.thiru.wealthlens.taxengine.input;

/**
 * What the taxpayer entered, as the engine will read it.
 *
 * <h2>A declared seam, and today it is empty</h2>
 *
 * <p>Decision <b>D-026</b>, and this needs stating plainly rather than being discovered later.
 * {@code DR 23} A3 gives {@code EngineInputs} a {@code DraftInputs entered} component.
 * {@code DraftInputs} ({@code 11-filing-drafts.md} F4) is {@code taxplanning}'s, so the engine may
 * not hold it (decision <b>D-024</b>) — and its own components, {@code Employment},
 * {@code HouseProperty}, {@code OtherSourceItem}, {@code DeclaredClaim}, {@code ProofOfClaim} and
 * {@code EntryProvenance}, are named in that one code block and <b>nowhere else in the design or
 * the backlog</b>. No item produces them. {@code Employment} alone — "incl. periods and revisions"
 * — is the whole per-period machinery {@code 13-edge-cases.md} R-11 describes.
 *
 * <p>A faithful projection is therefore the entire income-side domain model, which Phases 5 to 9
 * build. This record is the <b>seam it grows into</b>: each field arrives with the income item that
 * needs it, the way {@code CapShape} and {@code NodeValue} grew (rule B2).
 *
 * <h2>Why declare it empty rather than leave it out</h2>
 *
 * <p>Because the two are not the same, and the difference is a wrong number. Blocker <b>B-003</b>
 * turned on a component being <i>absent</i> from {@code EngineInputs}: the fingerprint
 * ({@code W-038}) is the cache key, so an absent component is an input the key cannot see, and two
 * computations differing only in it would collide. A component that is declared and thin is in the
 * key, and A3's own inference requires the fingerprint to be derived structurally — <i>"a field
 * added to {@code EngineInputs} is in the key automatically, which is the only way that invariant
 * survives"</i> — so every field added here enters the key without anyone remembering to add it.
 *
 * <p>So: no components yet, and that is the honest statement of what the engine can currently read
 * from what a user typed. It contributes nothing to the fingerprint today because there is nothing
 * to contribute, not because something was forgotten.
 */
public record EnteredFacts() {

    /** The only value this type can currently take. */
    public static EnteredFacts none() {
        return new EnteredFacts();
    }
}
