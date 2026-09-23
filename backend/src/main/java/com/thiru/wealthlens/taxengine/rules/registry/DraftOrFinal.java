package com.thiru.wealthlens.taxengine.rules.registry;

/**
 * Whether a source was the draft or the final text.
 *
 * <p>Rules are published in draft and amended before notification, and a value taken from a draft
 * may never have come into force. Recording which was read is the difference between a citation and
 * a guess.
 */
public enum DraftOrFinal {
    DRAFT,
    FINAL,

    /** The source was opened but does not say which it is. Honest, and not the same as FINAL. */
    UNKNOWN
}
