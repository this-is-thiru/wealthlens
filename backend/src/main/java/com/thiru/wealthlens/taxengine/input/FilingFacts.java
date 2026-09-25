package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * What was filed, when, and what had already been paid.
 *
 * <p>{@code DR 23} A3. These are the facts the interest and fee provisions read — ss.423 (late
 * filing), 424 (advance tax below ninety per cent), 425 (deferment) and 428 (late fee). None of
 * them can be computed without a due date and a furnishing date, and none of them may read a clock:
 * {@code taxengine_has_no_clock} holds because both arrive here as inputs.
 *
 * <h2>Dated instalments, not a total</h2>
 *
 * <p>A3 says "advance tax paid". A single total is <b>lossy</b>, for the same reason a derived age
 * is lossy in {@link Individual}: two of the rules turn on <i>when</i> the money was paid.
 * {@code ADV-MAR31-001} ({@code 01-rulebook.md:1159}) treats any amount paid on or before 31 March
 * as advance tax for that year, and s.425 charges deferment by instalment. So the payments are
 * carried with their dates and the total is derived — a subtraction the engine can always do, from
 * a fact it could never recover.
 *
 * @param dueDate the date by which the return was due
 * @param furnishedOn the date it was actually furnished; empty where it has not been filed yet,
 *     which is the ordinary case when someone is planning rather than filing
 * @param advanceTaxPaid the payments, with their dates; may be empty, never null
 */
public record FilingFacts(
        LocalDate dueDate, Optional<LocalDate> furnishedOn, List<Payment> advanceTaxPaid) {

    /**
     * One advance-tax payment.
     *
     * @param paidOn the date of payment — {@code ADV-MAR31-001} and s.425 both read it
     * @param amount the amount paid
     */
    public record Payment(LocalDate paidOn, Money amount) {

        public Payment {
            if (paidOn == null) {
                throw new IllegalArgumentException(
                        "An advance-tax payment must carry its date; ADV-MAR31-001 and s.425"
                                + " deferment both turn on when it was paid");
            }
            if (amount == null) {
                throw new IllegalArgumentException("An advance-tax payment must carry an amount");
            }
        }
    }

    public FilingFacts {
        if (dueDate == null) {
            throw new IllegalArgumentException(
                    "FilingFacts must carry a due date; ss.423 and 428 are computed against it");
        }
        if (furnishedOn == null) {
            throw new IllegalArgumentException(
                    "FilingFacts.furnishedOn may not be null; pass Optional.empty() for a return"
                            + " not yet furnished, which is different from one furnished today");
        }
        if (advanceTaxPaid == null) {
            throw new IllegalArgumentException(
                    "FilingFacts.advanceTaxPaid may not be null; pass an empty list where nothing"
                            + " was paid");
        }
        advanceTaxPaid = List.copyOf(advanceTaxPaid);
    }

    /** A return not yet furnished, with nothing paid in advance. */
    public static FilingFacts notYetFurnished(LocalDate dueDate) {
        return new FilingFacts(dueDate, Optional.empty(), List.of());
    }

    /** Everything paid in advance, whenever paid. */
    public Money totalAdvanceTaxPaid() {
        return advanceTaxPaid.stream().map(Payment::amount).reduce(Money.ZERO, Money::plus);
    }

    /**
     * Advance tax paid on or before a date.
     *
     * <p>{@code ADV-MAR31-001}'s shape: the year's advance tax is what was paid by 31 March, not
     * what was paid at any time.
     */
    public Money advanceTaxPaidBy(LocalDate date) {
        return advanceTaxPaid.stream()
                .filter(p -> !p.paidOn().isAfter(date))
                .map(Payment::amount)
                .reduce(Money.ZERO, Money::plus);
    }

    /** Whether the return was furnished after it was due — the trigger for ss.423 and 428. */
    public boolean furnishedLate() {
        return furnishedOn.isPresent() && furnishedOn.get().isAfter(dueDate);
    }
}
