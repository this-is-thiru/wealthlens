package com.thiru.wealthlens.brokercharges.dto.context;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * One FIFO lot consumed by a disposal.
 *
 * <p>Charges that depend on how long a holding was held apply per lot, not per transaction. A
 * redemption spanning lots of different ages attracts a different exit load on each, and averaging
 * over the transaction can be wrong by the whole charge rather than by a rounding error.
 *
 * <p>{@code price} is the lot's acquisition price, a fact about the lot. No charge is computed from it: a per-lot
 * charge is priced on what the lot's units fetched at the sale's price (W-303, D-619 C2a).
 */
public record LotSlice(double quantity, LocalDate acquisitionDate, double price) {

    public long holdingDays(LocalDate disposalDate) {
        return ChronoUnit.DAYS.between(acquisitionDate, disposalDate);
    }
}
