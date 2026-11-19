package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * What a draft does not yet reflect, ranked by what it is worth (PRD F33; {@code DR 20} F33; W-115, D-670): each item's
 * worth is measured, not assumed — the draft computed with the item filled in against the draft as it stands
 * ({@code D-MARGINAL}), so "is this number trustworthy yet?" is answered in rupees rather than a percentage.
 *
 * @param asOf the day it was computed on
 * @param regime the regime computed: the draft's election, or the year's default where none is made
 * @param revision the draft revision it read
 * @param top the items worth most, largest first, at most five (F33.3)
 * @param others the rest, in the same order
 * @param topWorth the worths of {@code top} added — what the items are worth "up to"; they overlap, so entering all of
 *     them need not save the sum
 * @param notIncluded what no item here measures yet
 */
public record DraftCompleteness(LocalDate asOf, String regime, int revision, List<Item> top, List<Item> others,
        Money topWorth, List<String> notIncluded) {

    public DraftCompleteness {
        top = List.copyOf(top);
        others = List.copyOf(others);
        notIncluded = List.copyOf(notIncluded);
    }

    /**
     * One thing the draft does not yet reflect.
     *
     * @param rule the row it counts under, or the rows a filling run raised, joined by " + "
     * @param direction whether entering it lowers the tax or raises it
     * @param counted what the row allows as the draft stands
     * @param room what entering it could add under the row
     * @param rentNeeded for rent, the least rent for the year that earns the room; empty otherwise
     * @param location for HRA with no home entered, the location the figure assumes; empty otherwise
     * @param worth the change in tax entering it would make, measured by a second run
     */
    public record Item(String rule, Direction direction, Money counted, Money room, Optional<Money> rentNeeded,
            Optional<String> location, Money worth) {
    }

    /** Which way an item moves the tax; F33.2 asks for both, and only {@link #REDUCES} is measured so far (W-324). */
    public enum Direction {
        /** Entering it would lower the tax. */
        REDUCES,
        /** Entering it would raise the tax — income the draft leaves out. */
        RAISES
    }
}
