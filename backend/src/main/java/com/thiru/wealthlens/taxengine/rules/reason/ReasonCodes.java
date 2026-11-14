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

    /** The line's own row excludes the benefit; its sentence says so, and this confirms nothing is added. (W-327b2, D-682) */
    public static final ReasonCode EXCLUDED_BY_RULE = new ReasonCode("EXCLUDED_BY_RULE");

    /** The use of an asset is valued as a yearly share of its cost for the months used, less what was paid back. (W-327b2, D-682) */
    public static final ReasonCode ASSET_USE_COST_SHARE = new ReasonCode("ASSET_USE_COST_SHARE");

    /** The use of a hired asset is valued at the rent paid, less what was paid back. (W-327b2, D-682) */
    public static final ReasonCode ASSET_USE_RENT = new ReasonCode("ASSET_USE_RENT");

    /** A transferred asset is valued at its written-down cost, less the price paid. (W-327b2, D-682) */
    public static final ReasonCode ASSET_TRANSFER_WRITTEN_DOWN = new ReasonCode("ASSET_TRANSFER_WRITTEN_DOWN");

    /** A benefit is valued at its amount, less what was paid back. (W-327b2, D-682) */
    public static final ReasonCode LESS_RECOVERED = new ReasonCode("LESS_RECOVERED");

    /** An employer's gifts are below the limit, so none of them is taxed. (W-327b2, D-682) */
    public static final ReasonCode GIFTS_BELOW_LIMIT = new ReasonCode("GIFTS_BELOW_LIMIT");

    /** An employer's gifts reach the limit, so all of them are taxed. (W-327b2, D-682) */
    public static final ReasonCode GIFTS_REACH_LIMIT = new ReasonCode("GIFTS_REACH_LIMIT");

    /** A child's education costs no more a month than the limit, so it is not taxed. (W-327b2, D-682) */
    public static final ReasonCode EDUCATION_WITHIN_LIMIT = new ReasonCode("EDUCATION_WITHIN_LIMIT");

    /** A child's education costs more a month than the limit, so the whole for the months is taxed, less what was paid back. (W-327b2, D-682) */
    public static final ReasonCode EDUCATION_ABOVE_LIMIT = new ReasonCode("EDUCATION_ABOVE_LIMIT");

    /** An employer's loans do not exceed the petty limit, so nothing is taxed. (W-327b2, D-682) */
    public static final ReasonCode LOANS_WITHIN_PETTY_LIMIT = new ReasonCode("LOANS_WITHIN_PETTY_LIMIT");

    /** The loan is for treating a listed disease, so it is not taxed. (W-327b2, D-682) */
    public static final ReasonCode LOAN_FOR_LISTED_DISEASE = new ReasonCode("LOAN_FOR_LISTED_DISEASE");

    /** A loan is valued at interest at the bank's rate on its balances, less the interest paid. (W-327b2, D-682) */
    public static final ReasonCode LOAN_INTEREST_AT_BANK_RATE = new ReasonCode("LOAN_INTEREST_AT_BANK_RATE");

    /** No limit reaches the food provided, so all of it is taxed. (W-327b2, D-682) */
    public static final ReasonCode MEALS_IN_FULL = new ReasonCode("MEALS_IN_FULL");

    /** A car is valued at a set amount for each month. (W-327b2, D-682) */
    public static final ReasonCode CAR_PER_MONTH = new ReasonCode("CAR_PER_MONTH");

    /** As CAR_PER_MONTH, plus the amount for a chauffeur. (W-327b2, D-682) */
    public static final ReasonCode CAR_PER_MONTH_WITH_CHAUFFEUR = new ReasonCode("CAR_PER_MONTH_WITH_CHAUFFEUR");

    /** A car is valued at its running cost plus wear and tear for the months, less what the employee was charged. (W-327b2, D-682) */
    public static final ReasonCode CAR_RUNNING_AND_WEAR = new ReasonCode("CAR_RUNNING_AND_WEAR");

    /** Accommodation is valued under its rule, less the rent paid. (W-327b2, D-682) */
    public static final ReasonCode ACCOMMODATION_LESS_RENT = new ReasonCode("ACCOMMODATION_LESS_RENT");

    /** Continued accommodation is capped at the first year's value raised by the cost inflation index. (W-327b2, D-682) */
    public static final ReasonCode ACCOMMODATION_CONTINUED_CAP = new ReasonCode("ACCOMMODATION_CONTINUED_CAP");

    /** Furniture is valued as a yearly share of its cost, less what the employee paid. (W-327b2, D-682) */
    public static final ReasonCode FURNITURE_COST_SHARE = new ReasonCode("FURNITURE_COST_SHARE");

    /** Furniture is valued at its hire charges, less what the employee paid. (W-327b2, D-682) */
    public static final ReasonCode FURNITURE_HIRE = new ReasonCode("FURNITURE_HIRE");

    /** The employee is not a specified employee, so the benefits taxed only for one are not. (W-327b2, D-682) */
    public static final ReasonCode NOT_SPECIFIED_EMPLOYEE = new ReasonCode("NOT_SPECIFIED_EMPLOYEE");

    /** Securities are valued at their fair market value, less what was paid. (W-327b2, D-682) */
    public static final ReasonCode SECURITY_VALUE = new ReasonCode("SECURITY_VALUE");

    /** Chapter VIII deductions together are limited to the income they are taken from. (W-327c1, D-684) */
    public static final ReasonCode DEDUCTIONS_WITHIN_INCOME = new ReasonCode("DEDUCTIONS_WITHIN_INCOME");

    /** The gross total income the deductions are limited to leaves out capital gains taxed at special rates. (W-327c1, D-684) */
    public static final ReasonCode GROSS_TOTAL_LESS_SPECIAL_GAINS = new ReasonCode("GROSS_TOTAL_LESS_SPECIAL_GAINS");

    /** Only savings-account interest is deducted; time-deposit interest only for a senior citizen. (W-327c1, D-684) */
    public static final ReasonCode DEPOSIT_INTEREST_SAVINGS_ONLY = new ReasonCode("DEPOSIT_INTEREST_SAVINGS_ONLY");

    /** A condition of the deduction is not met; the condition stays in limitedBy. (W-327c1, D-684) */
    public static final ReasonCode CONDITION_NOT_MET = new ReasonCode("CONDITION_NOT_MET");

    /** The deduction is the amount set for a certified disability of its degree. (W-327c1, D-684) */
    public static final ReasonCode DISABILITY_CERTIFIED = new ReasonCode("DISABILITY_CERTIFIED");

    /** Nothing was spent on or deposited for the dependant's care. (W-327c1, D-684) */
    public static final ReasonCode DEPENDANT_NOTHING_SPENT = new ReasonCode("DEPENDANT_NOTHING_SPENT");

    /** The deduction is only for a resident. (W-327c1, D-684) */
    public static final ReasonCode NOT_RESIDENT = new ReasonCode("NOT_RESIDENT");

    /** The donations' qualifying limit is a share of adjusted gross total income. (W-327c1, D-684) */
    public static final ReasonCode DONATION_QUALIFYING_LIMIT = new ReasonCode("DONATION_QUALIFYING_LIMIT");

    /** A donation paid in cash above the limit is not deducted. (W-327c1, D-684) */
    public static final ReasonCode DONATION_CASH_LIMIT = new ReasonCode("DONATION_CASH_LIMIT");

    /** A donation the donee has not reported is not deducted. (W-327c1, D-684) */
    public static final ReasonCode DONATION_UNREPORTED = new ReasonCode("DONATION_UNREPORTED");

    /** The donations are deducted without a qualifying limit. (W-327c1, D-684) */
    public static final ReasonCode DONATION_WITHOUT_LIMIT = new ReasonCode("DONATION_WITHOUT_LIMIT");

    /** The donations are deducted within the qualifying limit. (W-327c1, D-684) */
    public static final ReasonCode DONATION_WITHIN_QUALIFYING_LIMIT = new ReasonCode("DONATION_WITHIN_QUALIFYING_LIMIT");

    /** A health payment made in cash that the rules do not allow in cash is not deducted. (W-327c1, D-684) */
    public static final ReasonCode HEALTH_PAID_IN_CASH = new ReasonCode("HEALTH_PAID_IN_CASH");

    /** Preventive check-ups are split between the family's and the parents' limits as claimed. (W-327c1, D-684) */
    public static final ReasonCode HEALTH_CHECKUP_SPLIT = new ReasonCode("HEALTH_CHECKUP_SPLIT");

    /** Education-loan interest counts only for the initial tax year and the years after it. (W-327c1, D-684) */
    public static final ReasonCode EDUCATION_LOAN_OUTSIDE_YEARS = new ReasonCode("EDUCATION_LOAN_OUTSIDE_YEARS");

    /** The whole amount is deducted. (W-327c1, D-684) */
    public static final ReasonCode ALL_DEDUCTED = new ReasonCode("ALL_DEDUCTED");

    /** The loan fails a condition on its sanction or the house; the condition stays in limitedBy. (W-327c1, D-684) */
    public static final ReasonCode LOAN_SANCTION_CONDITION_NOT_MET = new ReasonCode("LOAN_SANCTION_CONDITION_NOT_MET");

    /** The whole amount is deducted, being within the limit. (W-327c1, D-684) */
    public static final ReasonCode DEDUCTED_IN_FULL = new ReasonCode("DEDUCTED_IN_FULL");

    /** The deduction is held to its limit. (W-327c1, D-684) */
    public static final ReasonCode DEDUCTION_LIMITED = new ReasonCode("DEDUCTION_LIMITED");

    /** Rent is not deducted: a home is owned where the assessee lives or works. (W-327c1, D-684) */
    public static final ReasonCode RENT_OWNS_HOME_HERE = new ReasonCode("RENT_OWNS_HOME_HERE");

    /** Rent is not deducted: an occupied home is owned elsewhere. (W-327c1, D-684) */
    public static final ReasonCode RENT_OWNS_HOME_ELSEWHERE = new ReasonCode("RENT_OWNS_HOME_ELSEWHERE");

    /** Rent is not deducted: house rent allowance is received. (W-327c1, D-684) */
    public static final ReasonCode RENT_RECEIVES_HRA = new ReasonCode("RENT_RECEIVES_HRA");

    /** A life premium counts only up to a share of the sum assured. (W-327c1, D-684) */
    public static final ReasonCode PREMIUM_SHARE_OF_SUM_ASSURED = new ReasonCode("PREMIUM_SHARE_OF_SUM_ASSURED");

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
            Map.entry(EMPLOYER_ACCRETION_SHARE, Set.of()),
            Map.entry(EXCLUDED_BY_RULE, Set.of()),
            Map.entry(ASSET_USE_COST_SHARE, Set.of("months", "recovered")),
            Map.entry(ASSET_USE_RENT, Set.of("recovered")),
            Map.entry(ASSET_TRANSFER_WRITTEN_DOWN, Set.of("written", "years", "price")),
            Map.entry(LESS_RECOVERED, Set.of("recovered")),
            Map.entry(GIFTS_BELOW_LIMIT, Set.of("given", "limit")),
            Map.entry(GIFTS_REACH_LIMIT, Set.of("given", "limit")),
            Map.entry(EDUCATION_WITHIN_LIMIT, Set.of("cost", "limit")),
            Map.entry(EDUCATION_ABOVE_LIMIT, Set.of("cost", "limit", "months", "recovered")),
            Map.entry(LOANS_WITHIN_PETTY_LIMIT, Set.of("lent", "limit")),
            Map.entry(LOAN_FOR_LISTED_DISEASE, Set.of()),
            Map.entry(LOAN_INTEREST_AT_BANK_RATE, Set.of("rate", "interest", "paid")),
            Map.entry(MEALS_IN_FULL, Set.of()),
            Map.entry(CAR_PER_MONTH, Set.of("months")),
            Map.entry(CAR_PER_MONTH_WITH_CHAUFFEUR, Set.of("months")),
            Map.entry(CAR_RUNNING_AND_WEAR, Set.of("wear", "months", "charged")),
            Map.entry(ACCOMMODATION_LESS_RENT, Set.of("value", "rent")),
            Map.entry(ACCOMMODATION_CONTINUED_CAP, Set.of("cap")),
            Map.entry(FURNITURE_COST_SHARE, Set.of("charges")),
            Map.entry(FURNITURE_HIRE, Set.of("charges")),
            Map.entry(NOT_SPECIFIED_EMPLOYEE, Set.of("salary", "threshold")),
            Map.entry(SECURITY_VALUE, Set.of("units", "value", "paid")),
            Map.entry(DEDUCTIONS_WITHIN_INCOME, Set.of()),
            Map.entry(GROSS_TOTAL_LESS_SPECIAL_GAINS, Set.of("gains")),
            Map.entry(DEPOSIT_INTEREST_SAVINGS_ONLY, Set.of()),
            Map.entry(CONDITION_NOT_MET, Set.of()),
            Map.entry(DISABILITY_CERTIFIED, Set.of()),
            Map.entry(DEPENDANT_NOTHING_SPENT, Set.of()),
            Map.entry(NOT_RESIDENT, Set.of()),
            Map.entry(DONATION_QUALIFYING_LIMIT, Set.of("adjusted")),
            Map.entry(DONATION_CASH_LIMIT, Set.of("limit")),
            Map.entry(DONATION_UNREPORTED, Set.of()),
            Map.entry(DONATION_WITHOUT_LIMIT, Set.of()),
            Map.entry(DONATION_WITHIN_QUALIFYING_LIMIT, Set.of()),
            Map.entry(HEALTH_PAID_IN_CASH, Set.of()),
            Map.entry(HEALTH_CHECKUP_SPLIT, Set.of("family", "parents")),
            Map.entry(EDUCATION_LOAN_OUTSIDE_YEARS, Set.of("initial", "years")),
            Map.entry(ALL_DEDUCTED, Set.of()),
            Map.entry(LOAN_SANCTION_CONDITION_NOT_MET, Set.of()),
            Map.entry(DEDUCTED_IN_FULL, Set.of("limit")),
            Map.entry(DEDUCTION_LIMITED, Set.of("limit")),
            Map.entry(RENT_OWNS_HOME_HERE, Set.of()),
            Map.entry(RENT_OWNS_HOME_ELSEWHERE, Set.of()),
            Map.entry(RENT_RECEIVES_HRA, Set.of()),
            Map.entry(PREMIUM_SHARE_OF_SUM_ASSURED, Set.of("share", "sumAssured")));

    private ReasonCodes() {
    }
}
