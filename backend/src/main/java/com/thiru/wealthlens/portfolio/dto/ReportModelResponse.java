package com.thiru.wealthlens.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * The response side of {@code ReportModel}, at every level of the hierarchy — yearly, monthly and
 * fortnightly all extend this.
 *
 * <p><b>The names must match {@code ReportModel} exactly.</b> {@code TJsonMapper.safeCopy} maps by
 * name, and they used to disagree — this class said {@code purchasePrice} / {@code sellPrice} /
 * {@code brokerCharges} where the entity says {@code purchaseAmount} / {@code sellAmount} /
 * {@code brokerage} — so all three silently read {@code 0.0} in every profit-and-loss response, at
 * all three levels, for both realised and out-sourced profits. Only {@code profit} and
 * {@code miscCharges} ever came through, which is why the endpoint reported a profit with no
 * turnover behind it.
 *
 * <p>They were renamed rather than aliased: {@code purchasePrice} was also simply the wrong word
 * for an amount aggregated over a period, and an alias would have preserved that wrong name
 * permanently in the surface a tax report reads from.
 */
@Data
public class ReportModelResponse {
    private double purchaseAmount;
    private double sellAmount;
    private double profit;
    private double brokerage;
    private double miscCharges;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastUpdatedTime;
}
