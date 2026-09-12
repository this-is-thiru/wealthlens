package com.thiru.wealthlens.brokercharges.dto.enums;

/**
 * The dimension a {@link ChargeBasis#SLAB} rule bands over.
 *
 * <p>Tiered pricing is not always tiered by trade size. A liquid fund's exit load tapers by holding
 * period — charged on the day of redemption and falling to nil after a week — while full-service
 * brokerage tiers band by turnover. Both are slabs; they simply band over different quantities.
 */
public enum SlabBandBasis {

    /** Bands over the rule's {@code amountBasis} value. Full-service brokerage tiers. */
    TURNOVER,

    /** Bands over days held. Graded mutual fund exit loads. */
    HOLDING_DAYS,

    /** Bands over units or shares transacted. */
    QUANTITY
}
