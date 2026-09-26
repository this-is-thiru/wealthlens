package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * One pay line as entered for an employment ({@code DR 05} M3.2, D-090): a registry code and the year's
 * amount. What the code means — its head, cash nature, treatment — is the registry's, never the entry's.
 *
 * @param code the component, resolved against the year's registry by the partition
 * @param annualAmount the amount for the whole tax year under this employment
 */
public record SalaryComponent(ComponentCode code, Money annualAmount) {

    public SalaryComponent {
        if (code == null || annualAmount == null) {
            throw new IllegalArgumentException("A salary component needs a code and an annual amount");
        }
    }
}
