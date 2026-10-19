package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.EmployerLoan;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * An interest-free or concessional loan from the employer as a perquisite, for {@link PerquisiteNode} (S4 r.15(5)
 * Table IV Sl. 1; Rules 1962 r.3(7)(i); D-118).
 *
 * <p>One month's interest at the entered State Bank of India rate per month-end balance, less the interest paid. Nil
 * where one employer's loans, as lent, do not exceed the petty limit; nil for a loan for a listed disease, save that
 * from the month after a medical insurance scheme reimbursed part of it, the lesser of the balance and the amount
 * reimbursed is valued.
 */
final class LoanPerquisite {

    static final Set<RuleSetId> RULES = Set.of(RuleSetIds.RS_PERQ_LOAN, RuleSetIds.RS_PERQ_LOAN_PETTY);

    private static final BaseId INTEREST_AT_BANK_RATE = new BaseId("INTEREST_AT_BANK_RATE");

    private LoanPerquisite() {
    }

    /** One line per loan, numbered from {@code firstSequence}. */
    static List<TraceLine> lines(NodeContext ctx, int firstSequence) {
        List<EmployerLoan> loans = ctx.inputs().entered().loans();
        Map<Integer, Money> lentByEmployer = new TreeMap<>();
        for (EmployerLoan loan : loans) {
            PerquisiteNode.requireEnteredEmployment(ctx, loan.employment(), "loan");
            lentByEmployer.merge(loan.employment(), loan.amountLent(), Money::plus);
        }
        Money pettyLimit = loans.isEmpty() ? Money.ZERO : TemporallyApportionedCap.apply(
                ctx.ruleOf(RuleSetIds.RS_PERQ_LOAN_PETTY), ctx.inputs().temporal(), new CapContext(Map.of())).value();
        List<TraceLine> lines = new ArrayList<>();
        for (EmployerLoan loan : loans) {
            int sequence = firstSequence + lines.size();
            Money lent = lentByEmployer.get(loan.employment());
            lines.add(lent.isLessThanOrEqual(pettyLimit)
                    ? ctx.traceLine(sequence, ctx.ruleIdOf(RuleSetIds.RS_PERQ_LOAN_PETTY), StepKind.INCOME,
                                    loan.amountLent(), Money.ZERO)
                            .withLimitedBy("employment " + loan.employment()
                            + "'s loans lent " + lent + " do not exceed " + pettyLimit)
                    : valued(ctx, loan, sequence));
        }
        return lines;
    }

    private static TraceLine valued(NodeContext ctx, EmployerLoan loan, int sequence) {
        List<Money> balances = valuedBalances(loan);
        Money total = balances.stream().reduce(Money.ZERO, Money::plus);
        if (!total.isGreaterThan(Money.ZERO) && loan.forListedDisease()) {
            return ctx.traceLine(sequence, ctx.ruleIdOf(RuleSetIds.RS_PERQ_LOAN), StepKind.INCOME,
                            loan.amountLent(), Money.ZERO)
                    .withLimitedBy("not a perquisite: for treatment of a disease the year's rule lists");
        }
        Money interest = loan.stateBankRate().ofShare(total, 1, 12);
        Money atBankRate = TemporallyApportionedCap.apply(ctx.ruleOf(RuleSetIds.RS_PERQ_LOAN), ctx.inputs().temporal(),
                CapContext.of(INTEREST_AT_BANK_RATE, interest)).value();
        Money value = atBankRate.minus(loan.interestPaid()).floorAtZero();
        return ctx.traceLine(sequence, ctx.ruleIdOf(RuleSetIds.RS_PERQ_LOAN), StepKind.INCOME, loan.amountLent(), value)
                .withLimitedBy("interest at " + loan.stateBankRate() + " a year on "
                + balances.size() + " month-end balances totalling " + total + ", " + atBankRate + ", less "
                + loan.interestPaid() + " paid" + (loan.forListedDisease() ? " — the part a medical insurance scheme"
                + " reimbursed" : ""));
    }

    /**
     * Every month-end balance; for a loan for a listed disease, only the reimbursed part after the month it was
     * reimbursed in (Table IV Sl. 1 col. D (b)).
     */
    private static List<Money> valuedBalances(EmployerLoan loan) {
        if (!loan.forListedDisease()) {
            return loan.monthEndBalances();
        }
        List<Money> valued = new ArrayList<>();
        loan.insuranceReimbursement().ifPresent(reimbursed -> {
            List<Money> balances = loan.monthEndBalances();
            for (int month = reimbursed.afterMonth() + 1; month < balances.size(); month++) {
                valued.add(Money.min(balances.get(month), reimbursed.amount()));
            }
        });
        return valued;
    }

}
