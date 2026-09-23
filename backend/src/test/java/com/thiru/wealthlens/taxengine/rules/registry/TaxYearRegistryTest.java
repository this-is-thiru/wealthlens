package com.thiru.wealthlens.taxengine.rules.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackLayer;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackRef;
import java.net.URI;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The entry point: which years exist, under what law, and what happens to a year that does not. */
class TaxYearRegistryTest {

    private static final TaxYearId TY_2025 = new TaxYearId(2025);
    private static final TaxYearId TY_2026 = new TaxYearId(2026);

    private static Instrument act2025() {
        return Instrument.assented(
                "Income-tax Act, 2025 (30 of 2025)",
                LocalDate.of(2025, 8, 21),
                LocalDate.of(2026, 4, 1),
                "CG-DL-E-22082025-265620",
                URI.create("https://egazette.gov.in/WriteReadData/2025/265620.pdf"),
                VerificationStatus.VERIFIED);
    }

    private static Instrument financeAct2026() {
        return Instrument.assented(
                "Finance Act, 2026 (4 of 2026)",
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 1),
                "CG-DL-E-31032026-271439",
                URI.create("https://egazette.gov.in/WriteReadData/2026/271439.pdf"),
                VerificationStatus.VERIFIED);
    }

    private static Instrument rules2026() {
        return Instrument.notified(
                "Income-tax Rules, 2026",
                LocalDate.of(2026, 4, 1),
                "CG-DL-E-20032026-271092",
                URI.create("https://egazette.gov.in/WriteReadData/2026/271092.pdf"),
                VerificationStatus.VERIFIED);
    }

    private static Map<RulePackLayer, RulePackRef> layers(String year) {
        Map<RulePackLayer, RulePackRef> map = new EnumMap<>(RulePackLayer.class);
        for (RulePackLayer layer : RulePackLayer.values()) {
            String name = layer.name().toLowerCase(java.util.Locale.ROOT);
            map.put(layer, new RulePackRef(layer, year + "." + name + ".1", "sha256:" + "a".repeat(64)));
        }
        return map;
    }

    private static TaxYearRegistryEntry entry(TaxYearId year) {
        return new TaxYearRegistryEntry(
                year,
                new GoverningInstruments(act2025(), financeAct2026(), rules2026()),
                layers(year.label()));
    }

    private static TaxYearRegistry registry(TaxYearId... years) {
        Map<TaxYearId, TaxYearRegistryEntry> map = new LinkedHashMap<>();
        for (TaxYearId year : years) {
            map.put(year, entry(year));
        }
        return new TaxYearRegistry(map);
    }

    @Nested
    @DisplayName("D-REGISTRY — nothing computes without an entry")
    class Resolution {

        @Test
        void aSupportedYearResolves() {
            assertThat(registry(TY_2025, TY_2026).entryFor(TY_2026).taxYear()).isEqualTo(TY_2026);
        }

        @Test
        void anUnsupportedYearNamesTheYearsThatAre() {
            // T9-2: the useful answer to "2031-32 is not supported" is which years are.
            // A bare refusal leaves the caller guessing between too old, too new and misspelt.
            assertThatThrownBy(() -> registry(TY_2025, TY_2026).entryFor(new TaxYearId(2031)))
                    .isInstanceOf(UnsupportedTaxYearException.class)
                    .hasMessageContaining("2031-32 is not supported")
                    .hasMessageContaining("2025-26")
                    .hasMessageContaining("2026-27");
        }

        @Test
        void supportsAnswersWithoutThrowing() {
            TaxYearRegistry registry = registry(TY_2026);

            assertThat(registry.supports(TY_2026)).isTrue();
            assertThat(registry.supports(TY_2025)).isFalse();
            assertThat(registry.supportedYears()).containsExactly(TY_2026);
        }

        @Test
        void anEmptyRegistrySupportsNothingAndSaysSo() {
            assertThatThrownBy(() -> new TaxYearRegistry(Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("supports no computation at all");
        }

        @Test
        void anEntryFiledUnderTheWrongYearIsRejected() {
            Map<TaxYearId, TaxYearRegistryEntry> misfiled = new LinkedHashMap<>();
            misfiled.put(TY_2025, entry(TY_2026));

            assertThatThrownBy(() -> new TaxYearRegistry(misfiled))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("filed under 2025-26 describes 2026-27");
        }
    }

    @Nested
    @DisplayName("three instruments, not one")
    class Instruments {

        @Test
        void theFinanceActIsCarriedSeparately() {
            // It is where RATE-SUR-* and RATE-SUR-MR-001 live. Omitting it makes the provenance of
            // the highest-risk rules in the product unrenderable.
            GoverningInstruments instruments = registry(TY_2026).entryFor(TY_2026).instruments();

            assertThat(instruments.act().citation()).contains("Income-tax Act, 2025");
            assertThat(instruments.financeAct().citation()).contains("Finance Act, 2026");
            assertThat(instruments.rules().citation()).contains("Income-tax Rules, 2026");
        }

        @Test
        void rulesAreNotifiedRatherThanAssented() {
            // Subordinate legislation has a gazette reference and a commencement date, no assent
            assertThat(rules2026().assentedOn()).isEmpty();
            assertThat(act2025().assentedOn()).contains(LocalDate.of(2025, 8, 21));
        }

        @Test
        void anEntryWithoutInstrumentsIsRejected() {
            assertThatThrownBy(() -> new TaxYearRegistryEntry(TY_2026, null, layers("2026-27")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Finance Act");
        }
    }

    @Nested
    @DisplayName("the pack identity a receipt records")
    class PackIdentity {

        @Test
        void everyLayerIsVersioned() {
            assertThat(registry(TY_2026).entryFor(TY_2026).packLayers())
                    .containsOnlyKeys(RulePackLayer.values());
        }

        @Test
        void aMissingLayerIsRejected() {
            // A stored computation must be reproducible under exactly the rules it was made under
            Map<RulePackLayer, RulePackRef> incomplete = layers("2026-27");
            incomplete.remove(RulePackLayer.CONTENT);

            assertThatThrownBy(() ->
                            new TaxYearRegistryEntry(
                                    TY_2026,
                                    new GoverningInstruments(act2025(), financeAct2026(), rules2026()),
                                    incomplete))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CONTENT");
        }
    }
}
