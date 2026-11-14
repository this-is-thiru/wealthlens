package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;
import java.util.Set;

/**
 * The monthly limit on a disabled employee's commuting allowance, and who qualifies (S4 r.280(2) Table Sl. 10;
 * Rules 1962 r.2BB(2) Table Sl. 11; D-102).
 *
 * <p>Not a cap shape: the limit depends on a condition and, from TY 2026-27, on the city class and on dearness
 * allowance paid "thereon" — facts about the claim rather than amounts a cap reads. TY 2025-26 is one flat amount
 * for three conditions; TY 2026-27 is a metro and an other-cities amount, "plus dearness allowance thereon", for
 * four.
 *
 * @param conditions the disabilities that qualify in this version
 * @param perMonth the monthly amount — the flat one, or the metro cities' where the amount depends on the city
 * @param perMonthInOtherCities the other cities' monthly amount, where the amount depends on the city
 * @param plusDearnessAllowanceThereon whether dearness allowance paid on the allowance is added to the limit
 */
public record DisabledTransportLimit(
        Set<TransportDisability> conditions,
        Money perMonth,
        Optional<Money> perMonthInOtherCities,
        boolean plusDearnessAllowanceThereon) implements RuleDefinition {

    public DisabledTransportLimit {
        if (conditions == null || conditions.isEmpty() || perMonth == null || perMonthInOtherCities == null) {
            throw new IllegalArgumentException("A transport limit needs its conditions and monthly amount; pass"
                    + " Optional.empty() where the amount does not depend on the city");
        }
        conditions = Set.copyOf(conditions);
    }

    /** Whether this version's list of disabilities includes {@code condition}. */
    public boolean admits(TransportDisability condition) {
        return conditions.contains(condition);
    }

    /**
     * The monthly limit for a claim. A fact this version reads and the claim lacks is refused, and so is a fact it
     * does not read and the claim states — ignoring it would give a figure the taxpayer did not ask for.
     */
    public Money monthlyLimit(Optional<CommutingCity> city, Optional<Money> dearnessAllowanceThereon) {
        if (perMonthInOtherCities.isPresent() && city.isEmpty()) {
            throw new IllegalArgumentException("This year's limit depends on whether the place is a metro city;"
                    + " state it");
        }
        if (perMonthInOtherCities.isEmpty() && city.isPresent()) {
            throw new IllegalArgumentException("This year's limit is the same in every city; the city stated would"
                    + " be ignored, so it is refused");
        }
        if (!plusDearnessAllowanceThereon && dearnessAllowanceThereon.isPresent()) {
            throw new IllegalArgumentException("This year's limit adds no dearness allowance; the amount stated would"
                    + " be ignored, so it is refused");
        }
        Money base = city.filter(which -> which == CommutingCity.OTHER_CITY)
                .flatMap(other -> perMonthInOtherCities)
                .orElse(perMonth);
        return base.plus(dearnessAllowanceThereon.orElse(Money.ZERO));
    }
}
