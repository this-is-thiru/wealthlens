package com.thiru.wealthlens.taxengine.rules.component;

import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import java.util.Optional;

/**
 * When a component existed, what replaced it, and what to do with drafts that still name it.
 *
 * @param validity the years this component may be used in
 * @param supersededBy the code that replaced it, on a rename or a merge
 * @param onWithdrawal what a draft naming it should do once it is out of validity
 */
public record ComponentLifecycle(
        DateRange validity,
        Optional<ComponentCode> supersededBy,
        DeprecationPolicy onWithdrawal) {

    public ComponentLifecycle {
        if (validity == null) {
            throw new IllegalArgumentException("A component lifecycle must state its validity");
        }
        if (onWithdrawal == null) {
            throw new IllegalArgumentException(
                    "A component lifecycle must state what happens on withdrawal; without it a"
                            + " draft naming a dead code has no defined behaviour (D-NOSILENT)");
        }
        supersededBy = supersededBy == null ? Optional.empty() : supersededBy;

        if (onWithdrawal instanceof DeprecationPolicy.AutoMigrate migrate
                && supersededBy.isPresent()
                && !supersededBy.get().equals(migrate.to())) {
            throw new IllegalArgumentException(
                    "Superseded by " + supersededBy.get() + " but auto-migrates to " + migrate.to()
                            + "; a draft would move somewhere the registry does not point");
        }
    }

    /** A component in force from a date, with no end and nothing to migrate. */
    public static ComponentLifecycle openEndedFrom(java.time.LocalDate from) {
        return new ComponentLifecycle(
                DateRange.fromOnwards(from), Optional.empty(), new DeprecationPolicy.HistoricalOnly());
    }

    /** Whether this component may be used in a given tax year. */
    public boolean isLiveIn(TaxYearId taxYear) {
        return validity.coversTaxYear(taxYear);
    }
}
