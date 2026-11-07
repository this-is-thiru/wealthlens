package com.thiru.wealthlens.taxengine.rules.relief;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Surcharge marginal relief, W = U + V: tax and surcharge may exceed the tax and surcharge at the
 * threshold (U) by no more than the income above it (V) ({@code DR 06} G3.2). The Finance Act states it
 * four times with four ladders; the thresholds are the parameter, this is the shape.
 *
 * @param thresholds the incomes at which a higher surcharge starts, in any order
 */
public record ThresholdLadderCeiling(List<Money> thresholds) implements ReliefShape {

    public ThresholdLadderCeiling {
        if (thresholds == null || thresholds.isEmpty()) {
            throw new IllegalArgumentException("ThresholdLadderCeiling needs at least one threshold");
        }
        thresholds = List.copyOf(thresholds);
    }

    @Override
    public ReliefResult apply(ReliefContext ctx) {
        Optional<Money> threshold = thresholds.stream()
                .filter(candidate -> ctx.totalIncome().isGreaterThan(candidate))
                .max(Comparator.naturalOrder());
        if (threshold.isEmpty()) {
            return new ReliefResult(Money.ZERO, "total income exceeds no surcharge threshold");
        }
        UnrelievedTax unrelieved = ctx.unrelievedAt().orElseThrow(() -> new IllegalStateException(
                "ThresholdLadderCeiling needs an UnrelievedTax to price U at " + threshold.get()
                        + "; only the caller can re-enter the computation"));
        Money ceiling = unrelieved.at(threshold.get()).plus(ctx.totalIncome().minus(threshold.get()));
        Money amount = ctx.taxBeforeRelief().minus(ceiling).floorAtZero();
        return new ReliefResult(amount, "marginal relief at " + threshold.get());
    }
}
