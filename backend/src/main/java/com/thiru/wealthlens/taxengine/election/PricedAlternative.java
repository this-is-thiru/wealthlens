package com.thiru.wealthlens.taxengine.election;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * One choice of an election and the net payable it gives ({@code DR 15} C2: elections are priced by recomputation,
 * never by a heuristic).
 *
 * @param choice what was chosen
 * @param netPayable the tax payable the computation gives with that choice, before rounding ({@code tax.netpayable})
 * @param <T> what is chosen
 */
public record PricedAlternative<T>(T choice, Money netPayable) {

    public PricedAlternative {
        if (choice == null || netPayable == null) {
            throw new IllegalArgumentException("A priced alternative needs its choice and its net payable");
        }
    }
}
