package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * Where a coarse figure's bill lies (PRD F34.2; W-122b C3a, D-720): measured both ends, never estimated. Income not yet
 * entered is outside it.
 *
 * @param upper the bill as the draft stands
 * @param lower the bill with every room of the open steps filled at once
 * @param says that the figure is coarse, in the year's words
 */
public record Band(Money upper, Money lower, String says) {
}
