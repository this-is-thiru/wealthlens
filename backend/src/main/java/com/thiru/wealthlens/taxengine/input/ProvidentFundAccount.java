package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * A person's own account in one provident fund for the tax year: what the person contributed, whether the employer
 * contributes, and the interest the fund accrued in the year in the taxable contribution account (S2 Schedule II Sl. 3,
 * 4 with S4 r.277; 1961 s.10(11), (12) provisos with Rules 1962 r.9D; D-198).
 *
 * <p>The taxable interest is the fund's figure, not one the engine can derive: the taxable contribution account holds
 * every year's contribution beyond the threshold since 2021-22 with its interest, less withdrawals, and no provision
 * prescribes the rate or how the fund credits it (r.277(1), (3)(b)). The contribution is entered so the year's excess
 * can be shown, per fund — the threshold is tested "in that fund", never across funds.
 *
 * <p>A fund is one the Provident Funds Act, 1925 applies to or the Central Government has notified, or a recognised
 * provident fund. An unrecognised fund's interest is not exempt under either clause to begin with, and is not entered
 * here.
 *
 * @param contributed the person's own contributions to the fund in the tax year
 * @param employerContributes whether the person's employer contributes to the fund — the threshold is higher where
 *     it does not
 * @param taxableAccountInterest the interest accrued in the tax year in the fund's taxable contribution account, as
 *     the fund reports it
 */
public record ProvidentFundAccount(Money contributed, boolean employerContributes, Money taxableAccountInterest) {

    public ProvidentFundAccount {
        for (Money figure : new Money[] {contributed, taxableAccountInterest}) {
            if (figure == null || figure.isLessThan(Money.ZERO)) {
                throw new IllegalArgumentException("A provident fund account needs its contribution and its taxable"
                        + " account's interest, neither negative; enter 0 where nil");
            }
        }
    }
}
