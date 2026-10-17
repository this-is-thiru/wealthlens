package com.thiru.wealthlens.taxengine.rules.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Premium share bands, as Schedule II Sl. 2 lists them (D-205): 20% from 1-4-2003, 10% from 2012, 15% special. */
class PremiumShareBandsTest {

    private static final PremiumShareBands SL_2 = new PremiumShareBands(List.of(
            new PremiumShareBands.Band(new PolicyIssueWindow(Optional.of(LocalDate.of(2003, 4, 1)),
                    Optional.of(LocalDate.of(2012, 3, 31)), false), Rate.percent("20")),
            new PremiumShareBands.Band(new PolicyIssueWindow(Optional.of(LocalDate.of(2012, 4, 1)), Optional.empty(),
                    false), Rate.percent("10")),
            new PremiumShareBands.Band(new PolicyIssueWindow(Optional.of(LocalDate.of(2013, 4, 1)), Optional.empty(),
                    true), Rate.percent("15"))));

    @Test
    void bandHolding_whenAPolicyPredatesEveryBand_isEmpty() {
        assertThat(SL_2.bandHolding(LocalDate.of(2001, 6, 1), false)).isEmpty();
    }

    @Test
    void bandHolding_whenASpecialPolicyIsIssuedFrom2013_isTheSpecialBand() {
        assertThat(SL_2.bandHolding(LocalDate.of(2015, 6, 1), true).orElseThrow().share())
                .isEqualTo(Rate.percent("15"));
        assertThat(SL_2.bandHolding(LocalDate.of(2015, 6, 1), false).orElseThrow().share())
                .isEqualTo(Rate.percent("10"));
    }

    @Test
    void bandFor_whenNoBandHolds_isRefused() {
        assertThatThrownBy(() -> SL_2.bandFor(LocalDate.of(2001, 6, 1), false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
