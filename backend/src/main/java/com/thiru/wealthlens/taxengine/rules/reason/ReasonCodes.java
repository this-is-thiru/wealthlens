package com.thiru.wealthlens.taxengine.rules.reason;

import com.thiru.wealthlens.taxengine.rules.id.ReasonCode;
import java.util.Map;
import java.util.Set;

/**
 * Every reason code the engine publishes, with the names of the facts its reason carries (W-327a, D-678). The content
 * must word each code, and a code's sentence may fill only these facts; the pack refuses anything else.
 */
public final class ReasonCodes {

    /** The regime forbids the rule by a disallowance set (D-165). */
    public static final ReasonCode REGIME_DISALLOWS = new ReasonCode("REGIME_DISALLOWS");

    /** The rule's row is written for other regimes (D-166). */
    public static final ReasonCode REGIME_EXCLUDES_ROW = new ReasonCode("REGIME_EXCLUDES_ROW");

    /** A {@code Fixed} cap: the amount itself (W-327a1, D-679). */
    public static final ReasonCode CAP_FIXED = new ReasonCode("CAP_FIXED");

    /** A {@code PercentOfBase} cap: the share taken. */
    public static final ReasonCode CAP_SHARE = new ReasonCode("CAP_SHARE");

    /** An {@code AmountInExcess} cap: the threshold's figure, above which the amount counts. */
    public static final ReasonCode CAP_IN_EXCESS = new ReasonCode("CAP_IN_EXCESS");

    /** An {@code AggregateWithCeiling} whose ceiling did not bind. */
    public static final ReasonCode CAP_ACTUAL_AMOUNTS = new ReasonCode("CAP_ACTUAL_AMOUNTS");

    /** An {@code AggregateWithCeiling} whose ceiling bound: the ceiling. */
    public static final ReasonCode CAP_AGGREGATE_CEILING = new ReasonCode("CAP_AGGREGATE_CEILING");

    /** The claimed disability is not among those the year's rule lists for the allowance. (W-327b1, D-681) */
    public static final ReasonCode ALLOWANCE_DISABILITY_NOT_LISTED = new ReasonCode("ALLOWANCE_DISABILITY_NOT_LISTED");

    /** The whole amount is within the limit. (W-327b1, D-681) */
    public static final ReasonCode WITHIN_LIMIT = new ReasonCode("WITHIN_LIMIT");

    /** The allowance is held to its monthly limit for the months covered. (W-327b1, D-681) */
    public static final ReasonCode ALLOWANCE_MONTHLY_LIMIT = new ReasonCode("ALLOWANCE_MONTHLY_LIMIT");

    /** The home is the assessee's own, or no rent was paid. (W-327b1, D-681) */
    public static final ReasonCode HRA_NOT_RENTED = new ReasonCode("HRA_NOT_RENTED");

    /** The journey is beyond the number the rules exempt in a block of calendar years. (W-327b1, D-681) */
    public static final ReasonCode LTC_JOURNEYS_IN_BLOCK = new ReasonCode("LTC_JOURNEYS_IN_BLOCK");

    /** The exemption is held to the travel assistance received. (W-327b1, D-681) */
    public static final ReasonCode LTC_ASSISTANCE_RECEIVED = new ReasonCode("LTC_ASSISTANCE_RECEIVED");

    /** The child is beyond the number of surviving children the concession covers. (W-327b1, D-681) */
    public static final ReasonCode LTC_CHILD_BEYOND_LIMIT = new ReasonCode("LTC_CHILD_BEYOND_LIMIT");

    /** Part of the cost abroad is excluded from the perquisite. (W-327b1, D-681) */
    public static final ReasonCode MEDICAL_ABROAD_EXCLUDED = new ReasonCode("MEDICAL_ABROAD_EXCLUDED");

    /** A condition for excluding the travel abroad is not met. (W-327b1, D-681) */
    public static final ReasonCode MEDICAL_TRAVEL_CONDITION_NOT_MET = new ReasonCode("MEDICAL_TRAVEL_CONDITION_NOT_MET");

    /** A deduction under the retirement entry was allowed in an earlier year, which bars another. (W-327b1, D-681) */
    public static final ReasonCode RETIREMENT_ALLOWED_BEFORE = new ReasonCode("RETIREMENT_ALLOWED_BEFORE");

    /** A rule bars a deduction under the retirement entry this year. (W-327b1, D-681) */
    public static final ReasonCode RETIREMENT_BARRED = new ReasonCode("RETIREMENT_BARRED");

    /** The commuted pension is held to the share of its commuted value the law exempts. (W-327b1, D-681) */
    public static final ReasonCode COMMUTED_PENSION_LIMIT = new ReasonCode("COMMUTED_PENSION_LIMIT");

