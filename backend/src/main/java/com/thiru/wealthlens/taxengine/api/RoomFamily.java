package com.thiru.wealthlens.taxengine.api;

/**
 * The rooms completeness measures, by what fills them (W-122b C3a, D-720): what a coarse figure's band fills at once
 * while the step that would enter them is open.
 */
public enum RoomFamily {
    /** The rent room: house rent allowance, or rent paid where none is received. */
    RENT,
    /** The Chapter VIII rooms: the investment ceiling, health cover for self and for parents, and own NPS. */
    DEDUCTIONS
}
