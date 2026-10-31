package com.thiru.wealthlens.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Fails the build on a private field or constant that nothing reads (CLAUDE.md §2, "no dead weight").
 *
 * <p>No stock tool sees this codebase's dead fields: Checkstyle 10.26.1 ships no such check; PMD's
 * {@code UnusedPrivateField} ignores every field whose class carries an annotation, which here is
 * every class; SpotBugs reports a field that is written and never read but not a
 * {@code private static final} constant, whose reads javac inlines. So the check is split by what
 * each half can see: bytecode for the fields Lombok does not inline, source text for the constants
 * it does.
 *
 * <p>Two blind spots are stated rather than hidden. A field read only by reflection, with no
 * Lombok-generated accessor, is reported as unread. A constant named only in a comment or a string
 * is counted as read, so a dead constant can still slip through.
 */
class UnusedPrivateFieldTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");

    private static final Pattern PRIVATE_STATIC_FINAL = Pattern.compile(
            "^\\s*private\\s+static\\s+final\\s+.+?\\b([A-Za-z_][A-Za-z0-9_]*)\\s*(?:=|;|\\[).*$");

    /**
     * A constant variable (JLS 4.12.4): primitive or {@code String}, {@code final}, initialised with
     * a literal. javac inlines every read of it — {@code LoginResponse.getTokenType()} compiles to
     * {@code ldc "Bearer"; areturn} — so bytecode holds no access to find.
     */
    private static final Pattern CONSTANT_VARIABLE = Pattern.compile(
            "^\\s*private\\s+final\\s+(?:String|byte|short|int|long|char|float|double|boolean)\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*"
                    + "(?:-[\\d.]+|\\d[\\d._]*[lLfFdD]?|\"[^\"]*\"|'[^']*'|true|false)\\s*;\\s*$");

    @Test
    void privateFields_whenImported_allHaveAReadAccess() {
        // Given
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.thiru.wealthlens");
        Map<Path, List<String>> sources = new HashMap<>();

        // When
        List<String> unread = new ArrayList<>();
        for (JavaClass javaClass : classes) {
            for (JavaField field : javaClass.getFields()) {
                if (isCandidate(field) && !hasReadAccess(field) && !isConstantVariable(field, sources)) {
                    unread.add(javaClass.getName() + "." + field.getName());
                }
            }
        }

        // Then
        assertThat(unread)
                .as("private fields nothing reads — delete the field, or make it a constant variable if the"
                        + " compiler is inlining its reads (CLAUDE.md §2, \"no dead weight\")")
                .isEmpty();
    }

    private static boolean isCandidate(JavaField field) {
        Set<JavaModifier> modifiers = field.getModifiers();
        return modifiers.contains(JavaModifier.PRIVATE)
                && !(modifiers.contains(JavaModifier.STATIC) && modifiers.contains(JavaModifier.FINAL));
    }

    private static boolean hasReadAccess(JavaField field) {
        return field.getAccessesToSelf().stream()
                .anyMatch(access -> access.getAccessType() == JavaFieldAccess.AccessType.GET);
    }

    private static boolean isConstantVariable(JavaField field, Map<Path, List<String>> sources) {
        Path source = MAIN_SOURCES
                .resolve(field.getOwner().getPackageName().replace('.', '/'))
                .resolve(field.getOwner().getSourceCodeLocation().getSourceFileName());
        List<String> lines = sources.computeIfAbsent(source, UnusedPrivateFieldTest::readLines);
        for (String line : lines) {
            Matcher matcher = CONSTANT_VARIABLE.matcher(line);
            if (matcher.matches() && matcher.group(1).equals(field.getName())) {
                return true;
            }
        }
        return false;
    }

    private static List<String> readLines(Path source) {
        try {
            return Files.readAllLines(source);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read " + source + " for a field's declaration", exception);
        }
    }

    @Test
    void privateStaticFinalConstants_whenScanned_areReferencedInTheirFile() throws IOException {
        // Given
        List<Path> files;
        try (Stream<Path> walk = Files.walk(MAIN_SOURCES)) {
            files = walk.filter(path -> path.toString().endsWith(".java")).toList();
        }

        // When
        List<String> unread = new ArrayList<>();
        for (Path file : files) {
            String source = Files.readString(file);
            for (String line : source.lines().toList()) {
                Matcher matcher = PRIVATE_STATIC_FINAL.matcher(line);
                if (matcher.matches() && occurrences(source, matcher.group(1)) == 1) {
                    unread.add(MAIN_SOURCES.relativize(file) + " :: " + matcher.group(1));
                }
            }
        }

        // Then
        assertThat(unread)
                .as("private static final constants referenced nowhere else in their file — a private"
                        + " constant can only be read from its own file, so one occurrence is the declaration"
                        + " alone (CLAUDE.md §2, \"no dead weight\")")
                .isEmpty();
    }

    private static long occurrences(String source, String name) {
        return Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(source).results().count();
    }
}
