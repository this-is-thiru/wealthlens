package com.thiru.wealthlens.taxengine.rules.cap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The TY 2026-27 default-regime ladder, and the validation that rejects a malformed one.
 *
 * <p>Every expected figure is copied from {@code 08-golden-tests.md} and {@code 01-rulebook.md}.
 * None is computed here, and none is derived from the code under test — if one of these fails, the
 * code is wrong, not the number (rule R3).
 */
class SlabLadderTest {

    private static final BaseId TOTAL_INCOME = new BaseId("TOTAL_INCOME");

    /**
     * {@code RATE-SLAB-NEW-TAX-REGIME} — 01-rulebook.md:82-92, VERIFIED (S2, s.202(1) Table).
     *
     * <p>Six bands of ₹4,00,000 at Nil/5/10/15/20/25%, then 30% above ₹24,00,000.
     */
    private static SlabLadder defaultRegime2026() {
        return new SlabLadder(
                List.of(
                        band("0", "400000", "0"),
                        band("400000", "800000", "5"),
                        band("800000", "1200000", "10"),
                        band("1200000", "1600000", "15"),
                        band("1600000", "2000000", "20"),
                        band("2000000", "2400000", "25"),
                        openBand("2400000", "30")),
                TOTAL_INCOME);
    }

    private static SlabLadder.Band band(String lower, String upper, String percent) {
        return new SlabLadder.Band(
                Money.of(lower), Optional.of(Money.of(upper)), Rate.percent(percent));
    }

    private static SlabLadder.Band openBand(String lower, String percent) {
        return new SlabLadder.Band(Money.of(lower), Optional.empty(), Rate.percent(percent));
    }

    private static Money taxOn(SlabLadder ladder, String income) {
        return ladder.apply(CapContext.of(TOTAL_INCOME, Money.of(income))).value();
    }

    @Nested
    @DisplayName("the TY 2026-27 default ladder")
    class DefaultLadder {

        /**
         * The cumulative table at 08-golden-tests.md:20, copied as a row, plus ₹50,00,000 from
         * T2-1 at 08-golden-tests.md:169 and 01-rulebook.md:298.
         */
        @ParameterizedTest(name = "₹{0} → ₹{1}")
        @CsvSource({
            "400000,    0",
            "800000,    20000",
            "1200000,   60000",
            "1600000,   120000",
            "2000000,   200000",
            "2400000,   300000",
            "5000000,   1080000",
        })
        void apply_matchesTheRulebookCumulativeTable(String income, String expectedTax) {
            assertThat(taxOn(defaultRegime2026(), income)).isEqualTo(Money.of(expectedTax));
        }

        @Test
        void apply_belowTheFirstCeiling_isNil() {
            // 01-rulebook.md:86 — up to ₹4,00,000 is Nil
            assertThat(taxOn(defaultRegime2026(), "399999")).isEqualTo(Money.ZERO);
            assertThat(taxOn(defaultRegime2026(), "0")).isEqualTo(Money.ZERO);
        }

        @Test
        void apply_oneRupeeAboveACeiling_taxesExactlyThatRupee() {
            // RATE-SLAB-BAND-001: the width is min(TI, upper) − lower, so ₹4,00,001 taxes ₹1 at 5%
            Money atCeiling = taxOn(defaultRegime2026(), "400000");
            Money oneAbove = taxOn(defaultRegime2026(), "400001");

            assertThat(oneAbove.minus(atCeiling)).isEqualTo(Money.of("0.05"));
        }

        @Test
        void apply_sumOfPerBandSlices_equalsTheWholeLadder() {
            // D-CLOSEDSUM: the ladder is a sum over bands, so splitting it must change nothing
            SlabLadder ladder = defaultRegime2026();
            Money whole = taxOn(ladder, "5000000");

            Money summed = Money.ZERO;
            for (SlabLadder.Band b : ladder.bands()) {
                Money ceiling = b.upperInclusive().orElse(Money.of("5000000"));
                Money width = Money.min(Money.of("5000000"), ceiling).minus(b.lowerExclusive());
                if (width.isGreaterThan(Money.ZERO)) {
                    summed = summed.plus(b.rate().of(width));
                }
            }

            assertThat(summed).isEqualTo(whole);
        }

        @Test
        void apply_namesItselfSlab() {
            assertThat(defaultRegime2026().apply(CapContext.of(TOTAL_INCOME, Money.of("800000"))).limitedBy())
                    .isEqualTo("slab");
        }

        @Test
        void validate_acceptsTheRealLadder() {
            assertThatCode(() -> defaultRegime2026().validate()).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("validate() — RATE-SLAB-BAND-002")
    class Validation {

        /** The trap this whole item exists for: a band written as the previous ceiling plus one. */
        @Test
        void validate_whenABandIsWrittenAsPreviousCeilingPlusOne_isRejected() {
            // Given — band 1 written 400001 instead of 400000
            List<SlabLadder.Band> bands = new ArrayList<>(defaultRegime2026().bands());
            bands.set(1, band("400001", "800000", "5"));
            SlabLadder ladder = new SlabLadder(bands, TOTAL_INCOME);

            // When / Then
            assertThatThrownBy(ladder::validate)
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("band 1")
                    .hasMessageContaining("400001")
                    .hasMessageContaining("RATE-SLAB-BAND-002");
        }

        @Test
        void validate_whenBandsOverlap_isRejected() {
            List<SlabLadder.Band> bands = new ArrayList<>(defaultRegime2026().bands());
            bands.set(1, band("300000", "800000", "5"));

            assertThatThrownBy(() -> new SlabLadder(bands, TOTAL_INCOME).validate())
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("band 1");
        }

        @Test
        void validate_whenTheFirstBandDoesNotStartAtZero_isRejected() {
            // Income below the first band would be untaxed and unreported
            List<SlabLadder.Band> bands = new ArrayList<>(defaultRegime2026().bands());
            bands.set(0, band("100000", "400000", "0"));

            assertThatThrownBy(() -> new SlabLadder(bands, TOTAL_INCOME).validate())
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("must start at 0");
        }

        @Test
        void validate_whenTheTopBandIsClosed_isRejected() {
            // Income above the last ceiling would be untaxed
            List<SlabLadder.Band> bands = new ArrayList<>(defaultRegime2026().bands());
            bands.set(6, band("2400000", "9999999", "30"));

            assertThatThrownBy(() -> new SlabLadder(bands, TOTAL_INCOME).validate())
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("must be open-ended");
        }

        @Test
        void validate_whenAnOpenBandIsNotLast_isRejected() {
            // Every band above an open one is unreachable
            List<SlabLadder.Band> bands = new ArrayList<>(defaultRegime2026().bands());
            bands.set(2, openBand("800000", "10"));

            assertThatThrownBy(() -> new SlabLadder(bands, TOTAL_INCOME).validate())
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("band 2")
                    .hasMessageContaining("unreachable");
        }

        @Test
        void validate_whenThereAreNoBands_isRejected() {
            assertThatThrownBy(() -> new SlabLadder(List.of(), TOTAL_INCOME).validate())
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("at least one band");
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        void bands_areDefensivelyCopied() {
            List<SlabLadder.Band> mutable = new ArrayList<>(defaultRegime2026().bands());
            SlabLadder ladder = new SlabLadder(mutable, TOTAL_INCOME);

            mutable.clear();

            assertThat(ladder.bands()).hasSize(7);
        }

        @Test
        void construction_whenBandsOrBaseIsNull_isRejected() {
            assertThatThrownBy(() -> new SlabLadder(null, TOTAL_INCOME))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new SlabLadder(List.of(), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
