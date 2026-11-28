package com.thiru.wealthlens.portfolio.dto.enums;

/**
 * What a broker's file writes to name an instrument (W-440b, D-1055), each read from the owner's sample exports: an ISIN is the
 * instrument's everywhere (Zerodha's tradebooks); a scrip code (Upstox's "Scrip Code") and a broker symbol (Fyers' and Upstox's names)
 * are the broker's own, so they are registered per broker.
 */
public enum InstrumentAliasKind {
    ISIN,
    SCRIP_CODE,
    BROKER_SYMBOL;

    public boolean isBrokersOwn() {
        return this != ISIN;
    }
}
