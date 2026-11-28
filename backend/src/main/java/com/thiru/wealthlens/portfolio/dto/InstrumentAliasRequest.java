package com.thiru.wealthlens.portfolio.dto;

import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.InstrumentAliasKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * One alias the owner registers (W-440b, D-1055): the value as a broker's file writes it, and the stock code it names. The broker is
 * left out for an ISIN and named for every other kind; {@code InstrumentAliasService} refuses either the wrong way round.
 */
public record InstrumentAliasRequest(@NotNull InstrumentAliasKind kind, BrokerName brokerName, @NotBlank String value,
        @NotBlank String stockCode) {

    /** How a report and a refusal name the alias: {@code ISIN INE… -> CODE}, or with the broker between. */
    public String describe() {
        return kind + (brokerName == null ? "" : " " + brokerName) + " " + value + " -> " + stockCode;
    }
}
