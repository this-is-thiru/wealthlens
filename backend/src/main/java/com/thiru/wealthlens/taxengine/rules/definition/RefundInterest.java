package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.money.Rate;

/**
 * Simple interest by the month the Central Government adds to a refund (2025 s.437(1) Table Sl. 1, 2, (2); 1961
 * s.244A(1)(a), (aa) and proviso; W-112b): on a refund out of tax at source or advance tax, and on one out of tax paid on
 * the return, each from its own date to the refund's grant, and none where the refund is a small share of the tax.
 *
 * @param perMonth the interest for each month or part of a month
 * @param noInterestBelowShare the share of the tax as determined below which a refund carries no interest
 */
public record RefundInterest(Rate perMonth, Rate noInterestBelowShare) implements RuleDefinition {

    public RefundInterest {
        if (perMonth == null || perMonth.compareTo(Rate.ZERO) <= 0 || noInterestBelowShare == null) {
            throw new IllegalArgumentException("Refund interest needs a rate above nil and the share below which none is"
                    + " paid, but was " + perMonth + " and " + noInterestBelowShare);
        }
    }
}
