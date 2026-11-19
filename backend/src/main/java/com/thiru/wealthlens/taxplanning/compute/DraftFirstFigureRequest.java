package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;

/**
 * Step 1's answer, with what a computation cannot run without (W-122c C1a, C4a; D-722).
 *
 * @param label the draft's label, which every draft needs
 * @param grossPay the year's gross pay, taken as one fully taxable component with nothing assumed (W-122 C2a)
 * @param dueDate the day the return is due, as the person states it: no rule holds it, since it is often extended
 */
public record DraftFirstFigureRequest(String label, Money grossPay, LocalDate dueDate) {
}
