/**
 * The tax computation itself: the node graph, the trace, money and rates, marginal reliefs, the
 * deduction aggregator, interest and fees, and ITR form selection.
 *
 * <p><b>This module is pure.</b> It fetches nothing and reads no clock — everything it may read
 * arrives in {@code EngineInputs}, and the instant of evaluation is an explicit input. That is what
 * makes a computation reproducible years later, and what keeps thousands of optimiser evaluations
 * affordable in a single request.
 *
 * <p>It may not depend on any repository, on Spring Data, or on {@code taxplanning}. ArchUnit
 * enforces this; the module boundary is the reason the enforcement is possible.
 */
@org.springframework.modulith.ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    allowedDependencies = {"shared", "taxrules"}
)
package com.thiru.wealthlens.taxengine;
