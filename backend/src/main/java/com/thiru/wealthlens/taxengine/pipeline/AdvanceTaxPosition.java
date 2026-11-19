package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.List;

/**
 * The year's advance tax: whether it is payable, how much, and what each instalment still asks (2025 ss.403 – 406, 408;
 * 1961 ss.207 – 211; W-089c). The interest on a shortfall reads it, so the liability is decided once.
 *
 * @param liable whether advance tax is payable at all
 * @param amount the year's advance tax: the bill less tax deducted or collected at source, nil or more
 * @param instalments each instalment, in date order; empty where not liable
 */
public record AdvanceTaxPosition(boolean liable, Money amount, List<Instalment> instalments) implements NodeValue {

    public AdvanceTaxPosition {
        if (amount == null || instalments == null) {
            throw new IllegalArgumentException("An advance-tax position needs an amount and a list of instalments");
        }
        instalments = List.copyOf(instalments);
    }

    @Override
    public Money asMoney() {
        return amount;
    }

    /**
     * One instalment.
     *
     * @param dueOn the date it falls due on or before
     * @param dueByThen the advance tax due by then, counting earlier instalments
     * @param paidByThen advance tax paid on or before then
     * @param stillOwed what is due by then and not paid, nil or more
     */
    public record Instalment(LocalDate dueOn, Money dueByThen, Money paidByThen, Money stillOwed) {
    }
}
