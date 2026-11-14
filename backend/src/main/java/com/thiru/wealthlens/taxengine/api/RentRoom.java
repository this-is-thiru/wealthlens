package com.thiru.wealthlens.taxengine.api;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;

/**
 * What rent the draft does not yet reflect is worth (W-115, D-670): the exemption or deduction rent could still earn,
 * the least rent that earns it, and the fall in the bill — measured by a second run with the rent filled in
 * ({@code D-MARGINAL}), so a reader sees what entering rent would change before entering it.
 *
 * @param rule the row the rent counts under: house rent allowance where it is received, else rent paid (s.134)
 * @param location the location the figure assumes where no tenancy is entered — the one that saves most; empty where
 *     the entered tenancies' own locations were used, or the row does not turn on location
 * @param counted what the run allowed under the row as the draft stands
 * @param room what rent could add to it
 * @param rentNeeded the least rent for the year that earns {@code counted} plus {@code room}
 * @param taxSaved what that rent would take off the bill
 */
public record RentRoom(String rule, Optional<String> location, Money counted, Money room, Money rentNeeded,
        Money taxSaved) {
}
