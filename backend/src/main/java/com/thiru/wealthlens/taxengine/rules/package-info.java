/**
 * The tax domain's vocabulary and the law as data: money and rates, identifiers, rule packs and the
 * tax-year registry, cap shapes, conditions, regimes, components and pack validation.
 *
 * <p><b>The arithmetic of a single rule lives here too.</b> A cap shape such as {@code SlabLadder} or
 * {@code LeastOf} computes the value one rule states. What this package does <i>not</i> do is run a
 * computation: ordering nodes, carrying values between them and producing the trace belong to the
 * rest of {@code taxengine}, which may read this package and never the reverse
 * ({@code ArchitectureTest.rules_do_not_depend_on_the_runner}).
 *
 * <p>Until decision D-048 this was its own module, {@code taxrules}. Three of the first four build
 * blockers came from that boundary rather than from tax logic, so it is now a package inside the
 * pure {@code taxengine} module, and the one direction the module used to enforce is an ArchUnit rule.
 */
package com.thiru.wealthlens.taxengine.rules;
