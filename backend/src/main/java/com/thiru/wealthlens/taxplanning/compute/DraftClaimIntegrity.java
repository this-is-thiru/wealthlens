package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * For each claim in a draft that the person's evidence bears on, the amount the law allows on the facts, the amount the
 * documents held support today, and what would close the difference (PRD F23; {@code DR 12} C2, C7; W-119c): rupees the
 * engine computed, never a judgement of risk. Computed on every request and stored nowhere.
 *
 * @param asOf the day it was computed on
 * @param regime the regime computed: the draft's election, or the year's default where none is made
 * @param revision the draft revision it read
 * @param rows each claim evidence bears on, by rule id
 * @param notIncluded what these rows do not yet say
 */
public record DraftClaimIntegrity(LocalDate asOf, String regime, int revision, List<Row> rows, List<String> notIncluded) {

    public DraftClaimIntegrity {
        rows = List.copyOf(rows);
        notIncluded = List.copyOf(notIncluded);
    }

    /**
     * One claim.
     *
     * @param rule the rule whose lines move with what is evidenced
     * @param allowed what its lines hold as the draft stands
     * @param evidenced what they hold with every evidenced figure cut to what is held
     * @param gap {@code allowed} less {@code evidenced}; below nil where another claim takes over when evidence is cut
     * @param limitedBy what bound the evidenced figure, as the engine names it
     * @param says what the row means and what would close it, in the year's words
     */
    public record Row(String rule, Money allowed, Money evidenced, Money gap, Optional<String> limitedBy,
            List<String> says) {

        public Row {
            says = List.copyOf(says);
        }
    }
}
