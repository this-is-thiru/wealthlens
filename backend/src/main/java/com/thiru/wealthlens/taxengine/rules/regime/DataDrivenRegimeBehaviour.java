package com.thiru.wealthlens.taxengine.rules.regime;

/**
 * The behaviour every regime gets unless it names another: pure data, no overrides.
 *
 * <p>Used whenever {@code RegimeSpec.behaviour()} is empty, which should be always. Its existence is
 * what lets the rest of the engine treat "has a behaviour" as universal rather than special-casing
 * the common path — the null check happens once, here, instead of at every call site.
 */
public final class DataDrivenRegimeBehaviour implements RegimeBehaviour {

    /** The id reserved for the default. No pack may bind it explicitly. */
    public static final BehaviourId ID = new BehaviourId("DATA_DRIVEN");

    private static final DataDrivenRegimeBehaviour INSTANCE = new DataDrivenRegimeBehaviour();

    private DataDrivenRegimeBehaviour() {
    }

    public static DataDrivenRegimeBehaviour instance() {
        return INSTANCE;
    }

    @Override
    public BehaviourId id() {
        return ID;
    }
}
