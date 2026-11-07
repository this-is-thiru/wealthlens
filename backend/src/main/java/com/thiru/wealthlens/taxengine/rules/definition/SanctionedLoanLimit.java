package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.Optional;

/**
 * A loan-interest deduction's limit and the loan it is for: sanctioned within a window, within amount and value
 * ceilings, and, for a house, to someone who owned none on the date of sanction (S2 s.130–132; D-139). A definition
 * carrying its limit, as {@link DisabledTransportLimit} does, because a rule version is a cap or a definition, never
 * both.
 *
 * @param limit the most interest deductible in a tax year
 * @param sanctionedFrom the first sanction date that qualifies
 * @param sanctionedTo the last sanction date that qualifies
 * @param maxAmountSanctioned the largest loan that qualifies, where the section sets one
 * @param maxPropertyValue the largest value of the house, where the section sets one (s.130(3)(c))
 * @param maxStampDutyValue the largest stamp duty value of the house, where the section sets one (s.131(2)(b))
 * @param requiresNoHouseOwned whether the assessee must have owned no residential house on the date of sanction
 */
public record SanctionedLoanLimit(
        Money limit,
        LocalDate sanctionedFrom,
        LocalDate sanctionedTo,
        Optional<Money> maxAmountSanctioned,
        Optional<Money> maxPropertyValue,
        Optional<Money> maxStampDutyValue,
        boolean requiresNoHouseOwned) implements RuleDefinition {

    public SanctionedLoanLimit {
        if (limit == null || sanctionedFrom == null || sanctionedTo == null || maxAmountSanctioned == null
                || maxPropertyValue == null || maxStampDutyValue == null) {
            throw new IllegalArgumentException("A sanctioned-loan limit needs its limit, window and ceilings;"
                    + " Optional.empty() where the section sets none");
        }
        if (sanctionedTo.isBefore(sanctionedFrom)) {
            throw new IllegalArgumentException("A sanction window ends " + sanctionedTo + ", before it starts "
                    + sanctionedFrom);
        }
    }
}
