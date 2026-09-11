package com.thiru.wealthlens.portfolio.entity.model;

import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * <b>Superseded.</b> Six fixed charge columns in the P&L report.
 *
 * <p>Replaced by {@code ChargeSummaryReport}, which is keyed by charge code.
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
public class BrokerChargesReport {
    @Field("brokerage")
    private double brokerage;

    @Field("account_opening_charges")
    private double accountOpeningCharges;

    @Field("amc_charges")
    private double amcCharges;

    @Field("govt_charges")
    private double govtCharges;

    @Field("taxes")
    private double taxes;

    @Field("dp_charges")
    private double dpCharges;

    @Field(name = "last_updated_time")
    @LastModifiedDate
    private LocalDateTime lastUpdatedTime;
}
