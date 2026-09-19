package com.thiru.wealthlens.taxrules.registry;

import com.thiru.wealthlens.taxrules.id.TaxYearId;
import java.util.Collection;
import java.util.TreeSet;

/**
 * A computation was requested for a year the registry does not carry.
 *
 * <p>Names the years that <b>are</b> supported, because the useful answer to &ldquo;2031-32 is not
 * supported&rdquo; is which years are — golden case T9-2. A bare refusal leaves the caller guessing
 * whether the year is too old, too new, or misspelt.
 */
public class UnsupportedTaxYearException extends RuntimeException {

    public UnsupportedTaxYearException(TaxYearId requested, Collection<TaxYearId> supported) {
        super("Tax year " + (requested == null ? "null" : requested.label())
                + " is not supported. Supported years: "
                + new TreeSet<>(supported.stream().map(TaxYearId::label).toList()));
    }
}
