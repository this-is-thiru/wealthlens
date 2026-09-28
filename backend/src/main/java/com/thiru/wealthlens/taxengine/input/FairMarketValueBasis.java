package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;

/**
 * How a specified security's fair market value on the exercise date is found — the method the Rules prescribe, with
 * the prices or valuation it reads entered as facts (S4 r.15(6)–(7); Rules 1962 r.3(8)–(9); D-121).
 */
public sealed interface FairMarketValueBasis {

    /**
     * A listed share traded on the exercise date: "the average of the opening price and closing price of the share on
     * that date", on the exchange recording the highest volume where it is listed on several.
     *
     * @param openingPrice the price of the first settlement that day
     * @param closingPrice the price of the last settlement that day
     */
    record ListedTraded(Money openingPrice, Money closingPrice) implements FairMarketValueBasis {

        public ListedTraded {
            if (openingPrice == null || closingPrice == null || openingPrice.isLessThan(Money.ZERO)
                    || closingPrice.isLessThan(Money.ZERO)) {
                throw new IllegalArgumentException("A listed share needs its opening and closing prices, nil or more");
            }
        }
    }

    /**
     * A listed share not traded on the exercise date: its closing price "on a date closest to the date of exercising of
     * the option and immediately preceding such date".
     *
     * @param closingPrice that day's closing price
     * @param onDate that day, before the exercise date
     */
    record ListedNotTraded(Money closingPrice, LocalDate onDate) implements FairMarketValueBasis {

        public ListedNotTraded {
            if (closingPrice == null || onDate == null || closingPrice.isLessThan(Money.ZERO)) {
                throw new IllegalArgumentException("An untraded listed share needs a closing price and its date");
            }
        }
    }

    /**
     * An unlisted share, or a specified security that is not an equity share: "such value … as determined by a merchant
     * banker on the specified date".
     *
     * @param valuePerUnit the merchant banker's value of one share or security
     * @param specifiedDate the date of the valuation — the exercise date or up to the Rule's window before it
     */
    record MerchantBankerValue(Money valuePerUnit, LocalDate specifiedDate) implements FairMarketValueBasis {

        public MerchantBankerValue {
            if (valuePerUnit == null || specifiedDate == null || valuePerUnit.isLessThan(Money.ZERO)) {
                throw new IllegalArgumentException("A merchant banker's value needs an amount and its date");
            }
        }
    }
}
