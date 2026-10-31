package com.thiru.wealthlens.taxengine.rules.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The capped life policies' premium limits (S2 Schedule II Sl. 2 col. C (a), (b); 1961 s.10(10D) provisos; D-205):
 * ₹2,50,000 for a unit linked policy from 1-2-2021, ₹5,00,000 for another from 1-4-2023, none for an IFSC office's.
 */
class PolicyPremiumLimitsTest {

    private static final PolicyPremiumLimits TY_2026 = new PolicyPremiumLimits(LocalDate.of(2021, 2, 1),
            Money.of("250000"), LocalDate.of(2023, 4, 1), Money.of("500000"), Optional.of(LocalDate.of(2025, 4, 1)));
    private static final PolicyPremiumLimits TY_2025 = new PolicyPremiumLimits(LocalDate.of(2021, 2, 1),
            Money.of("250000"), LocalDate.of(2023, 4, 1), Money.of("500000"), Optional.empty());

    @Test
    void limitFor_whenAUnitLinkedPolicyIsIssuedOnOrAfterItsDate_isItsLimit() {
        assertThat(TY_2026.limitFor(LocalDate.of(2021, 2, 1), true, false)).contains(Money.of("250000"));
        assertThat(TY_2026.limitFor(LocalDate.of(2021, 1, 31), true, false)).isEmpty();
    }

    @Test
    void limitFor_whenAnotherPolicyIsIssuedOnOrAfterItsDate_isItsLimit() {
        assertThat(TY_2026.limitFor(LocalDate.of(2023, 4, 1), false, false)).contains(Money.of("500000"));
        assertThat(TY_2026.limitFor(LocalDate.of(2023, 3, 31), false, false)).isEmpty();
    }

    @Test
    void limitFor_whenAnIfscOfficeIssuedItInTaxYear2026_freesItOnlyFromItsDate() {
        assertThat(TY_2026.limitFor(LocalDate.of(2025, 4, 1), false, true)).isEmpty();
        assertThat(TY_2026.limitFor(LocalDate.of(2024, 6, 1), false, true)).contains(Money.of("500000"));
    }

    @Test
    void limitFor_whenAnIfscOfficeIssuedItInTaxYear2025_freesItWhenever() {
        assertThat(TY_2025.limitFor(LocalDate.of(2024, 6, 1), false, true)).isEmpty();
    }

    @Test
    void constructor_whenALimitIsNilOrADateMissing_isRefused() {
        assertThatThrownBy(() -> new PolicyPremiumLimits(LocalDate.of(2021, 2, 1), Money.ZERO, LocalDate.of(2023, 4, 1),
                        Money.of("500000"), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PolicyPremiumLimits(null, Money.of("1"), LocalDate.of(2023, 4, 1),
                        Money.of("500000"), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
