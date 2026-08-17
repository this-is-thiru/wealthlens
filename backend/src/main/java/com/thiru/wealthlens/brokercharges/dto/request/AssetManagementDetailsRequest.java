package com.thiru.wealthlens.brokercharges.dto.request;

import com.thiru.wealthlens.brokercharges.dto.enums.AmcChargeFrequency;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import java.time.LocalDate;
import lombok.Data;

@Data
public class AssetManagementDetailsRequest {
    private String email;
    private String dematAccountId;
    private BrokerName brokerName;
    private double accountOpeningCharges;
    private double taxOnAccountOpeningCharges;
    private LocalDate lastAmcChargesDeductedOn;
    private AmcChargeFrequency amcChargesFrequency;
}
