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
        return List.of(BONUS, STOCK_SPLIT, RIGHTS_ISSUANCE, BUYBACK, DEMERGER);
    }
}
