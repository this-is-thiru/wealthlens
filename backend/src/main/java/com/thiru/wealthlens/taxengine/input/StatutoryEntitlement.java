package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;

/**
 * What a labour statute entitles an employee to on leaving, as the employer computed it (S2 s.19(1) Table Sl. 5, 10;
 * 1961 s.10(10)(ii), (10B); D-107, D-109).
 *
 * <p>The tax Acts limit gratuity to "the amount calculated as per the provisions of section 4(2) and (3)" of the
 * Payment of Gratuity Act, and retrenchment compensation to the amount under s.25F(b) of the Industrial Disputes Act.
 * Both Acts were repealed on 21-11-2025 and are read as the Code on Social Security s.53(2)–(3) and the Industrial
 * Relations Code s.70(b) (D-107). The sums turn on each Act's own wage — the Code's adds back allowances above
 * one-half of remuneration (s.2(88)) — on the pay basis, on "average pay", and on any days a government notifies, so
 * the employer, who must compute the amount to pay it lawfully, states it. The separation day says which Act it
 * answers to: the repealed one before 21-11-2025, the Code on or after.
 *
 * @param separatedOn the day the employment ended — retirement, termination or retrenchment
 * @param amountUnderStatute the gratuity or compensation the statute in force on that day entitles
 */
public record StatutoryEntitlement(LocalDate separatedOn, Money amountUnderStatute) {

    public StatutoryEntitlement {
        if (separatedOn == null || amountUnderStatute == null) {
            throw new IllegalArgumentException("A statutory entitlement needs the separation day and the amount");
        }
        if (amountUnderStatute.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("The amount under the statute may not be negative");
        }
    }
}
