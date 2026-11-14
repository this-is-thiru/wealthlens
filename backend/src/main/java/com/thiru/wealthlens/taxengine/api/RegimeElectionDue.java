package com.thiru.wealthlens.taxengine.api;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.Optional;

/**
 * A regime reached by opting out of the default, when that must be done by and what it is worth (W-114b C2a, D-667): the
 * year's pack states how and by when ({@code ElectionProtocol}); the bills are the regime comparison's, before credits.
 *
 * @param regime the regime opted into
 * @param by the deadline resolved to a date; empty where the protocol sets none of its own ({@code WithTheReturn},
 *     {@code NoDeadline})
 * @param vehicle where this taxpayer records the choice, {@code IN_THE_RETURN} or {@code PRESCRIBED_FORM}
 * @param form the prescribed form's number, where the vehicle is a form
 * @param defaultBill the default regime's bill
 * @param bill this regime's bill
 */
public record RegimeElectionDue(String regime, Optional<LocalDate> by, String vehicle, Optional<String> form,
        Money defaultBill, Money bill) {
}
