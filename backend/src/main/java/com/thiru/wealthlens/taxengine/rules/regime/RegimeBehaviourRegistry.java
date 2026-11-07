package com.thiru.wealthlens.taxengine.rules.regime;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Every {@link RegimeBehaviour} the running system knows, by id.
 *
 * <p>The registry is what makes L5's two load errors possible, and they run in opposite directions:
 *
 * <ul>
 *   <li>A pack naming a {@code BehaviourId} nobody implements would leave a regime's special
 *       behaviour silently not running — a plausible wrong answer for everyone in that regime.
 *   <li>An implementation no pack references is dead code that looks alive. It is the
 *       {@code D-ONEIMPL} reachability rule extended to behaviours, and it matters more here than
 *       elsewhere: a behaviour that stopped being referenced by a pack edit is exactly the sort of
 *       thing that is still maintained, still reviewed, and has not run in a year.
 * </ul>
 *
 * <p>Both checks live in the pack validator, which has the pack; this type answers the questions
 * they ask.
 */
public record RegimeBehaviourRegistry(Map<BehaviourId, RegimeBehaviour> behaviours) {

    public RegimeBehaviourRegistry {
        if (behaviours == null) {
            throw new IllegalArgumentException("A behaviour registry may not be null; pass an empty one");
        }
        Map<BehaviourId, RegimeBehaviour> copied = new LinkedHashMap<>(behaviours);
        for (Map.Entry<BehaviourId, RegimeBehaviour> entry : copied.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().id())) {
                throw new IllegalArgumentException(
                        "Behaviour registered under " + entry.getKey() + " calls itself "
                                + entry.getValue().id());
            }
        }
        behaviours = Map.copyOf(copied);
    }

    /** A registry built from the implementations the container found. */
    public static RegimeBehaviourRegistry of(Collection<RegimeBehaviour> implementations) {
        Map<BehaviourId, RegimeBehaviour> byId = new LinkedHashMap<>();
        for (RegimeBehaviour behaviour : implementations) {
            RegimeBehaviour existing = byId.put(behaviour.id(), behaviour);
            if (existing != null) {
                throw new IllegalArgumentException(
                        "Two behaviours both claim " + behaviour.id() + ": "
                                + existing.getClass().getName() + " and " + behaviour.getClass().getName());
            }
        }
        return new RegimeBehaviourRegistry(byId);
    }

    /** The empty registry — the state L5 recommends shipping in. */
    public static RegimeBehaviourRegistry empty() {
        return new RegimeBehaviourRegistry(Map.of());
    }

    /**
     * The behaviour for a regime.
     *
     * <p>A regime naming none gets {@link DataDrivenRegimeBehaviour}, so callers never branch on
     * absence.
     */
    public RegimeBehaviour forRegime(RegimeSpec regime) {
        return regime.behaviour()
                .map(id -> Optional.ofNullable(behaviours.get(id))
                        .orElseThrow(() -> new IllegalStateException(
                                "Regime " + regime.code() + " names behaviour " + id
                                        + ", which is not registered; the pack validator should have"
                                        + " refused this pack (invariant I9)")))
                .orElseGet(DataDrivenRegimeBehaviour::instance);
    }

    /** Whether an id resolves. */
    public boolean isRegistered(BehaviourId id) {
        return behaviours.containsKey(id);
    }

    /** Every registered id, for the reachability check. */
    public Set<BehaviourId> registeredIds() {
        return behaviours.keySet();
    }
}
