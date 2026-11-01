package com.thiru.wealthlens.portfolio.dto.enums;

/**
 * What an expense on buying or selling a land or building was for — the codes a property sale carries (W-088g9, D-434).
 * A fact, not a tax outcome: which amount each is in computing the gain is {@code RS-CG-CHARGE}'s rule data, by side and
 * code, so a new kind of expense is a new value here and a row there. A broker's trade has its codes from the charge
 * catalogue; no rate card bills these, so the catalogue does not hold them.
 */
public enum PropertyExpense {
    STAMP_DUTY,
    REGISTRATION,
    BROKERAGE,
    LEGAL_FEE
}
