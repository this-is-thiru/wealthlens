/**
 * Tax rules as data: rule packs, the tax-year registry, cap shapes, conditions, regimes, ITR form
 * specifications and the claim catalogue.
 *
 * <p><b>This module computes nothing.</b> It loads, validates and serves the immutable, checksummed
 * rule packs that every computation reads. Keeping data and its validation apart from the arithmetic
 * is what allows a new tax year, a Budget change or a new allowance to be a data edit rather than a
 * code change.
 *
 * <p><b>Why this module is {@code OPEN} where the other three tax modules are {@code CLOSED}</b>
 * (decision D-022, blocker B-002). This module is the shared vocabulary of the tax domain: its
 * {@code Money}, identifiers, cap shapes, conditions and pack types exist precisely to be read by
 * {@code taxengine}, {@code taxdocs} and {@code taxplanning}. Every one of those types lives in a
 * sub-package, and a {@code CLOSED} module exposes only its root package, so {@code CLOSED} here
 * would mean a {@code @NamedInterface} on all twelve sub-packages and a per-package dependency list
 * in all three consumers — the same visible surface, declared twelve times over.
 *
 * <p>This does not weaken the boundary the design was bought for. {@code taxengine},
 * {@code taxdocs} and {@code taxplanning} — the three modules that have internals worth hiding —
 * remain {@code CLOSED}, so "the engine may not reach a repository" and "{@code taxplanning} may
 * not reach into engine internals" are still enforced rather than conventional. This module has no
 * internals to hide: it is data and the validation of data.
 */
@org.springframework.modulith.ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.OPEN,
    allowedDependencies = {"shared"}
)
package com.thiru.wealthlens.taxrules;
