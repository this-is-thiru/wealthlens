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

    /** The line is for one entered item; its sentence names the income, and this confirms it. (W-327c2, D-685) */
    public static final ReasonCode OS_FOR_THIS_ITEM = new ReasonCode("OS_FOR_THIS_ITEM");

    /** The expense is not allowed against this kind of income. (W-327c2, D-685) */
    public static final ReasonCode OS_EXPENSE_NOT_DEDUCTED = new ReasonCode("OS_EXPENSE_NOT_DEDUCTED");

    /** The expense is payable outside India untaxed, so nothing is deducted. (W-327c2, D-685) */
    public static final ReasonCode UNTAXED_ABROAD = new ReasonCode("UNTAXED_ABROAD");

    /** A business trust's specified sum: distributed less issue price and earlier charges, never below nil. (W-327c2, D-685) */
    public static final ReasonCode BUSINESS_TRUST_SPECIFIED_SUM = new ReasonCode("BUSINESS_TRUST_SPECIFIED_SUM");

    /** The contribution above the threshold enters the taxable contribution account. (W-327c2, D-685) */
    public static final ReasonCode PF_CONTRIBUTION_ABOVE_THRESHOLD = new ReasonCode("PF_CONTRIBUTION_ABOVE_THRESHOLD");

    /** The interest earned in the taxable contribution account. (W-327c2, D-685) */
    public static final ReasonCode PF_TAXABLE_ACCOUNT_INTEREST = new ReasonCode("PF_TAXABLE_ACCOUNT_INTEREST");

    /** The gift is excluded on a ground the rules list; the ground stays in limitedBy. (W-327c2, D-685) */
    public static final ReasonCode GIFT_EXCLUDED = new ReasonCode("GIFT_EXCLUDED");

    /** Sums received on a COVID-19 death are excluded up to the limit. (W-327c2, D-685) */
    public static final ReasonCode GIFT_COVID_DEATH_LIMIT = new ReasonCode("GIFT_COVID_DEATH_LIMIT");

    /** The gift is measured on its own against the threshold. (W-327c2, D-685) */
    public static final ReasonCode GIFT_ON_ITS_OWN = new ReasonCode("GIFT_ON_ITS_OWN");

    /** The gifts of the kind are totalled for the year against the threshold. (W-327c2, D-685) */
    public static final ReasonCode GIFTS_FOR_THE_YEAR = new ReasonCode("GIFTS_FOR_THE_YEAR");

    /** The sums received under the life policy are excluded. (W-327c2, D-685) */
    public static final ReasonCode LIFE_POLICY_EXCLUDED = new ReasonCode("LIFE_POLICY_EXCLUDED");

    /** Only the sum above premium not deducted or counted is income. (W-327c2, D-685) */
    public static final ReasonCode LIFE_POLICY_ABOVE_PREMIUMS = new ReasonCode("LIFE_POLICY_ABOVE_PREMIUMS");

    /** Interest expense against dividend and unit income, within the row's limit. (W-327c2, D-685) */
    public static final ReasonCode DIVIDEND_INTEREST_LIMITED = new ReasonCode("DIVIDEND_INTEREST_LIMITED");

    /** The line is for one entered property; a short confirmation. (W-327d1, D-687) */
    public static final ReasonCode HP_FOR_THIS_PROPERTY = new ReasonCode("HP_FOR_THIS_PROPERTY");

    /** Arrears of rent received, less the deduction the law allows off them. (W-327d1, D-687) */
    public static final ReasonCode HP_ARREARS_LESS = new ReasonCode("HP_ARREARS_LESS");

    /** The annual value of the owner's own residence is nil. (W-327d1, D-687) */
    public static final ReasonCode HP_OWN_RESIDENCE_NIL = new ReasonCode("HP_OWN_RESIDENCE_NIL");

    /** A let property's rent against its expected value, less local taxes paid. (W-327d1, D-687) */
    public static final ReasonCode HP_ANNUAL_VALUE_LET = new ReasonCode("HP_ANNUAL_VALUE_LET");

    /** A property not let: its expected value, less local taxes paid. (W-327d1, D-687) */
    public static final ReasonCode HP_ANNUAL_VALUE_NOT_LET = new ReasonCode("HP_ANNUAL_VALUE_NOT_LET");

    /** The rent receivable, less the rent that could not be realised. (W-327d1, D-687) */
    public static final ReasonCode HP_LESS_UNREALISED_RENT = new ReasonCode("HP_LESS_UNREALISED_RENT");

    /** Interest on own residences is held to an aggregate, each kind within its limit. (W-327d1, D-687) */
    public static final ReasonCode HP_OWN_RESIDENCE_INTEREST_AGGREGATE = new ReasonCode("HP_OWN_RESIDENCE_INTEREST_AGGREGATE");

    /** The priced choice with the least net payable, taken. (W-327d1, D-687) */
    public static final ReasonCode ELECTION_CHOSEN = new ReasonCode("ELECTION_CHOSEN");

    /** A priced choice not taken, its net payable being higher. (W-327d1, D-687) */
    public static final ReasonCode ELECTION_NOT_CHOSEN = new ReasonCode("ELECTION_NOT_CHOSEN");

    /** A choice the assessee stated, applied as stated and not priced. (W-327d1, D-687) */
    public static final ReasonCode ELECTION_AS_STATED = new ReasonCode("ELECTION_AS_STATED");

    /** Under the regime the loss is deemed given full effect and is not carried forward. (W-327d1, D-687) */
    public static final ReasonCode LOSS_DEEMED_GIVEN_EFFECT = new ReasonCode("LOSS_DEEMED_GIVEN_EFFECT");

    /** A house-property loss not set off is carried forward against that head. (W-327d1, D-687) */
    public static final ReasonCode LOSS_CARRIED_FORWARD_HOUSE_PROPERTY = new ReasonCode("LOSS_CARRIED_FORWARD_HOUSE_PROPERTY");

    /** A loss not set off that the law does not carry forward. (W-327d1, D-687) */
    public static final ReasonCode LOSS_NOT_CARRIED_FORWARD = new ReasonCode("LOSS_NOT_CARRIED_FORWARD");

    /** A head's loss set against another head's income. (W-327d1, D-687) */
    public static final ReasonCode LOSS_SET_OFF_AGAINST_HEAD = new ReasonCode("LOSS_SET_OFF_AGAINST_HEAD");

    /** A head's loss set against a capital-gains part. (W-327d1, D-687) */
    public static final ReasonCode LOSS_SET_OFF_AGAINST_PART = new ReasonCode("LOSS_SET_OFF_AGAINST_PART");

    /** A loss not determined in a return filed in time is not carried forward. (W-327d1, D-687) */
    public static final ReasonCode CARRIED_LOSS_NOT_IN_TIME = new ReasonCode("CARRIED_LOSS_NOT_IN_TIME");

    /** A carried loss past the years it may be carried. (W-327d1, D-687) */
    public static final ReasonCode CARRIED_LOSS_EXPIRED = new ReasonCode("CARRIED_LOSS_EXPIRED");

    /** What is left of a carried loss is carried forward again. (W-327d1, D-687) */
    public static final ReasonCode CARRIED_LOSS_STILL_CARRIED = new ReasonCode("CARRIED_LOSS_STILL_CARRIED");

    /** A carried loss set against a capital-gains part. (W-327d1, D-687) */
    public static final ReasonCode CARRIED_LOSS_AGAINST_PART = new ReasonCode("CARRIED_LOSS_AGAINST_PART");

    /** A carried loss set against a head's income. (W-327d1, D-687) */
    public static final ReasonCode CARRIED_LOSS_AGAINST_HEAD = new ReasonCode("CARRIED_LOSS_AGAINST_HEAD");

    /** A capital-gains part nets to a loss. (W-327d2, D-688) */
    public static final ReasonCode CG_PART_LOSS = new ReasonCode("CG_PART_LOSS");

    /** A capital-gains part nets to a gain. (W-327d2, D-688) */
    public static final ReasonCode CG_PART_GAIN = new ReasonCode("CG_PART_GAIN");

    /** An indexed cost above the consideration leaves nothing at the indexed measure. (W-327d2, D-688) */
    public static final ReasonCode CG_INDEXED_MEASURE_NIL = new ReasonCode("CG_INDEXED_MEASURE_NIL");

    /** A short-term capital loss is set off against a part. (W-327d2, D-688) */
    public static final ReasonCode CG_SHORT_TERM_LOSS_SET_OFF_AGAINST_PART = new ReasonCode("CG_SHORT_TERM_LOSS_SET_OFF_AGAINST_PART");

    /** A long-term capital loss is set off against a part. (W-327d2, D-688) */
    public static final ReasonCode CG_LONG_TERM_LOSS_SET_OFF_AGAINST_PART = new ReasonCode("CG_LONG_TERM_LOSS_SET_OFF_AGAINST_PART");

    /** What is left of a short-term capital loss is carried forward. (W-327d2, D-688) */
    public static final ReasonCode CG_SHORT_TERM_LOSS_CARRIED_FORWARD = new ReasonCode("CG_SHORT_TERM_LOSS_CARRIED_FORWARD");

    /** What is left of a long-term capital loss is carried forward. (W-327d2, D-688) */
    public static final ReasonCode CG_LONG_TERM_LOSS_CARRIED_FORWARD = new ReasonCode("CG_LONG_TERM_LOSS_CARRIED_FORWARD");

    /** A charge is expenditure on the transfer. (W-327d2, D-688) */
    public static final ReasonCode CG_CHARGE_TRANSFER_EXPENDITURE = new ReasonCode("CG_CHARGE_TRANSFER_EXPENDITURE");

    /** A charge is part of the cost of acquisition. (W-327d2, D-688) */
    public static final ReasonCode CG_CHARGE_COST_OF_ACQUISITION = new ReasonCode("CG_CHARGE_COST_OF_ACQUISITION");

    /** A charge is not deducted. (W-327d2, D-688) */
    public static final ReasonCode CG_CHARGE_NOT_DEDUCTED = new ReasonCode("CG_CHARGE_NOT_DEDUCTED");

    /** The cost is indexed between two years. (W-327d2, D-688) */
    public static final ReasonCode CG_COST_INDEXED = new ReasonCode("CG_COST_INDEXED");

    /** Another head charges the gain. (W-327d2, D-688) */
    public static final ReasonCode CG_LEFT_TO_ANOTHER_HEAD = new ReasonCode("CG_LEFT_TO_ANOTHER_HEAD");

    /** No record shows the buy's STT; a row gives its state. (W-327d2, D-688) */
    public static final ReasonCode CG_STT_ON_BUY = new ReasonCode("CG_STT_ON_BUY");

    /** No record shows the sale's STT; a row gives its state. (W-327d2, D-688) */
    public static final ReasonCode CG_STT_ON_SALE = new ReasonCode("CG_STT_ON_SALE");

    /** Held longer than the period, so long-term. (W-327d2, D-688) */
    public static final ReasonCode CG_HELD_LONG_TERM = new ReasonCode("CG_HELD_LONG_TERM");

    /** Held no longer than the period, so short-term. (W-327d2, D-688) */
    public static final ReasonCode CG_HELD_SHORT_TERM = new ReasonCode("CG_HELD_SHORT_TERM");

    /** The new house is dated outside the window. (W-327d2, D-688) */
    public static final ReasonCode CG_REINVESTMENT_OUTSIDE_WINDOW = new ReasonCode("CG_REINVESTMENT_OUTSIDE_WINDOW");

    /** The buckets a claim reached show no gain together. (W-327d2, D-688) */
    public static final ReasonCode CG_REINVESTMENT_NO_GAIN = new ReasonCode("CG_REINVESTMENT_NO_GAIN");

    /** The new house's counted cost reaches the net consideration: the whole gain is exempt. (W-327d2, D-688) */
    public static final ReasonCode CG_REINVESTMENT_WHOLE = new ReasonCode("CG_REINVESTMENT_WHOLE");

    /** The new house's counted cost is below the net consideration: a share is exempt. (W-327d2, D-688) */
    public static final ReasonCode CG_REINVESTMENT_SHARE = new ReasonCode("CG_REINVESTMENT_SHARE");

    /** A resident individual of the row's age is not liable to advance tax. (W-327e1, D-690) */
    public static final ReasonCode ADVANCE_TAX_NOT_LIABLE_BY_AGE = new ReasonCode("ADVANCE_TAX_NOT_LIABLE_BY_AGE");

    /** What is due after tax at source is below the amount advance tax starts at. (W-327e1, D-690) */
    public static final ReasonCode ADVANCE_TAX_NOT_LIABLE_BELOW = new ReasonCode("ADVANCE_TAX_NOT_LIABLE_BELOW");

    /** Liable to advance tax on the bill less tax at source. (W-327e1, D-690) */
    public static final ReasonCode ADVANCE_TAX_LIABLE = new ReasonCode("ADVANCE_TAX_LIABLE");

    /** What is still owed of the instalments due by a date. (W-327e1, D-690) */
    public static final ReasonCode ADVANCE_TAX_STILL_OWED = new ReasonCode("ADVANCE_TAX_STILL_OWED");

    /** Tax at source credited. (W-327e1, D-690) */
    public static final ReasonCode CREDIT_TAX_AT_SOURCE = new ReasonCode("CREDIT_TAX_AT_SOURCE");

    /** Advance tax credited. (W-327e1, D-690) */
    public static final ReasonCode CREDIT_ADVANCE_TAX = new ReasonCode("CREDIT_ADVANCE_TAX");

    /** Self-assessment tax credited. (W-327e1, D-690) */
    public static final ReasonCode CREDIT_SELF_ASSESSMENT_TAX = new ReasonCode("CREDIT_SELF_ASSESSMENT_TAX");

    /** Not liable to advance tax, so no interest on it. (W-327e1, D-690) */
    public static final ReasonCode INTEREST_NOT_LIABLE_TO_ADVANCE_TAX = new ReasonCode("INTEREST_NOT_LIABLE_TO_ADVANCE_TAX");

    /** An instalment date not yet reached. (W-327e1, D-690) */
    public static final ReasonCode DEFERMENT_NOT_YET_REACHED = new ReasonCode("DEFERMENT_NOT_YET_REACHED");

    /** Enough paid by an instalment date. (W-327e1, D-690) */
    public static final ReasonCode DEFERMENT_PAID_ENOUGH = new ReasonCode("DEFERMENT_PAID_ENOUGH");

    /** Less than the share paid by an instalment date. (W-327e1, D-690) */
    public static final ReasonCode DEFERMENT_SHORT = new ReasonCode("DEFERMENT_SHORT");

    /** Enough paid by the date, and tax on income arising after it relieved. (W-327e1, D-690) */
    public static final ReasonCode DEFERMENT_PAID_ENOUGH_RELIEVED = new ReasonCode("DEFERMENT_PAID_ENOUGH_RELIEVED");

    /** Short by the date, and tax on income arising after it relieved. (W-327e1, D-690) */
    public static final ReasonCode DEFERMENT_SHORT_RELIEVED = new ReasonCode("DEFERMENT_SHORT_RELIEVED");

    /** Enough paid by the date; tax on income arising after it not relieved. (W-327e1, D-690) */
    public static final ReasonCode DEFERMENT_PAID_ENOUGH_NOT_RELIEVED = new ReasonCode("DEFERMENT_PAID_ENOUGH_NOT_RELIEVED");

    /** Short by the date; tax on income arising after it not relieved. (W-327e1, D-690) */
    public static final ReasonCode DEFERMENT_SHORT_NOT_RELIEVED = new ReasonCode("DEFERMENT_SHORT_NOT_RELIEVED");

    /** The return is furnished by its due date. (W-327e1, D-690) */
    public static final ReasonCode FEE_RETURN_FURNISHED_IN_TIME = new ReasonCode("FEE_RETURN_FURNISHED_IN_TIME");

    /** The return is furnished after its due date. (W-327e1, D-690) */
    public static final ReasonCode FEE_RETURN_FURNISHED_LATE = new ReasonCode("FEE_RETURN_FURNISHED_LATE");

    /** Interest for the months counted. (W-327e1, D-690) */
    public static final ReasonCode INTEREST_FOR_MONTHS = new ReasonCode("INTEREST_FOR_MONTHS");

    /** A span of interest not yet begun. (W-327e1, D-690) */
    public static final ReasonCode INTEREST_NOT_YET_DUE = new ReasonCode("INTEREST_NOT_YET_DUE");

    /** Advance tax paid reaches the share of the assessed tax. (W-327e1, D-690) */
    public static final ReasonCode SHORTFALL_ADVANCE_TAX_PAID_ENOUGH = new ReasonCode("SHORTFALL_ADVANCE_TAX_PAID_ENOUGH");

    /** A refund below the share of the tax carries no interest. (W-327e1, D-690) */
    public static final ReasonCode REFUND_BELOW_SHARE = new ReasonCode("REFUND_BELOW_SHARE");

    /** Refund interest on the slice returning a payment on the return. (W-327e1, D-690) */
    public static final ReasonCode REFUND_INTEREST_ON_RETURN_PAYMENT = new ReasonCode("REFUND_INTEREST_ON_RETURN_PAYMENT");

    /** Refund interest on the slice returning tax at source and advance tax. (W-327e1, D-690) */
    public static final ReasonCode REFUND_INTEREST_ON_PREPAID_TAX = new ReasonCode("REFUND_INTEREST_ON_PREPAID_TAX");

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
            Map.entry(PREMIUM_SHARE_OF_SUM_ASSURED, Set.of("share", "sumAssured")),
            Map.entry(OS_FOR_THIS_ITEM, Set.of()),
            Map.entry(OS_EXPENSE_NOT_DEDUCTED, Set.of()),
            Map.entry(UNTAXED_ABROAD, Set.of()),
            Map.entry(BUSINESS_TRUST_SPECIFIED_SUM, Set.of("distributed", "issued", "earlier")),
            Map.entry(PF_CONTRIBUTION_ABOVE_THRESHOLD, Set.of()),
            Map.entry(PF_TAXABLE_ACCOUNT_INTEREST, Set.of()),
            Map.entry(GIFT_EXCLUDED, Set.of()),
            Map.entry(GIFT_COVID_DEATH_LIMIT, Set.of()),
            Map.entry(GIFT_ON_ITS_OWN, Set.of()),
            Map.entry(GIFTS_FOR_THE_YEAR, Set.of()),
            Map.entry(LIFE_POLICY_EXCLUDED, Set.of()),
            Map.entry(LIFE_POLICY_ABOVE_PREMIUMS, Set.of("premiums")),
            Map.entry(DIVIDEND_INTEREST_LIMITED, Set.of("income", "limit")),
            Map.entry(HP_FOR_THIS_PROPERTY, Set.of()),
            Map.entry(HP_ARREARS_LESS, Set.of("deduction")),
            Map.entry(HP_OWN_RESIDENCE_NIL, Set.of()),
            Map.entry(HP_ANNUAL_VALUE_LET, Set.of("rent", "expected", "taxes")),
            Map.entry(HP_ANNUAL_VALUE_NOT_LET, Set.of("expected", "taxes")),
            Map.entry(HP_LESS_UNREALISED_RENT, Set.of("unrealised")),
            Map.entry(HP_OWN_RESIDENCE_INTEREST_AGGREGATE, Set.of("aggregate")),
            Map.entry(ELECTION_CHOSEN, Set.of()),
            Map.entry(ELECTION_NOT_CHOSEN, Set.of()),
            Map.entry(ELECTION_AS_STATED, Set.of()),
            Map.entry(LOSS_DEEMED_GIVEN_EFFECT, Set.of("regime")),
            Map.entry(LOSS_CARRIED_FORWARD_HOUSE_PROPERTY, Set.of()),
            Map.entry(LOSS_NOT_CARRIED_FORWARD, Set.of()),
            Map.entry(LOSS_SET_OFF_AGAINST_HEAD, Set.of("loss", "against")),
            Map.entry(LOSS_SET_OFF_AGAINST_PART, Set.of("loss", "part")),
            Map.entry(CARRIED_LOSS_NOT_IN_TIME, Set.of("year")),
            Map.entry(CARRIED_LOSS_EXPIRED, Set.of("year", "years")),
            Map.entry(CARRIED_LOSS_STILL_CARRIED, Set.of("year")),
            Map.entry(CARRIED_LOSS_AGAINST_PART, Set.of("year", "part")),
            Map.entry(CARRIED_LOSS_AGAINST_HEAD, Set.of("year", "head")),
            Map.entry(CG_PART_LOSS, Set.of("part")),
            Map.entry(CG_PART_GAIN, Set.of("part")),
            Map.entry(CG_INDEXED_MEASURE_NIL, Set.of("part")),
            Map.entry(CG_SHORT_TERM_LOSS_SET_OFF_AGAINST_PART, Set.of("part")),
            Map.entry(CG_LONG_TERM_LOSS_SET_OFF_AGAINST_PART, Set.of("part")),
            Map.entry(CG_SHORT_TERM_LOSS_CARRIED_FORWARD, Set.of()),
            Map.entry(CG_LONG_TERM_LOSS_CARRIED_FORWARD, Set.of()),
            Map.entry(CG_CHARGE_TRANSFER_EXPENDITURE, Set.of()),
            Map.entry(CG_CHARGE_COST_OF_ACQUISITION, Set.of()),
            Map.entry(CG_CHARGE_NOT_DEDUCTED, Set.of()),
            Map.entry(CG_COST_INDEXED, Set.of("cost", "from", "to", "indexed")),
            Map.entry(CG_LEFT_TO_ANOTHER_HEAD, Set.of()),
            Map.entry(CG_STT_ON_BUY, Set.of("state")),
            Map.entry(CG_STT_ON_SALE, Set.of("state")),
            Map.entry(CG_HELD_LONG_TERM, Set.of("bought", "sold", "months")),
            Map.entry(CG_HELD_SHORT_TERM, Set.of("bought", "sold", "months")),
            Map.entry(CG_REINVESTMENT_OUTSIDE_WINDOW, Set.of("house", "from", "to")),
            Map.entry(CG_REINVESTMENT_NO_GAIN, Set.of()),
            Map.entry(CG_REINVESTMENT_WHOLE, Set.of("cost", "consideration")),
            Map.entry(CG_REINVESTMENT_SHARE, Set.of("cost", "consideration")),
            Map.entry(ADVANCE_TAX_NOT_LIABLE_BY_AGE, Set.of("age")),
            Map.entry(ADVANCE_TAX_NOT_LIABLE_BELOW, Set.of("threshold")),
            Map.entry(ADVANCE_TAX_LIABLE, Set.of()),
            Map.entry(ADVANCE_TAX_STILL_OWED, Set.of("date")),
            Map.entry(CREDIT_TAX_AT_SOURCE, Set.of()),
            Map.entry(CREDIT_ADVANCE_TAX, Set.of()),
            Map.entry(CREDIT_SELF_ASSESSMENT_TAX, Set.of()),
            Map.entry(INTEREST_NOT_LIABLE_TO_ADVANCE_TAX, Set.of()),
            Map.entry(DEFERMENT_NOT_YET_REACHED, Set.of("date")),
            Map.entry(DEFERMENT_PAID_ENOUGH, Set.of("date")),
            Map.entry(DEFERMENT_SHORT, Set.of("date")),
            Map.entry(DEFERMENT_PAID_ENOUGH_RELIEVED, Set.of("date", "relieved", "paidBy")),
            Map.entry(DEFERMENT_SHORT_RELIEVED, Set.of("date", "relieved", "paidBy")),
            Map.entry(DEFERMENT_PAID_ENOUGH_NOT_RELIEVED, Set.of("date", "tax", "paidBy", "paid")),
            Map.entry(DEFERMENT_SHORT_NOT_RELIEVED, Set.of("date", "tax", "paidBy", "paid")),
            Map.entry(FEE_RETURN_FURNISHED_IN_TIME, Set.of("date")),
            Map.entry(FEE_RETURN_FURNISHED_LATE, Set.of("date")),
            Map.entry(INTEREST_FOR_MONTHS, Set.of("months", "from", "to")),
            Map.entry(INTEREST_NOT_YET_DUE, Set.of("from")),
            Map.entry(SHORTFALL_ADVANCE_TAX_PAID_ENOUGH, Set.of()),
            Map.entry(REFUND_BELOW_SHARE, Set.of("refund", "share", "tax")),
            Map.entry(REFUND_INTEREST_ON_RETURN_PAYMENT, Set.of("months", "from", "to", "paidOn")),
            Map.entry(REFUND_INTEREST_ON_PREPAID_TAX, Set.of("months", "from", "to")));

    private ReasonCodes() {
    }
}
