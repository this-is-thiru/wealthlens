package com.thiru.wealthlens.portfolio.holding;

/**
 * Which date a policy's validity window is measured against.
 *
 * <p>This is the distinction most easily got wrong. The 12/24-month framework introduced on
 * 23 July 2024 applies by <b>date of transfer</b> — a sale on the 22nd uses the old table and the
 * 23rd uses the new one, whatever the purchase date. The specified-mutual-fund rule applies by
 * <b>date of acquisition</b> — a fund bought on 31 March 2023 escapes it forever, however late it is
 * sold.
 *
 * <p>A policy store that applied one effective date to one date would misclassify every trade that
 * straddles a change, so each policy declares which date it is keyed on.
 */
public enum HoldingPeriodDate {
    ACQUISITION,
    TRANSFER
}
