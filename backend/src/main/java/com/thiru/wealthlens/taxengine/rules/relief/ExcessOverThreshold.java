package com.thiru.wealthlens.taxengine.rules.relief;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * Relief that keeps the tax from exceeding the income above a threshold: "the amount by which the
 * income-tax payable on such total income is in excess of the amount by which the total income exceeds"
 * the threshold (S2 s.156(2)(b)). Nothing where the income is not above the threshold.
 */
public record ExcessOverThreshold(Money threshold) implements ReliefShape {

    public ExcessOverThreshold {
        if (threshold == null) {
            throw new IllegalArgumentException("ExcessOverThreshold needs a threshold");
        }
    }

    @Override
    public ReliefResult apply(ReliefContext ctx) {
        if (!ctx.totalIncome().isGreaterThan(threshold)) {
            return new ReliefResult(Money.ZERO, "total income does not exceed " + threshold);
        }
        Money excess = ctx.totalIncome().minus(threshold);
        Money amount = ctx.taxBeforeRelief().minus(excess).floorAtZero();
        return new ReliefResult(amount, "tax in excess of the " + excess + " by which income exceeds " + threshold);
    }
}
