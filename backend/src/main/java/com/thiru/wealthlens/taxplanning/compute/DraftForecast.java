package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxengine.api.ComputedTax;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Whether a draft ends in a refund after filing or an amount payable now, and the three figures it is the difference of
 * (PRD F29; {@code DR 20} F29; W-113, D-664). {@code liability − byEmployer − otherCredits + rounding = net}, where
 * {@code rounding} is s.516 / s.288B's to the nearest ₹10 and {@code net} is the computation's final amount.
 *
 * @param liability the bill with the interest and fees owed on it, unrounded
 * @param byEmployer the part of tax at source the person states the employer deducts for the full year; empty where not
 *     stated, and then all tax at source is in {@code otherCredits}
 * @param otherCredits every other credit: the rest of tax at source, advance tax and self-assessment tax
 * @param rounding what rounding the final amount to the nearest ₹10 added or took away
 * @param net above nil payable, below nil refundable
 * @param settlement which of the two, or neither
 * @param payableBy where payable, the earliest advance-tax instalment on or after {@code asOf} with tax still owed, or else
 *     the return's due date; empty otherwise
 * @param interestOnRefund the interest the Government adds to a refund, never in {@code net} (W-112b)
 * @param regime the regime computed: the draft's election, or the year's default where none is made
 * @param asOf the day it was computed on
 * @param revision the draft revision it read
 * @param notIncluded what no figure here includes yet
 */
public record DraftForecast(Money liability, Optional<Money> byEmployer, Money otherCredits, Money rounding, Money net,
        Settlement settlement, Optional<LocalDate> payableBy, List<ComputedTax.Owed> interestOnRefund, String regime,
        LocalDate asOf, int revision, List<String> notIncluded) {

    public DraftForecast {
        interestOnRefund = List.copyOf(interestOnRefund);
        notIncluded = List.copyOf(notIncluded);
    }

    /** How the year settles, as the draft stands. */
    public enum Settlement {
        /** More is owed than has been or will be credited: pay it, by {@code payableBy}. */
        PAYABLE_NOW,
        /** More is credited than is owed: the difference comes back once the return is furnished. */
        REFUND_AFTER_FILING,
        /** Nothing either way. */
        NIL
    }
}
