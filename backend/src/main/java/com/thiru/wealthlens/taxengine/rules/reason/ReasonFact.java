package com.thiru.wealthlens.taxengine.rules.reason;

import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;

/**
 * One fact a {@link Reason} carries, typed so the renderer can put it into words: a regime through the content's term
 * for it (W-327a, D-678), a figure in Indian notation and a share as a percentage (W-327a1, D-679), a count and a tax
 * year (W-327b1, D-681). A kind is added with the first reason that needs it.
 */
public sealed interface ReasonFact {

    /** A regime, named in words by the content's {@code terms.regime}. */
    record RegimeFact(RegimeCode regime) implements ReasonFact {

        public RegimeFact {
            if (regime == null) {
                throw new IllegalArgumentException("A regime fact must name its regime");
            }
        }
    }

    /** A figure, written in Indian notation. */
    record MoneyFact(Money amount) implements ReasonFact {

        public MoneyFact {
            if (amount == null) {
                throw new IllegalArgumentException("A money fact must carry its amount");
            }
        }
    }

    /** A count, written as a number (W-327b1, D-681). */
    record CountFact(int count) implements ReasonFact {

        public CountFact {
            if (count < 0) {
                throw new IllegalArgumentException("A count fact may not be negative, but was " + count);
            }
        }
    }

    /** A tax year, written as its label, such as 2021-22 (W-327b1, D-681). */
    record TaxYearFact(TaxYearId taxYear) implements ReasonFact {

        public TaxYearFact {
            if (taxYear == null) {
                throw new IllegalArgumentException("A tax-year fact must name its year");
            }
        }
    }

    /** A share, written as a percentage. */
    record ShareFact(Rate share) implements ReasonFact {

        public ShareFact {
            if (share == null) {
                throw new IllegalArgumentException("A share fact must carry its share");
            }
        }
    }
}
