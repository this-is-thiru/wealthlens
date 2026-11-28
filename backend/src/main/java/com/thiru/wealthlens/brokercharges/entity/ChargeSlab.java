package com.thiru.wealthlens.brokercharges.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One band of a tiered charge.
 *
 * <p>The quantity being banded is named by the owning rule's {@code slabBandBasis}, so the same
 * structure expresses a brokerage tier banded by turnover and a mutual fund exit load that tapers
 * by holding period.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChargeSlab {

    /** Lower bound, inclusive. */
    private Double fromValue;

    /** Upper bound, exclusive. Null means unbounded. */
    private Double toValue;

    /** Percentage applied within this band, if the band is rate-based. */
    private Double rate;

    /** Fixed amount charged within this band, if the band is flat. */
    private Double flatAmount;
}
