package com.thiru.wealthlens.taxengine.rules.timeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The packs the application serves (D-210): every shipped year compiled up front, each with its own lock's
 * instruments, or none at all.
 */
class ShippedRulePacksTest {

    private static final TaxYearId TY_2025 = TaxYearId.of(2025);
    private static final TaxYearId TY_2026 = TaxYearId.of(2026);

    @Test
    void compile_whenBothYearsAreShipped_servesEachUnderItsOwnAct() {
        // Given
        Set<TaxYearId> years = Set.of(TY_2025, TY_2026);

        // When
        ShippedRulePacks packs = ShippedRulePacks.compile("rulepacks", years);

        // Then — the Acts are typed from each lock's instruments block
        assertThat(packs.availableYears()).containsExactlyInAnyOrder(TY_2025, TY_2026);
        assertThat(packs.load(TY_2025).taxYear()).isEqualTo(TY_2025);
        assertThat(packs.load(TY_2025).instruments().act().citation()).isEqualTo("Income-tax Act, 1961 (43 of 1961)");
        assertThat(packs.load(TY_2026).taxYear()).isEqualTo(TY_2026);
        assertThat(packs.load(TY_2026).instruments().act().citation()).isEqualTo("Income-tax Act, 2025 (30 of 2025)");
    }

    @Test
    void compile_whenATimelineDiffersFromItsLock_refusesNamingTheRule() {
        // Given — the real timeline with the 2026-27 lock relabelled, pinning a band rate the timeline no longer has
        Set<TaxYearId> years = Set.of(TaxYearId.of(2094));

        // When / Then
        assertThatThrownBy(() -> ShippedRulePacks.compile("rulepacks-broken/rule-edited-without-lock", years))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("2094-95 does not match its lock")
                .hasMessageContaining("RATE-SLAB-NEW-TAX-REGIME changed");
    }

    @Test
    void compile_whenNoYearIsShipped_refuses() {
        // When / Then
        assertThatThrownBy(() -> ShippedRulePacks.compile("rulepacks", Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An application shipping no tax years cannot compute anything");
    }

    @Test
    void load_whenTheYearIsNotShipped_refusesNamingTheShippedYears() {
        // Given
        ShippedRulePacks packs = ShippedRulePacks.compile("rulepacks", Set.of(TY_2026));

        // When / Then
        assertThatThrownBy(() -> packs.load(TY_2025))
                .isInstanceOf(PackValidationException.class)
                .hasMessage("No pack for 2025-26; the application ships [2026-27]");
    }
}
