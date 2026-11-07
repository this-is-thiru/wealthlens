package com.thiru.wealthlens.taxengine.rules.timeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.pack.PackAssembler;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackLayer;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackRef;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Discovery, lock reading and lock verification, against a synthetic timeline.
 *
 * <p>The fixture is TY 2099-00 — a year that can never collide with a real pack, and the same year
 * {@code 09-build-plan.md} uses for the synthetic-pack acceptance test. Its timeline is one synthetic
 * node: this class is about sourcing and pinning content, not understanding it.
 */
class ClasspathRulePackProviderTest {

    private static final String ROOT = "rulepacks-fixture";
    private static final TaxYearId TY_2099 = new TaxYearId(2099);
    private static final TaxYearId TY_2098 = new TaxYearId(2098);
    private static final TaxYearId TY_2097 = new TaxYearId(2097);

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
    @DisplayName("lock and checksums")
    class Lock {

        @Test
        void itReadsEveryLayersVersionFromTheLock() {
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
            // The 2098-99 lock declares a law checksum the timeline does not compile to — which is
            // what an edit that skipped review looks like. Without this check, every computation
            // made from it would claim a provenance it does not have.
            assertThatThrownBy(() -> provider(new RecordingAssembler(), TY_2098).load(TY_2098))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("LAW layer")
                    .hasMessageContaining("does not match its lock")
                    .hasMessageContaining("with a version bump");
        }

        @Test
        void theFailureNamesBothChecksums() {
            // So the person reading the log can tell a stale lock from an unreviewed edit
            assertThatThrownBy(() -> provider(new RecordingAssembler(), TY_2098).load(TY_2098))
                    .hasMessageContaining("lock says sha256:" + "0".repeat(64))
                    .hasMessageContaining("timeline compiles to sha256:");
        }

        @Test
        void aPackFiledUnderTheWrongYearIsRefused() {
            // The file name says 2097-98, the lock inside says 2099-00
            assertThatThrownBy(() -> provider(new RecordingAssembler(), TY_2097).load(TY_2097))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("is the lock for 2099-00, read as the lock for 2097-98");
        }

        @Test
        void aMissingPackNamesTheFileThatIsAbsent() {
            assertThatThrownBy(() ->
                            new ClasspathRulePackProvider(
                                            "no-such-root", Set.of(TY_2099), new RecordingAssembler())
                                    .load(TY_2099))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("no-such-root/2099-00.lock.json")
                    .hasMessageContaining("not on the classpath");
        }
    }

    /** The shipped years, declared in a manifest beside the locks rather than scanned (W-088s, D-268). */
    @Nested
    @DisplayName("shipped years")
    class ShippedYears {

        @Test
        void shippedYears_whenTheManifestListsTwoYears_readsThem() {
            // When
            Set<TaxYearId> years = ClasspathRulePackProvider.shippedYears("rulepacks");

            // Then — rulepacks/shipped-years.json lists "2025-26" and "2026-27"
            assertThat(years).containsExactlyInAnyOrder(new TaxYearId(2025), new TaxYearId(2026));
        }

        @Test
        void shippedYears_whenALabelIsNotATaxYear_isRefusedNamingIt() {
            assertThatThrownBy(() -> ClasspathRulePackProvider.shippedYears("rulepacks-broken/manifest-not-a-tax-year"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("2025-27")
                    .hasMessageContaining("not a tax year");
        }

        @Test
        void shippedYears_whenAYearIsListedTwice_isRefused() {
            assertThatThrownBy(() -> ClasspathRulePackProvider.shippedYears("rulepacks-broken/manifest-listed-twice"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("2026-27")
                    .hasMessageContaining("twice");
        }

        @Test
        void shippedYears_whenThereIsNoManifest_isRefused() {
            assertThatThrownBy(() -> ClasspathRulePackProvider.shippedYears("no-such-root"))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("no-such-root/shipped-years.json")
                    .hasMessageContaining("not on the classpath");
        }
    }

    /**
     * The application runs from a JAR, where the timeline is an archive entry, not a directory (D-210). The
     * shipped timeline is zipped as a plain JAR lays it out; the Spring Boot JAR's own nested filesystem is
     * proved by the probe in {@code plans/W-079a.md}, since it needs the repackaged application.
     */
    @Nested
    @DisplayName("loading from a JAR")
    class FromJar {

        private static final Path TIMELINE = Path.of("src/main/resources/rulepacks/timeline");

        @TempDir
        Path directory;

        @Test
        void timelineAt_whenTheTimelineIsInAJar_compilesEachYearAsFromTheDirectory() throws IOException {
            // Given
            Path jar = jarOf(TIMELINE, directory.resolve("timeline.jar"));
            URL url = URI.create("jar:" + jar.toUri() + "!/rulepacks/timeline").toURL();

            // When
            Timeline fromJar = ClasspathRulePackProvider.timelineAt(url);

            // Then
            Timeline fromDirectory = TimelineReader.read(TIMELINE);
            for (TaxYearId year : List.of(TaxYearId.of(2025), TaxYearId.of(2026))) {
                assertThat(RulePackCompiler.compile(fromJar, year).files())
                        .as(year.label())
                        .isEqualTo(RulePackCompiler.compile(fromDirectory, year).files());
            }
        }

        @Test
        void timelineAt_whenTheTimelineIsNeitherADirectoryNorAJar_refusesNamingTheLocation() throws IOException {
            // Given
            URL url = URI.create("https://example.invalid/rulepacks/timeline").toURL();

            // When / Then
            assertThatThrownBy(() -> ClasspathRulePackProvider.timelineAt(url))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessage("The timeline is at https://example.invalid/rulepacks/timeline; it is read only from"
                            + " a directory or a JAR on the classpath");
        }

        private static Path jarOf(Path timeline, Path jar) throws IOException {
            try (OutputStream out = Files.newOutputStream(jar);
                    ZipOutputStream zip = new ZipOutputStream(out);
                    Stream<Path> paths = Files.walk(timeline)) {
                for (Path path : paths.sorted().toList()) {
                    String name = "rulepacks/timeline/" + timeline.relativize(path).toString().replace('\\', '/');
                    if (Files.isDirectory(path)) {
                        zip.putNextEntry(new ZipEntry(name.endsWith("/") ? name : name + "/"));
                    } else {
                        zip.putNextEntry(new ZipEntry(name));
                        Files.copy(path, zip);
                    }
                    zip.closeEntry();
                }
            }
            return jar;
        }
    }
}
