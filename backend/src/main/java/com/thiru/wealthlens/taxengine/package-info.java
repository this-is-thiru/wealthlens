/**
 * The tax computation: the law as data ({@code rules}) and the engine that evaluates it — the node
 * graph, the trace, and the results.
 *
 * <p><b>This module is pure.</b> It fetches nothing and reads no clock — everything it may read
 * arrives in {@code EngineInputs}, and the instant of evaluation is an explicit input. That is what
 * makes a computation reproducible years later, and what keeps thousands of optimiser evaluations
 * affordable in a single request. ArchUnit enforces it ({@code taxengine_fetches_nothing},
 * {@code taxengine_has_no_clock}).
 *
 * <p><b>Closed, with named interfaces.</b> Other modules see only the packages this module declares
 * with {@code @NamedInterface} — today {@code input} and {@code result}. Everything else, notably the
 * {@code pipeline} and the nodes, is internal. A package is exposed by the item that first needs it
 * from outside (decision D-048).
 */
@org.springframework.modulith.ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    allowedDependencies = {"shared"}
)
package com.thiru.wealthlens.taxengine;
