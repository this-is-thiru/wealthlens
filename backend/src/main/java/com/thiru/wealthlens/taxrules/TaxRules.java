package com.thiru.wealthlens.taxrules;

/**
 * Marker for the {@code taxrules} module root.
 *
 * <p>Exists so the package is discovered as a Spring Modulith application module before any type
 * has been added to it. It carries no behaviour and should be deleted once the module has real
 * public types.
 */
public final class TaxRules {

    private TaxRules() {
    }
}
