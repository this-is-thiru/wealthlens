package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.List;
import java.util.Map;

/**
 * Set-off row by row, as Schedules CYLA and BFLA lay it out (W-093a Q4a, D-658): for each head, and each capital-gains
 * part, its income, what the year's house-property and other-sources losses took from it, what remained, what the
 * brought-forward losses took, and what remained after them.
 *
 * @param rows a row for each head other than capital gains, by its name, and for each capital-gains part, by its code
 * @param currentYearLoss each head's loss of the year before set-off
 * @param currentYearLossLeft each head's loss of the year not set off against another head
 */
public record SetOffRows(List<Row> rows, Map<HeadCode, Money> currentYearLoss, Map<HeadCode, Money> currentYearLossLeft) {

    /** No rows: a value built without them, as a hand-built test value is. */
    public static final SetOffRows NONE = new SetOffRows(List.of(), Map.of(), Map.of());

    /**
     * One row.
     *
     * @param row the head's name or the part's code
     * @param income its income before set-off, nil where it is a loss
     * @param housePropertyLossSetOff the year's house-property loss set off against it
     * @param otherSourcesLossSetOff the year's other-sources loss set off against it
     * @param afterCurrentYear what remained after the year's losses
     * @param broughtForwardSetOff the brought-forward losses set off against it
     * @param afterBroughtForward what remained after them
     */
    public record Row(String row, Money income, Money housePropertyLossSetOff, Money otherSourcesLossSetOff,
            Money afterCurrentYear, Money broughtForwardSetOff, Money afterBroughtForward) {
    }

    public SetOffRows {
        if (rows == null || currentYearLoss == null || currentYearLossLeft == null) {
            throw new IllegalArgumentException("Set-off rows need their rows and the year's losses, empty for none");
        }
        rows = List.copyOf(rows);
        currentYearLoss = Map.copyOf(currentYearLoss);
        currentYearLossLeft = Map.copyOf(currentYearLossLeft);
    }
}
