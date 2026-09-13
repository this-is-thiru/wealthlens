package com.thiru.wealthlens.portfolio.dto.charges;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * What a holding has cost in charges, on the way in and on the way out.
 *
 * <p>The two sides are kept apart rather than lumped, because they answer different questions and
 * are exact to different depths. {@code buy} is what was paid to acquire the lot, and for a single
 * unsold lot it is a contract note read back unchanged. {@code sell} is this lot's share of the
 * charges on the disposals that consumed it — already pro-rated at the time of the sell and stored
 * in {@code trade_outcomes}, never re-derived here.
 *
 * <p>A holding never sold carries a {@code sell} of null rather than a zero, for the same reason
 * the whole object is null when nothing was priced: an absent charge and a charge of nothing are
 * different facts.
 */
@Data
@NoArgsConstructor
@ToString
public class AssetCharges {

    /** Charges on acquiring the lot. Null when the buy predates the engine. */
    private ChargeNote buy;

    /** This lot's allocated share of the sells that consumed it. Null while it is unsold. */
    private ChargeNote sell;

    /**
     * The part of the sell charges that reduces the taxable gain, as resolved against the
     * catalogue when the sell was recorded. Notably excludes STT.
     */
    private double deductibleSellCharges;

    /**
     * How much of the user-entered buy charge has already been handed to a disposal.
     *
     * <p>Carried because {@code brokerCharges} on the lot is what was paid, not what remains
     * attributable to the quantity still held — a half-sold lot has already spent half of it.
     */
    private double allocatedOutBuyCharges;

    /** {@code buy} and {@code sell} together: every charge this holding has attracted. */
    private double total;
}
