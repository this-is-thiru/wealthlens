package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

/**
 * An index notified for each financial year — the Cost Inflation Index (1961 s.48 Explanation (v); 2025 Act
 * s.72(8)(a); D-115).
 *
 * <p>A table rather than one figure, because a rule reading it compares two years: the accommodation cap scales the
 * first year's amount by this year's index over the first year's (S4 r.15(2)(d); Rules 1962 r.3(1) third proviso). A
 * year the table does not hold is refused, never read as zero or as its neighbour.
 *
 * @param indexByYear the index for each financial year, as the notifications state it
 */
public record IndexByYear(Map<TaxYearId, Integer> indexByYear) implements RuleDefinition {

    public IndexByYear {
        if (indexByYear == null || indexByYear.isEmpty()) {
            throw new IllegalArgumentException("An index table needs at least one year");
        }
        if (indexByYear.values().stream().anyMatch(index -> index == null || index <= 0)) {
            throw new IllegalArgumentException("Every index must be a positive number, but the table was "
                    + indexByYear);
        }
        indexByYear = Map.copyOf(indexByYear);
    }

    /**
     * The index for one year.
     *
     * @throws IllegalArgumentException if the table does not hold that year
     */
    public int of(TaxYearId year) {
        Integer index = indexByYear.get(year);
        if (index == null) {
            throw new IllegalArgumentException("No index is notified in this pack for " + year.label() + "; the table"
                    + " holds " + yearsHeld());
        }
        return index;
    }

    private String yearsHeld() {
        TreeMap<TaxYearId, Integer> ordered = new TreeMap<>(Comparator.comparingInt(TaxYearId::startYear));
        ordered.putAll(indexByYear);
        return ordered.keySet().toString();
    }
}
