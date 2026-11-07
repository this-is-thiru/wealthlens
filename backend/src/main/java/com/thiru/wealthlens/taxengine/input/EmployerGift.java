package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * A gift, voucher or token in lieu of a gift an employer gave the employee or a member of the household, on a
 * ceremonial occasion or otherwise (S4 r.15(5) Table IV Sl. 4; Rules 1962 r.3(7)(iv); D-117).
 *
 * @param employment which entered employment gave it, by position — gifts are aggregated per employer (D-117 C4a)
 * @param amount its value
 */
public record EmployerGift(int employment, Money amount) {

    public EmployerGift {
        if (amount == null || amount.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("A gift needs a value of nil or more");
        }
        if (employment < 0) {
            throw new IllegalArgumentException("An employment position may not be negative, but was " + employment);
        }
    }
}
