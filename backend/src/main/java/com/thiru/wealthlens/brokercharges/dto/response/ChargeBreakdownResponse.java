package com.thiru.wealthlens.brokercharges.dto.response;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.entity.ChargeLine;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A contract note as the API returns it — the same shape whether the charge was simulated or is
 * being read back from a user's history.
 *
 * <p>A deliberate translation rather than the engine's own record. {@code ChargeComputation} is an
 * internal type whose fields move as the engine does, and a published API should not move with it.
 * It also carries {@code scheduleId}, which is a Mongo identifier of no use to a caller; the
 * {@code scheduleCode} beside it is the thing a human can look up.
 *
 * <p>{@code resolution} is always populated, including when nothing was charged. A zero total with
 * {@code NO_SCHEDULE} is a gap in the rate cards; a zero total with {@code CORPORATE_ACTION_EXEMPT}
 * is correct. Returning an empty list without saying which would leave the caller to guess.
 */
@Data
@NoArgsConstructor
@ToString
public class ChargeBreakdownResponse {

    private String scheduleCode;

    private String instrumentId;

    private ChargeResolution resolution;

    private List<ChargeLine> lines;

    /** Denormalised from {@code lines}, in evaluation order, so a breakdown reads naturally. */
    private Map<String, Double> amountByCode;

    private double totalCharges;

    public static ChargeBreakdownResponse from(ChargeComputation computation) {
        ChargeBreakdownResponse response = new ChargeBreakdownResponse();
        response.scheduleCode = computation.scheduleCode();
        response.instrumentId = computation.instrumentId();
        response.resolution = computation.resolution();
        response.lines = computation.lines();
        response.amountByCode = computation.amountByCode();
        response.totalCharges = computation.total();
        return response;
    }

    public static ChargeBreakdownResponse from(UserChargeEntity charge) {
        ChargeBreakdownResponse response = new ChargeBreakdownResponse();
        response.scheduleCode = charge.getScheduleCode();
        response.instrumentId = charge.getInstrumentId();
        response.resolution = charge.getResolution();
        response.lines = charge.getLines();
        response.amountByCode = charge.getAmountByCode();
        response.totalCharges = charge.getTotalCharges();
        return response;
    }
}
