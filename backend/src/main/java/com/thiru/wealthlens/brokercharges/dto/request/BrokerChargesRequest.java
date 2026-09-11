package com.thiru.wealthlens.brokercharges.dto.request;


import com.thiru.wealthlens.brokercharges.dto.enums.AmcChargeFrequency;
import com.thiru.wealthlens.brokercharges.dto.helper.BrokerageChargesDto;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <b>Superseded.</b> The write contract for a superseded rate card.
 *
 * <p>Replaced by the seed files under {@code data/charges/} and {@code POST /charge-schedules}.
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
public class BrokerChargesRequest{
    private BrokerName brokerName;
    private LocalDate startDate;
    private EntityStatus status;
    private double accountOpeningCharges;
    private double amcChargesAnnually;
    private AmcChargeFrequency amcChargesFrequency;
    private BrokerageChargesDto brokerageCharges;
    private double dpChargesPerScrip;
    private double stt;
    private double sebiCharges;
    private double stampDuty;

    /**
     * Example of gst applicable description
     * 18%-brokerage,18%-dp_charges,18%-stt,18%-amc_charges,18%-dp_charges_per_scrip
     */
    private String gstApplicableDescription;
}
