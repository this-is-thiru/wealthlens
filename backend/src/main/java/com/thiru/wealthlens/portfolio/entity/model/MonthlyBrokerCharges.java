package com.thiru.wealthlens.portfolio.entity.model;

import java.time.Month;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * <b>Superseded.</b> A month of superseded charge columns.
 *
 * <p>Replaced by {@code MonthlyChargeSummary}.
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
@Data
@EqualsAndHashCode(callSuper = true)
public class MonthlyBrokerCharges extends BrokerChargesReport {
    private Month month;

    @Field("first_half_broker_charges")
    private BrokerChargesReport firstHalfBrokerCharges;

    @Field("second_half_broker_charges")
    private BrokerChargesReport secondHalfBrokerCharges;

    public MonthlyBrokerCharges(Month month) {
        this.month = month;
    }
}
