package com.thiru.wealthlens.taxrules.pack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxrules.id.TaxYearId;
import com.thiru.wealthlens.taxrules.validate.PackValidationException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Discovery, manifest reading and checksum verification, against a synthetic pack.
 *
 * <p>The fixture is TY 2099-00 — a year that can never collide with a real pack, and the same year
 * {@code 09-build-plan.md} uses for the synthetic-pack acceptance test. Its files are deliberately
 * near-empty: this item is about sourcing bytes, not understanding them.
 */
class ClasspathRulePackProviderTest {

    private static final String ROOT = "rulepacks-fixture";
    private static final TaxYearId TY_2099 = new TaxYearId(2099);
    private static final TaxYearId TY_2098 = new TaxYearId(2098);

    /** Records what the provider hands over, and returns a pack built from nothing. */
    private static final class RecordingAssembler implements PackAssembler {

        final AtomicReference<Map<RulePackLayer, RulePackRef>> layers = new AtomicReference<>();
        final AtomicReference<Map<String, String>> contents = new AtomicReference<>();

        @Override
        public RulePack assemble(
                TaxYearId taxYear,
                Map<RulePackLayer, RulePackRef> declaredLayers,
                Map<String, String> layerContents) {
            layers.set(declaredLayers);
            contents.set(layerContents);
            return null; // this item does not build packs; W-033 does
        }
    }

    private static ClasspathRulePackProvider provider(PackAssembler assembler, TaxYearId... years) {
        return new ClasspathRulePackProvider(ROOT, Set.of(years), assembler);
    }

    @Nested
    @DisplayName("discovery")
    class Discovery {

        @Test
        void itServesTheYearsItWasDeclaredWith() {
            assertThat(provider(new RecordingAssembler(), TY_2099).availableYears())
                    .containsExactly(TY_2099);
        }

        @Test
        void aYearItDoesNotServeFailsWithWhatItDoesServe() {
            assertThatThrownBy(() -> provider(new RecordingAssembler(), TY_2099).load(new TaxYearId(2026)))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("2026-27")
                    .hasMessageContaining("2099-00");
        }

        @Test
        void aProviderServingNoYearsIsRejected() {
            assertThatThrownBy(() -> new ClasspathRulePackProvider(ROOT, Set.of(), new RecordingAssembler()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot compute anything");
        }
    }

    @Nested
    @DisplayName("manifest and checksums")
    class Manifest {

        @Test
        void itReadsEveryLayersVersionFromTheManifest() {
            RecordingAssembler assembler = new RecordingAssembler();

            provider(assembler, TY_2099).load(TY_2099);

            assertThat(assembler.layers.get())
                    .containsOnlyKeys(RulePackLayer.values())
                    .extractingByKey(RulePackLayer.LAW)
                    .extracting(RulePackRef::version)
                    .isEqualTo("2099-00.law.3");
        }

        @Test
        void itHandsTheAssemblerEveryLayersContent() {
            RecordingAssembler assembler = new RecordingAssembler();

            provider(assembler, TY_2099).load(TY_2099);

            assertThat(assembler.contents.get())
                    .containsKeys(
                            "law/rates.json",
                            "law/regimes.json",
                            "exec/pipelines.json",
                            "exec/components.json",
                            "forms/itr-forms.json",
                            "content/explanations.json");
        }

        @Test
        void aLayerEditedWithoutABumpIsRefused() {
            // The 2098-99 fixture declares a law checksum its bytes do not hash to — which is what
            // a layer edited in place on a server looks like. Without this check, every computation
            // made from it would claim a provenance it does not have.
            assertThatThrownBy(() -> provider(new RecordingAssembler(), TY_2098).load(TY_2098))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("LAW")
                    .hasMessageContaining("does not match its declared checksum")
                    .hasMessageContaining("without a version bump");
        }

        @Test
        void theFailureNamesBothChecksums() {
            // So the person reading the log can tell a tampered file from a stale manifest
            assertThatThrownBy(() -> provider(new RecordingAssembler(), TY_2098).load(TY_2098))
                    .hasMessageContaining("sha256:" + "0".repeat(64))
                    .hasMessageContaining("content hashes to sha256:");
        }

        @Test
        void aPackFiledUnderTheWrongYearIsRefused() {
            // The directory says one year, the manifest says another
            assertThatThrownBy(() -> provider(new RecordingAssembler(), TY_2098)
                            .load(TY_2098))
                    .isInstanceOf(PackValidationException.class);
        }

        @Test
        void aMissingPackNamesTheFileThatIsAbsent() {
            assertThatThrownBy(() ->
                            new ClasspathRulePackProvider(
                                            "no-such-root", Set.of(TY_2099), new RecordingAssembler())
                                    .load(TY_2099))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("no-such-root/2099-00/manifest.json")
                    .hasMessageContaining("not on the classpath");
        }
    }
}
