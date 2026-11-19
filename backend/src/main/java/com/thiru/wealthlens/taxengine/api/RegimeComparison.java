package com.thiru.wealthlens.taxengine.api;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.List;

/**
 * Each regime on the same inputs, and the one the rules choose.
 *
 * @param chosen the regime's code
 * @param mode how it was chosen
 * @param priced each candidate and its bill
 */
public record RegimeComparison(String chosen, String mode, List<Priced> priced) {

    /** One regime's bill. */
    public record Priced(String regime, Money bill) {
    }
}
