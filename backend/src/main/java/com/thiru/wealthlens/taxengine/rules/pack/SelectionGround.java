package com.thiru.wealthlens.taxengine.rules.pack;

/**
 * What decides whether a row of a set applies, where the set has several (W-088m1, D-252).
 *
 * <p>Most sets choose by regime alone and leave a row's conditions to be tested, and traced, when it is applied. A set
 * whose rows replace one another by the person's facts — the slab ladders by age — chooses by its conditions too.
 */
public enum SelectionGround {
    REGIME,
    REGIME_AND_CONDITIONS
}
