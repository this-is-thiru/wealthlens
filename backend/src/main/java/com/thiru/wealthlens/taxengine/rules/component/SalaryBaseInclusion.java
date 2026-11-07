package com.thiru.wealthlens.taxengine.rules.component;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;

/**
 * That a component counts in one salary base, and on what footing (D-092).
 *
 * <p>Each base's statute says "salary includes dearness allowance, if the terms of employment so provide"
 * (S2 s.19(2)(b)); basic counts outright. Which components are which is the registry's, so the engine
 * reads this flag rather than recognising a code.
 *
 * @param rule the salary-base rule the component counts in
 * @param onlyWhereTermsProvide true where it counts only if the employment's terms provide for it
 */
public record SalaryBaseInclusion(RuleId rule, boolean onlyWhereTermsProvide) {

    public SalaryBaseInclusion {
        if (rule == null) {
            throw new IllegalArgumentException("A salary-base inclusion needs its rule");
        }
    }
}
