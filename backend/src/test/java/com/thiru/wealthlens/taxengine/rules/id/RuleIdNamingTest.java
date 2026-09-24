package com.thiru.wealthlens.taxengine.rules.id;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A rule id or regime code names a concept, never a year, "old/new" or a section number (D-035,
 * D-046 B4, D-051): an unchanged law keeps one identity across years and across the 1961 → 2025 Act
 * move, and the year and section live in the version and the provision instead.
 *
 * <p>The one exception is the 2025 Act's own heading "New tax regime" (s.202, D-051): there "new" is
 * the statute's word, not a relative label. Regime codes separate words with {@code _} and rule ids
 * with {@code -}; both are checked with {@code -}, so one rule covers both.
 *
 * <p>Only the shipped packs are read. {@code rulepacks-broken/} is broken on purpose for other tests,
 * and {@code rulepacks-fixture/} carries no rules or regimes.
 */
class RuleIdNamingTest {

    private static final Path PACK_ROOT = Path.of("src/main/resources/rulepacks");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String STATUTORY_NEW = "NEW-TAX-REGIME";
    private static final Pattern YEAR = Pattern.compile("-(19|20)\\d\\d\\b");
    private static final Pattern OLD_OR_NEW = Pattern.compile("(^|-)(OLD|NEW)(-|$)");
    private static final Pattern SECTION = Pattern.compile("S\\d{3}");

    @Test
    void shippedPacks_whenEveryRuleIdAndRegimeCodeIsRead_noneNamesAYearOldNewOrSection() {
        // Given
        List<Path> packs = packDirectories();
        List<String> identities = new ArrayList<>();
        for (Path pack : packs) {
            identities.addAll(fieldValues(pack.resolve("law/rates.json"), "rules", "ruleId"));
            identities.addAll(fieldValues(pack.resolve("law/regimes.json"), "regimes", "code"));
        }

        // When
        List<String> violations = identities.stream()
                .flatMap(identity -> violation(identity).stream())
                .toList();

        // Then
        assertThat(packs).as("pack directories under %s", PACK_ROOT).isNotEmpty();
        assertThat(identities).as("identities read").isNotEmpty();
        assertThat(violations).isEmpty();
    }

    @Test
    void violation_whenIdCarriesAYear_isReported() {
        // When / Then
        assertThat(violation("RATE-SLAB-X-2026")).isPresent();
    }

    @Test
    void violation_whenIdSaysOldOrNewAsAWord_isReported() {
        // When / Then
        assertThat(violation("RATE-SLAB-OLD")).isPresent();
        assertThat(violation("NEW_REGIME")).isPresent();
        assertThat(violation("REBATE-NEW-001")).isPresent();
    }

    @Test
    void violation_whenIdCarriesASectionNumber_isReported() {
        // When / Then
        assertThat(violation("S202_DEFAULT")).isPresent();
    }

    @Test
    void violation_whenIdUsesTheStatutoryNewTaxRegimeHeading_isNotReported() {
        // When / Then
        assertThat(violation("NEW_TAX_REGIME")).isEmpty();
        assertThat(violation("RATE-SLAB-NEW-TAX-REGIME")).isEmpty();
        assertThat(violation("FIRST_SCHEDULE_RATES")).isEmpty();
    }

    private static Optional<String> violation(String identity) {
        String words = identity.replace('_', '-');
        if (YEAR.matcher(words).find()) {
            return Optional.of(identity + " carries a year");
        }
        if (OLD_OR_NEW.matcher(words.replace(STATUTORY_NEW, "")).find()) {
            return Optional.of(identity + " says OLD or NEW");
        }
        if (SECTION.matcher(words).find()) {
            return Optional.of(identity + " carries a section number");
        }
        return Optional.empty();
    }

    private static List<Path> packDirectories() {
        try (Stream<Path> years = Files.list(PACK_ROOT)) {
            return years.filter(Files::isDirectory).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list " + PACK_ROOT.toAbsolutePath(), e);
        }
    }

    private static List<String> fieldValues(Path file, String arrayField, String idField) {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            JsonNode array = MAPPER.readTree(Files.readString(file)).path(arrayField);
            List<String> values = new ArrayList<>();
            array.forEach(element -> values.add(element.path(idField).asText()));
            return values;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }
}
