package com.thiru.wealthlens.portfolio.dto.enums;

public enum AssetType {
    EQUITY,
    MUTUAL_FUND,
    BOND,
    GOLD_BOND,
    FD,
    INSURANCE,
    LAND_OR_BUILDING;

    /**
     * Why a {@link #LAND_OR_BUILDING} is refused on the broker trade paths (W-088g5, D-433): no rate card prices a
     * plot, so the charge engine records nothing for it, a V2 buy's entered charges are replaced by that nothing, and
     * the capital-gains summary reads only coded charges — its purchase expenses could never reach its cost. One
     * sentence for the JSON paths and the upload, so the two cannot drift.
     */
    public static final String NOT_A_BROKER_TRADE = "A LAND_OR_BUILDING cannot be recorded as a broker trade: no"
            + " broker card prices it, so its stamp duty, registration and brokerage would be lost from its cost; enter its sale at"
            + " POST /properties/user/{email}/sales";
}
