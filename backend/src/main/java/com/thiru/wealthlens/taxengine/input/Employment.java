package com.thiru.wealthlens.taxengine.input;

import java.util.List;

/**
 * One employment in the tax year, as entered ({@code DR 05} M3.2, D-090).
 *
 * <p>It holds the pay lines and nothing else yet: the period, employer type, previous-employer status and
 * revisions each arrive with the item that first reads them, so no field sits here unread.
 *
 * @param components the pay lines, in the order entered
 */
public record Employment(List<SalaryComponent> components) {

    public Employment {
        if (components == null) {
            throw new IllegalArgumentException(
                    "An employment's components may not be null; pass an empty list where there are none");
        }
        components = List.copyOf(components);
    }
}
