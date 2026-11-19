package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * One pay line as entered for an employment ({@code DR 05} M3.2, D-090): a registry code and the year's
 * amount. What the code means — its head, cash nature, treatment — is the registry's, never the entry's.
 *
 * @param code the component, resolved against the year's registry by the partition
 * <p>What an amount means is the charge's (S2 s.15; 1961 s.15; D-096): salary <b>due</b> in the tax year whether
 * paid or not, salary <b>paid</b> in the year before it fell due, and <b>arrears</b> paid in the year that no earlier
 * year charged — a former employer included. Salary paid in advance and taxed in an earlier year is not entered
 * again when it falls due, and a partner's remuneration from the firm is not salary at all. The engine cannot
 * check any of this; arrears relief (2025 s.157) is deferred, so an arrear entered here is taxed in this year.
 *
 * @param annualAmount the amount for the whole tax year under this employment, as s.15 charges it
 */
public record SalaryComponent(ComponentCode code, Money annualAmount) {

    public SalaryComponent {
        if (code == null || annualAmount == null) {
            throw new IllegalArgumentException("A salary component needs a code and an annual amount");
        }
    }
}
