package com.thiru.wealthlens.taxengine.rules.definition;

/**
 * Whether the disabled employee's transport allowance falls under the Rules' "Metro Cities" or "Other Cities"
 * (S4 r.280(2) Table Sl. 10; D-102). The Rules do not define "Metro Cities" (OQ-7), so the taxpayer states which,
 * as the employer treats it; the engine defines no list.
 */
public enum CommutingCity {

    /** "Metro Cities". */
    METRO_CITY,

    /** "Other Cities". */
    OTHER_CITY
}
