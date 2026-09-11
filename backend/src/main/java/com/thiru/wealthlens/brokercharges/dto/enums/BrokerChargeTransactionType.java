package com.thiru.wealthlens.brokercharges.dto.enums;

/**
 * <b>Superseded.</b> A side and an event mixed into one enum, which is defect D5.
 *
 * <p>Replaced by {@code ChargeEvent} together with {@code ChargeSide}.
 *
 * <p><b>Do not delete yet.</b> Still reached by live code, and removal waits on human testing of
 * the charges engine — see {@code docs/charges-engine/implementation-checklist.md}, Chunk 11.
 *
 * <p>Plain {@code @Deprecated} rather than {@code forRemoval = true} on purpose. A removal warning
 * is <em>not</em> suppressed at a deprecated use site, so seventeen interlinked classes would warn
 * about each other and bury the only signal worth having. An ordinary deprecation warning is
 * suppressed inside deprecated code, which leaves the build reporting exactly the <b>live</b>
 * callers still to be migrated — and that list reaching zero is the precondition for deleting any
 * of this.
 */
@Deprecated
public enum BrokerChargeTransactionType {
    BUY,
    SELL,
    AMC_CHARGES,
    ACCOUNT_OPENING_CHARGES
}
