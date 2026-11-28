package com.thiru.wealthlens.portfolio.dto;

import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.InstrumentAliasKind;
import com.thiru.wealthlens.portfolio.entity.InstrumentAliasEntity;

/** A registered alias as the owner reads it back (W-440b). */
public record InstrumentAliasResponse(InstrumentAliasKind kind, BrokerName brokerName, String value, String stockCode) {

    public static InstrumentAliasResponse from(InstrumentAliasEntity alias) {
        return new InstrumentAliasResponse(alias.getKind(), alias.getBrokerName(), alias.getValue(), alias.getStockCode());
    }
}
