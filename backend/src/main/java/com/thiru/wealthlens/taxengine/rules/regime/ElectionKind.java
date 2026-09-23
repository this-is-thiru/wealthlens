package com.thiru.wealthlens.taxengine.rules.regime;

/**
 * How a taxpayer ends up in a regime.
 */
public enum ElectionKind {

    /** The regime applies unless the taxpayer does something. The new regime, since TY 2024-25. */
    DEFAULT_NO_ACTION,

    /** The taxpayer must positively choose it. */
    OPT_IN,

    /** The taxpayer must positively leave the default to reach it. */
    OPT_OUT
}
