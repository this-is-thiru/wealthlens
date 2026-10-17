package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * A journey on leave, as entered for the leave travel concession (S4 r.278; Rules 1962 r.2B; D-100).
 *
 * <p>The Rules cap each journey at the fare they admit for its mode — "the air-conditioned first-class rail fare by
 * the shortest route" and the like — which is a figure the engine cannot know, so the taxpayer states it. For the
 * one case where the Rules state a rate rather than a fare (r.278(1)(iii)(B), per kilometre) the distance is entered
 * instead. The date places the journey in its four-calendar-year block.
 *
 * @param startedOn when the journey began; its calendar year places it in a block
 * @param mode which clause of the Rules the journey falls under
 * @param amountIncurred what was actually spent on the travel
 * @param fareAdmissible the fare the Rules admit for this mode, as the taxpayer states it
 * @param distanceKm the distance by the shortest route, where the Rules price the journey per kilometre
 * @param travellers each person's part of the amount and the fare, where the two-children limit needs them (D-212);
 *     empty where the journey is not split, else adding up to the journey's own figures
 */
public record Journey(
        LocalDate startedOn,
        JourneyMode mode,
        Money amountIncurred,
        Optional<Money> fareAdmissible,
        Optional<Integer> distanceKm,
        List<JourneyTraveller> travellers) {

    public Journey {
        if (startedOn == null || mode == null || amountIncurred == null || fareAdmissible == null
                || distanceKm == null || travellers == null) {
            throw new IllegalArgumentException("A journey needs its date, mode and amount; pass Optional.empty() for"
                    + " a fare or distance not given, and an empty list where it is not split by traveller");
        }
        if (fareAdmissible.isEmpty() && distanceKm.isEmpty()) {
            throw new IllegalArgumentException("A journey needs the fare the Rules admit for it, or its distance where"
                    + " the Rules price it per kilometre");
        }
        if (amountIncurred.isLessThan(Money.ZERO) || fareAdmissible.filter(fare -> fare.isLessThan(Money.ZERO))
                .isPresent()) {
            throw new IllegalArgumentException("A journey's amount incurred and fare may not be negative");
        }
        if (distanceKm.filter(km -> km <= 0).isPresent()) {
            throw new IllegalArgumentException("A journey's distance must be more than zero, but was "
                    + distanceKm.get());
        }
        travellers = List.copyOf(travellers);
        requireTravellersAddUp(startedOn, amountIncurred, fareAdmissible, travellers);
    }

    /** A split journey's parts are the journey: their amounts and their fares add up to its own. */
    private static void requireTravellersAddUp(LocalDate startedOn, Money amountIncurred,
            Optional<Money> fareAdmissible, List<JourneyTraveller> travellers) {
        if (travellers.isEmpty()) {
            return;
        }
        Money spent = travellers.stream().map(JourneyTraveller::amountIncurred).reduce(Money.ZERO, Money::plus);
        if (!spent.equals(amountIncurred)) {
            throw new IllegalArgumentException("The travellers on the journey of " + startedOn + " spent " + spent
                    + " between them, not the journey's " + amountIncurred);
        }
        if (travellers.stream().anyMatch(traveller -> traveller.fareAdmissible().isPresent() != fareAdmissible
                .isPresent())) {
            throw new IllegalArgumentException("Every traveller on the journey of " + startedOn + " states a fare"
                    + " exactly where the journey does");
        }
        Money fares = travellers.stream().map(traveller -> traveller.fareAdmissible().orElse(Money.ZERO))
                .reduce(Money.ZERO, Money::plus);
        if (fareAdmissible.isPresent() && !fares.equals(fareAdmissible.get())) {
            throw new IllegalArgumentException("The travellers' fares on the journey of " + startedOn + " add up to "
                    + fares + ", not the journey's " + fareAdmissible.get());
        }
    }
}