    /** As COMMUTED_PENSION_LIMIT, at the smaller share that applies when gratuity was also received. (W-327b1, D-681) */
    public static final ReasonCode COMMUTED_PENSION_LIMIT_WITH_GRATUITY = new ReasonCode("COMMUTED_PENSION_LIMIT_WITH_GRATUITY");

    /** The ceiling across employers and years is reduced by what earlier years allowed. (W-327b1, D-681) */
    public static final ReasonCode RETIREMENT_CEILING_USED = new ReasonCode("RETIREMENT_CEILING_USED");

    /** As RETIREMENT_CEILING_USED, and by what this year's earlier payments were allowed. (W-327b1, D-681) */
    public static final ReasonCode RETIREMENT_CEILING_USED_THIS_YEAR = new ReasonCode("RETIREMENT_CEILING_USED_THIS_YEAR");

    /** Salary includes the perquisites valued in their own node. (W-327b1, D-681) */
    public static final ReasonCode SALARY_INCLUDES_PERQUISITES = new ReasonCode("SALARY_INCLUDES_PERQUISITES");

    /** Salary includes the employer's retirement contributions above the limit. (W-327b1, D-681) */
    public static final ReasonCode SALARY_INCLUDES_EMPLOYER_EXCESS = new ReasonCode("SALARY_INCLUDES_EMPLOYER_EXCESS");

    /** Salary includes the employer's contribution to the pension-scheme account. (W-327b1, D-681) */
    public static final ReasonCode SALARY_INCLUDES_PENSION_CONTRIBUTION = new ReasonCode("SALARY_INCLUDES_PENSION_CONTRIBUTION");

    /** Salary includes the Central Government's contribution to the Agniveer Corpus Fund account. (W-327b1, D-681) */
    public static final ReasonCode SALARY_INCLUDES_AGNIVEER = new ReasonCode("SALARY_INCLUDES_AGNIVEER");

    /** The cap's law changed within the year, so each part applies to its share of the year. (W-327b1, D-681) */
    public static final ReasonCode CAP_APPORTIONED = new ReasonCode("CAP_APPORTIONED");

    /** The accretion taxed is the share of the fund's income earned on contributions above the limit. (W-327b1, D-681) */
    public static final ReasonCode EMPLOYER_ACCRETION_SHARE = new ReasonCode("EMPLOYER_ACCRETION_SHARE");

    /** Each code, with the facts its reason carries. */
    public static final Map<ReasonCode, Set<String>> FACTS = Map.ofEntries(
            Map.entry(REGIME_DISALLOWS, Set.of("regime")),
            Map.entry(REGIME_EXCLUDES_ROW, Set.of("regime")),
            Map.entry(CAP_FIXED, Set.of("amount")),
            Map.entry(CAP_SHARE, Set.of("share")),
            Map.entry(CAP_IN_EXCESS, Set.of("threshold")),
            Map.entry(CAP_ACTUAL_AMOUNTS, Set.of()),
            Map.entry(CAP_AGGREGATE_CEILING, Set.of("ceiling")),
            Map.entry(ALLOWANCE_DISABILITY_NOT_LISTED, Set.of()),
            Map.entry(WITHIN_LIMIT, Set.of("limit")),
            Map.entry(ALLOWANCE_MONTHLY_LIMIT, Set.of("limit")),
            Map.entry(HRA_NOT_RENTED, Set.of()),
            Map.entry(LTC_JOURNEYS_IN_BLOCK, Set.of("journeys", "years")),
            Map.entry(LTC_ASSISTANCE_RECEIVED, Set.of()),
            Map.entry(LTC_CHILD_BEYOND_LIMIT, Set.of("children")),
            Map.entry(MEDICAL_ABROAD_EXCLUDED, Set.of("excluded")),
            Map.entry(MEDICAL_TRAVEL_CONDITION_NOT_MET, Set.of()),
            Map.entry(RETIREMENT_ALLOWED_BEFORE, Set.of("year")),
            Map.entry(RETIREMENT_BARRED, Set.of()),
            Map.entry(COMMUTED_PENSION_LIMIT, Set.of("limit")),
            Map.entry(COMMUTED_PENSION_LIMIT_WITH_GRATUITY, Set.of("limit")),
            Map.entry(RETIREMENT_CEILING_USED, Set.of("before")),
            Map.entry(RETIREMENT_CEILING_USED_THIS_YEAR, Set.of("before", "thisYear")),
            Map.entry(SALARY_INCLUDES_PERQUISITES, Set.of()),
            Map.entry(SALARY_INCLUDES_EMPLOYER_EXCESS, Set.of()),
            Map.entry(SALARY_INCLUDES_PENSION_CONTRIBUTION, Set.of()),
            Map.entry(SALARY_INCLUDES_AGNIVEER, Set.of()),
            Map.entry(CAP_APPORTIONED, Set.of()),
            Map.entry(EMPLOYER_ACCRETION_SHARE, Set.of()));

    private ReasonCodes() {
    }
}
