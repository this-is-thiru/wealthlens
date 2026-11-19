package com.thiru.wealthlens.corporate.dto.enums;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

public enum CorporateActionType {
    DIVIDEND,
    BONUS,
    STOCK_SPLIT,
    BUYBACK,
    RIGHTS_ISSUANCE,
    DEMERGER,
    NAME_OR_SYMBOL_CHANGE;

    @JsonIgnore
    public static final List<CorporateActionType> FILTERABLE_CORPORATE_ACTIONS = filterableCorporateActions();

    @JsonIgnore
    private static List<CorporateActionType> filterableCorporateActions() {
        // Only actions perform can apply hold a trade (W-316): a rights issue or buyback is recorded, not applied, so holding
        // a trade for one held it for good and stopped the quarter's run at it
        return List.of(BONUS, STOCK_SPLIT, DEMERGER);
    }
}
