package com.thiru.wealthlens.taxengine.rules.component;

import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * When money paid into a fund can come out (A2.6 {@code liquidity}; W-238e1): the terms the governing instruments state,
 * each dated, and the periods for which no term is stated. A row with neither says nothing about liquidity, which is
 * right for pay in hand and is refused by the pack's checks for a fund or a contribution.
 *
 * @param terms the stated terms; several may apply on one day, one per event
 * @param unstated the periods without stated terms; none overlaps a term, since a day cannot be both
 */
public record Liquidity(List<WithdrawalTerm> terms, List<UnstatedTerms> unstated) {

    public static final Liquidity NONE = new Liquidity(List.of(), List.of());

    public Liquidity {
        terms = List.copyOf(terms);
        unstated = List.copyOf(unstated);
        for (UnstatedTerms gap : unstated) {
            for (WithdrawalTerm term : terms) {
                if (gap.validity().overlaps(term.validity())) {
                    throw new IllegalArgumentException("'" + term.event() + "' (" + term.validity()
                            + ") overlaps a period without stated terms (" + gap.validity() + ")");
                }
            }
        }
    }

    /**
     * The first day of {@code span} that no term and no unstated period covers, found by walking the periods in order
     * of their first day.
     */
    public Optional<LocalDate> firstUnaccountedDay(DateRange span) {
        LocalDate day = span.from();
        for (DateRange period : periods().sorted(Comparator.comparing(DateRange::from)).toList()) {
            if (!span.contains(day)) {
                return Optional.empty();
            }
            if (period.contains(day)) {
                if (period.to().isEmpty()) {
                    return Optional.empty();
                }
                day = period.to().get().plusDays(1);
            }
        }
        return span.contains(day) ? Optional.of(day) : Optional.empty();
    }

    private Stream<DateRange> periods() {
        return Stream.concat(terms.stream().map(WithdrawalTerm::validity), unstated.stream().map(UnstatedTerms::validity));
    }
}
