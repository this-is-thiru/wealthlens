package com.thiru.wealthlens.taxengine.rules.definition;

/**
 * What a row of {@code RS-CG-NOT-MODELLED} says of a provision the engine does not build yet: what it is about, and
 * whether applying it could raise the tax or only lower it (W-088g4, D-432).
 *
 * <p>The direction is the law's — a deemed consideration can only add to a gain, a cost of improvement or a higher cost
 * of acquisition can only take from it — so it is data; how strongly a notice speaks for it is the node's. Building the
 * provision deletes its row.
 *
 * @param subject what the provision does, in words a taxpayer reads, without figures
 * @param effect whether applying it could raise the tax or only lower it
 */
public record NotModelledProvision(String subject, TaxEffect effect) implements RuleDefinition {

    public NotModelledProvision {
        if (subject == null || subject.isBlank() || effect == null) {
            throw new IllegalArgumentException("A provision not modelled needs what it is about and its effect");
        }
    }

    /** Which way the provision, once applied, could move the tax. */
    public enum TaxEffect {
        /** It could raise the tax, so a figure computed without it may be too low. */
        MAY_RAISE,
        /** It could only lower the tax, so a figure computed without it is at most too high. */
        MAY_LOWER
    }
}
