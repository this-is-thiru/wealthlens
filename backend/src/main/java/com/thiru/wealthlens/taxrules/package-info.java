/**
 * Tax rules as data: rule packs, the tax-year registry, cap shapes, conditions, regimes, ITR form
 * specifications and the claim catalogue.
 *
 * <p><b>This module computes nothing.</b> It loads, validates and serves the immutable, checksummed
 * rule packs that every computation reads. Keeping data and its validation apart from the arithmetic
 * is what allows a new tax year, a Budget change or a new allowance to be a data edit rather than a
 * code change.
 */
@org.springframework.modulith.ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    allowedDependencies = {"shared"}
)
package com.thiru.wealthlens.taxrules;
