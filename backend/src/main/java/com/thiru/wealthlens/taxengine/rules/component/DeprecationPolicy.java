package com.thiru.wealthlens.taxengine.rules.component;

import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Rate;

/**
 * What happens to a saved draft when the component it names goes away.
 *
 * <p>Components are withdrawn, renamed and merged between years, and a user's draft outlives those
 * changes. The question this answers is what a draft referencing a dead code should do on reload —
 * and the one answer that is never acceptable is dropping it silently, because the user would file a
 * return missing a figure they had entered.
 *
 * <p>Sealed, so a fourth policy is a new class and every caller is forced to handle it.
 */
public sealed interface DeprecationPolicy {

    /**
     * The code is gone and no successor is equivalent. The user must decide.
     *
     * @param guidance what to tell them — shown verbatim, so it must be actionable
     */
    record RequireUserCorrection(String guidance) implements DeprecationPolicy {

        public RequireUserCorrection {
            if (guidance == null || guidance.isBlank()) {
                throw new IllegalArgumentException(
                        "RequireUserCorrection must say what the user should do; a blank prompt"
                                + " blocks a draft without explaining how to unblock it");
            }
        }
    }

    /**
     * The code maps cleanly onto a successor, so the draft can be upcast on read.
     *
     * @param to the successor code
     * @param conversionFactor applied to the amount — {@code 100%} for a plain rename
     */
    record AutoMigrate(ComponentCode to, Rate conversionFactor) implements DeprecationPolicy {

        public AutoMigrate {
            if (to == null) {
                throw new IllegalArgumentException("AutoMigrate must name the successor code");
            }
            if (conversionFactor == null) {
                throw new IllegalArgumentException(
                        "AutoMigrate to " + to + " must state a conversion factor;"
                                + " use Rate.percent(\"100\") for a plain rename");
            }
        }
    }

    /** Still accepted for the years it was valid in, rejected for new ones. */
    record HistoricalOnly() implements DeprecationPolicy {}
}
