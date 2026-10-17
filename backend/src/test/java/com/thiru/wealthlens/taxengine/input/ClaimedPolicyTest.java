package com.thiru.wealthlens.taxengine.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** An old capped policy, claimed exempt earlier (CBDT Circulars No. 2/2022, 15/2023; D-207). Figures are fixtures. */
class ClaimedPolicyTest {

    @Test
    void payableIn_whenTheYearIsWithinItsPremiumYears_isTrue() {
        // When — premiums 2023-24 to 2032-33
        ClaimedPolicy old = new ClaimedPolicy(LocalDate.of(2023, 4, 1), false, Money.of("450000"), TaxYearId.of(2023),
                TaxYearId.of(2032));

        // Then
        assertThat(old.payableIn(2023)).isTrue();
        assertThat(old.payableIn(2032)).isTrue();
        assertThat(old.payableIn(2033)).isFalse();
    }

    @Test
    void construct_whenThePremiumYearsRunBackwardsOrAFactIsMissing_isRefused() {
        assertThatThrownBy(() -> new ClaimedPolicy(LocalDate.of(2023, 4, 1), false, Money.of("1"), TaxYearId.of(2030),
                        TaxYearId.of(2029)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClaimedPolicy(LocalDate.of(2023, 4, 1), false, null, TaxYearId.of(2023),
                        TaxYearId.of(2032)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
