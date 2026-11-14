package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One item of income chargeable under the head "Income from other sources", with what the assessee spent on it, as
 * entered (S2 ss.92–94; 1961 ss.56–58; {@code DR 23} A3's {@code OtherSourceItem}; D-194).
 *
 * <p>The expenses are entered as spent, not as allowed: which of them the law deducts depends on the kind and the year
 * (a banker's commission against interest on securities only, interest expense against dividends only up to a limit
 * that is nil from TY 2026-27), and the engine says so line by line rather than the entry pre-judging it.
 *
 * @param kind what the income is
 * @param received the income received or accrued in the tax year
 * @param bankerCommission commission or remuneration paid to a banker or another person to realise it
 * @param interestExpense interest paid on money borrowed to earn it
 * @param otherExpenditure other revenue expenditure laid out wholly and exclusively for making or earning it — never
 *     personal expenses (S2 s.94(1)(a); 1961 s.58(1)(a)(i))
 * @param payableOutsideIndiaUntaxed whether any of these expenses is interest or salary payable outside India on which
 *     tax has not been paid or deducted, which bars it (S2 s.94(1)(b), (c); 1961 s.58(1)(a)(ii), (iii))
 * @param receivedOn the day it was received or accrued, where entered; a dividend's is what the deferment relief turns on
 *     (2025 s.425(4); 1961 s.234C(1) first proviso; W-112a), and without it the relief cannot reach that dividend
 */
public record OtherSourceItem(
        OtherSourceKind kind,
        Money received,
        Money bankerCommission,
        Money interestExpense,
        Money otherExpenditure,
        boolean payableOutsideIndiaUntaxed,
        Optional<LocalDate> receivedOn) {

    public OtherSourceItem {
        if (receivedOn == null) {
            throw new IllegalArgumentException("An item of income from other sources needs its received date or"
                    + " Optional.empty() where none was entered");
        }
        if (kind == null) {
            throw new IllegalArgumentException("An item of income from other sources needs its kind");
        }
        for (Money figure : new Money[] {received, bankerCommission, interestExpense, otherExpenditure}) {
            if (figure == null || figure.isLessThan(Money.ZERO)) {
                throw new IllegalArgumentException("An item of income from other sources needs every figure, none"
                        + " negative; enter 0 where nil");
            }
        }
    }
}
