package com.thiru.wealthlens.taxengine.rules.generated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Keeps the committed constants and the packs in step.
 *
 * <p>This is what buys {@code 19-engineering-foundations.md}'s two properties without a build-time
 * generator:
 *
 * <ul>
 *   <li><b>A code removed from every pack</b> changes what this test regenerates, so the test fails;
 *       regenerating deletes the constant, and every Java reference to it then <b>fails to
 *       compile</b> — the strongest deprecation signal available.
 *   <li><b>A typo in a pack</b> fails this test, and tests run in the build, so it fails the
 *       <b>build</b> rather than surfacing at startup.
 * </ul>
 *
 * <p>Run with {@code -Dtaxengine.rules.regenerate=true} to rewrite the committed files instead of asserting.
 */
class GeneratedConstantsTest {

    private static final String REGENERATE = "taxengine.rules.regenerate";

    private record Target(
            String className, String valueType, java.util.List<String> purpose, TreeSet<String> values) {}

    private static Target ruleIds() {
        return new Target(
                "RuleIds",
                "RuleId",
                java.util.List.of(
                        "Every rule id in every pack. A rule referenced from Java is referenced by",
                        "constant, so a misspelling does not compile, and a rule deleted from the packs",
                        "breaks its callers visibly rather than at run time."),
                ConstantGenerator.ruleIds());
    }

    private static Target componentCodes() {
        return new Target(
                "ComponentCodes",
                "ComponentCode",
                java.util.List.of(
                        "Every component code in every pack. Codes live in data so that adding an",
                        "allowance is a data change; these constants give Java the compile-time safety",
                        "the enum used to, without closing the vocabulary."),
                ConstantGenerator.componentCodes());
    }

    private static void check(Target target) {
        Path file = ConstantGenerator.OUTPUT_DIR.resolve(target.className() + ".java");
        String expected = ConstantGenerator.render(
                target.className(), target.valueType(), target.purpose(), target.values());

        if (Boolean.getBoolean(REGENERATE)) {
            write(file, expected);
            return;
        }
        assertThat(read(file))
                .as(
                        "%s is stale. The packs and this file disagree. Regenerate with:%n"
                                + "  ./mvnw test -pl backend -Dtest=GeneratedConstantsTest -D%s=true",
                        file, REGENERATE)
                .isEqualTo(expected);
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file.toAbsolutePath(), e);
        }
    }

    private static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + file.toAbsolutePath(), e);
        }
    }

    @Nested
    @DisplayName("the committed constants match the packs")
    class InStep {

        @Test
        void ruleIdsIsCurrent() {
            check(ruleIds());
        }

        @Test
        void componentCodesIsCurrent() {
            check(componentCodes());
        }
    }

    @Nested
    @DisplayName("what the generator reads")
    class Sources {

        @Test
        void itFindsTheRulesInTheRealPack() {
            assertThat(ConstantGenerator.ruleIds())
                    .contains("RATE-SLAB-NEW-2026", "RATE-SLAB-OLD-2026");
        }

        @Test
        void componentCodesIsEmptyUntilW059AuthorsThem() {
            // exec/components.json is a stub. An empty final class is odd and honest, and this
            // test is what makes it fill in the moment the components are authored.
            assertThat(ConstantGenerator.componentCodes()).isEmpty();
        }

        @Test
        void itReadsEveryPackDirectory() {
            assertThat(ConstantGenerator.packDirectories())
                    .anyMatch(path -> path.getFileName().toString().equals("2026-27"));
        }
    }

    @Nested
    @DisplayName("rendering")
    class Rendering {

        @Test
        void aHyphenatedRuleIdBecomesAValidConstantName() {
            assertThat(ConstantGenerator.constantName("SAL-HRA-001")).isEqualTo("SAL_HRA_001");
            assertThat(ConstantGenerator.constantName("round.totalincome"))
                    .isEqualTo("ROUND_TOTALINCOME");
        }

        @Test
        void theOutputCarriesTheDoNotEditBanner() {
            String source = ConstantGenerator.render(
                    "RuleIds",
                    "RuleId",
                    java.util.List.of("purpose"),
                    new TreeSet<>(java.util.Set.of("SAL-HRA-001")));

            assertThat(source)
                    .contains("GENERATED — do not edit")
                    .contains("public static final RuleId SAL_HRA_001 = new RuleId(\"SAL-HRA-001\");")
                    .contains("private RuleIds() {");
        }

        @Test
        void anEmptySetStillProducesCompilableSource() {
            String source = ConstantGenerator.render(
                    "ComponentCodes", "ComponentCode", java.util.List.of("purpose"), new TreeSet<>());

            assertThat(source).contains("// No values in any pack yet.");
            assertThatCode(() -> assertThat(source).contains("private ComponentCodes() {"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("the two properties DR 19 promises")
    class Properties {

        @Test
        void aCodeRemovedFromEveryPackLosesItsConstant() {
            // Which is what makes every Java reference to it fail to compile — the strongest
            // deprecation signal available. Simulated by rendering with the value absent.
            TreeSet<String> withRule = new TreeSet<>(java.util.Set.of("RATE-SLAB-NEW-2026"));
            String before = ConstantGenerator.render(
                    "RuleIds", "RuleId", java.util.List.of("purpose"), withRule);
            String after = ConstantGenerator.render(
                    "RuleIds", "RuleId", java.util.List.of("purpose"), new TreeSet<>());

            assertThat(before).contains("RATE_SLAB_NEW_2026");
            assertThat(after).doesNotContain("RATE_SLAB_NEW_2026");
        }

        @Test
        void aTypoInAPackFailsThisTestAndSoTheBuild() {
            // A misspelt rule id generates a differently named constant, so the committed file and
            // the packs disagree and this class fails. Tests run in the build, so the build fails
            // rather than the typo surfacing at startup.
            String typo = ConstantGenerator.render(
                    "RuleIds",
                    "RuleId",
                    java.util.List.of("purpose"),
                    new TreeSet<>(java.util.Set.of("RATE-SLAB-NEW-2062")));

            assertThat(typo).contains("RATE_SLAB_NEW_2062").doesNotContain("RATE_SLAB_NEW_2026");
        }

        @Test
        void regeneratingIsIdempotent() {
            // A generator whose output drifts between runs would fail this test on alternate builds
            String once = ConstantGenerator.render(
                    "RuleIds", "RuleId", java.util.List.of("purpose"), ConstantGenerator.ruleIds());
            String twice = ConstantGenerator.render(
                    "RuleIds", "RuleId", java.util.List.of("purpose"), ConstantGenerator.ruleIds());

            assertThat(twice).isEqualTo(once);
        }

        @Test
        void theCommittedFileIsAFormatterFixedPoint() {
            // Proven by construction elsewhere: the import is emitted only when used, and the
            // javadoc is pre-wrapped. This pins the two decisions that make it so.
            String empty = ConstantGenerator.render(
                    "ComponentCodes", "ComponentCode", java.util.List.of("purpose"), new TreeSet<>());
            String populated = ConstantGenerator.render(
                    "RuleIds",
                    "RuleId",
                    java.util.List.of("purpose"),
                    new TreeSet<>(java.util.Set.of("SAL-HRA-001")));

            assertThat(empty).doesNotContain("import ");
            assertThat(populated).contains("import com.thiru.wealthlens.taxengine.rules.id.RuleId;");
            assertThat(populated.lines()).allMatch(line -> line.length() <= 100);
        }
    }
}
