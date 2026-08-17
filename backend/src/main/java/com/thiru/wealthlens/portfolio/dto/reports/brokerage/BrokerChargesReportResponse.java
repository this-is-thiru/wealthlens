package com.thiru.wealthlens.portfolio.dto.reports.brokerage;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.thiru.wealthlens.shared.util.time.TLocalDateTime;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class BrokerChargesReportResponse {
    private double brokerage;
    private double accountOpeningCharges;
    private double amcCharges;
    private double govtCharges;
    private double taxes;
    private double dpCharges;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TLocalDateTime.COMPLETE_DATE_TIME_FORMAT)
    private LocalDateTime lastUpdatedTime;
}
