package com.thiru.wealthlens.taxengine.input;

/**
 * How a journey on leave was made, as the Rules classify it — the four clauses of S4 r.278(1) and Rules 1962
 * r.2B(1), each admitting a different fare (D-100). A closed set because the Rules' classification is closed; which
 * fare each admits is the rule's, not this type's.
 */
public enum JourneyMode {

    /** By air — r.278(1)(i). */
    AIR,

    /** Between places connected by rail, by any mode other than air — r.278(1)(ii). */
    RAIL_CONNECTED,

    /** Between places not connected by rail, where a recognised public transport system exists — r.278(1)(iii)(A). */
    NOT_RAIL_CONNECTED_WITH_PUBLIC_TRANSPORT,

    /** Between places not connected by rail, with no recognised public transport system — r.278(1)(iii)(B). */
    NOT_RAIL_CONNECTED_WITHOUT_PUBLIC_TRANSPORT
}
