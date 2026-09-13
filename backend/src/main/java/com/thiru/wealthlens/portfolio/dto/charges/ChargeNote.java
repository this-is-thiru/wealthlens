package com.thiru.wealthlens.portfolio.dto.charges;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.entity.ChargeLine;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Charges attached to a response, at the most detail that response can honestly carry.
 *
 * <p><b>Why {@code verbatim} exists.</b> A full contract note — the rate, the base it was applied
 * to, and whether it was taxable — is only true of a single charge row read back unchanged. Two
 * other shapes reach a caller, and both would be a lie dressed as a contract note:
 *
 * <ul>
 *   <li><b>Allocated</b>: a sell's charge split across the FIFO lots it consumed. The split is
 *       exact per code, but no single line's {@code baseAmount} belongs to one lot.
 *   <li><b>Merged</b>: several lots grouped into one portfolio row. The lines describe different
 *       trades and cannot be concatenated into one note.
 * </ul>
 *
 * <p>In both, {@code lines} is null and {@code byCode} carries the whole truth. Scaling the lines
 * instead would re-introduce the independent rounding that {@code TradeOutcomeRecorder.allocate}
 * was written to avoid — parts that no longer sum to the amount actually charged.
 */
@Data
@NoArgsConstructor
@ToString
public class ChargeNote {

    /** Whether {@code lines} is a contract note read back unchanged, rather than a total. */
    private boolean verbatim;

    /** Why these charges, or why none. Only meaningful when a single row is the source. */
    private ChargeResolution resolution;

    /** The contract note, in evaluation order. Null unless {@code verbatim}. */
    private List<ChargeLine> lines;

    /** Charge code to amount. Always populated, and always sums to {@code total}. */
    private Map<String, Double> byCode = new LinkedHashMap<>();

    private double total;

    /** How many charge rows contributed. One, for a verbatim note. */
    private int sourceCount;
}
