package com.thiru.wealthlens.brokercharges.dto.helper;


import com.thiru.wealthlens.brokercharges.dto.enums.BrokerageAggregatorType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <b>Superseded.</b> The brokerage block of a superseded rate card.
 *
 * <p>Replaced by a {@code ChargeRule} with {@code basis = TURNOVER}.
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
@AllArgsConstructor
@NoArgsConstructor
public class BrokerageChargesDto {
    private double brokerage;
    private double brokerageCharges;
    private BrokerageAggregatorType brokerageAggregator;
    private double minimumBrokerage;
    private double maximumBrokerage;
}
