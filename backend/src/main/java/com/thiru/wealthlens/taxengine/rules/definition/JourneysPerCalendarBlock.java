package com.thiru.wealthlens.taxengine.rules.definition;

/**
 * How many journeys on leave are exempt in each block of calendar years, and where the blocks start (S4 r.278(2);
 * Rules 1962 r.2B(2); D-100).
 *
 * <p>A count, not an amount, so no cap shape carries it. The blocks are of calendar years, not tax years, so a tax
 * year straddles two ({@code DR 13} R-12); both Rules put the boundaries in the same place — r.2B counts from 1986
 * and r.278 from 2022, four years apart. The carry-over (r.278(3)–(4)) is what the block means and is applied with
 * it: a journey first taken in a block's first calendar year, after an unused previous block, is exempt and not
 * counted.
 *
 * @param journeys how many are exempt in a block
 * @param calendarYears how long a block is
 * @param blocksCommence the first calendar year of some block; every block starts a whole number of blocks from it
 */
public record JourneysPerCalendarBlock(int journeys, int calendarYears, int blocksCommence) implements RuleDefinition {

    public JourneysPerCalendarBlock {
        if (journeys < 1 || calendarYears < 1) {
            throw new IllegalArgumentException("A block needs at least one journey and one calendar year, but was "
                    + journeys + " journeys in " + calendarYears + " years");
        }
    }

    /** The first calendar year of the block {@code calendarYear} falls in. */
    public int blockStarting(int calendarYear) {
        return blocksCommence + Math.floorDiv(calendarYear - blocksCommence, calendarYears) * calendarYears;
    }
}
