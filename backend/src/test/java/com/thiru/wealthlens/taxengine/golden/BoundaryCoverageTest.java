package com.thiru.wealthlens.taxengine.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.golden.BoundaryCaseGenerator.Boundary;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Every boundary the shipped packs have is either worked by hand in a T1/T2 case or listed as awaiting one (W-140, C3a):
 * a law change that moves a threshold fails here until a person works the new case. And the {@code DR 08} boundary cases
 * are among those the generator finds ({@code 16-getting-started.md:372}).
 */
class BoundaryCoverageTest {

    private static final String AWAITING = "taxengine/golden/boundaries-awaiting-figures.json";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static List<Boundary> generated;
    private static List<GoldenCase> handCases;

    /** One awaiting entry, as the file states it. */
    private record Awaiting(RegimeCode regime, TaxYearId year, Money totalIncome, int ageAtLeast) {
    }

    @BeforeAll
    static void generateEveryShippedYear() {
        BoundaryCaseGenerator generator = new BoundaryCaseGenerator(new GoldenCaseRunner());
        generated = ClasspathRulePackProvider.shippedYears("rulepacks").stream()
                .sorted(Comparator.comparingInt(TaxYearId::startYear))
                .flatMap(year -> generator.generate(year).stream()).toList();
        handCases = Stream.concat(GoldenCase.loadAll("taxengine/golden/T1").stream(),
                GoldenCase.loadAll("taxengine/golden/T2").stream()).toList();
    }

    @Test
    void generate_whenTheShippedPacksAreRead_findsEveryDr08BoundaryCase() {
        // Given — the cases DR 08 states as boundaries: a ceiling, one step above, a crossover pair, an age variant's ceiling
        Set<String> boundaryCases = Set.of("T1-N1", "T1-N2", "T1-N4a", "T1-N4b", "T1-O1", "T1-O2", "T1-A1b", "T1-A1c",
                "T1-A1d", "T2-1", "T2-2", "T2-3a", "T2-3b", "T2-4a", "T2-5");

        // When / Then
        List<String> missed = new ArrayList<>();
        for (GoldenCase golden : handCases) {
            if (boundaryCases.contains(golden.id())) {
                golden.taxYears().stream().filter(year -> generated.stream().noneMatch(coveredBy(golden, year)))
                        .forEach(year -> missed.add(golden.id() + " in " + year.label()));
            }
        }
        assertThat(missed).as("DR 08 boundary cases the generator did not find").isEmpty();
    }

    @Test
    void generate_whenTheShippedPacksAreRead_findsNoBoundaryWithoutAHandCaseOrAnAwaitingEntry() {
        // Given
        List<Awaiting> awaiting = awaiting();

        // When
        List<String> uncovered = generated.stream()
                .filter(boundary -> handCases.stream().noneMatch(golden -> coveredBy(golden, boundary.year()).test(boundary)))
                .filter(boundary -> !awaiting.contains(key(boundary)))
                .map(BoundaryCoverageTest::describe).distinct().toList();

        // Then
        assertThat(uncovered).as("boundaries with no T1/T2 case: work one by hand, or list it in %s with why", AWAITING)
                .isEmpty();
    }

    @Test
    void awaiting_whenListed_isStillABoundaryWithNoHandCase() {
        // Given
        Set<Awaiting> stillGenerated = generated.stream().map(BoundaryCoverageTest::key).collect(Collectors.toSet());

        // When / Then — a stale entry hides nothing, but it misleads: remove it with the case that answers it
        assertThat(awaiting()).as("entries in %s that no longer name an uncovered boundary", AWAITING)
                .allMatch(stillGenerated::contains)
                .noneMatch(entry -> handCases.stream().anyMatch(golden -> golden.taxYears().contains(entry.year())
                        && golden.regime().equals(entry.regime()) && golden.totalIncome().compareTo(entry.totalIncome()) == 0
                        && ageClass(golden, entry.regime(), entry.year()) == entry.ageAtLeast()));
    }

    private static Predicate<Boundary> coveredBy(GoldenCase golden, TaxYearId year) {
        return boundary -> boundary.year().equals(year) && golden.taxYears().contains(year)
                && boundary.regime().equals(golden.regime()) && boundary.totalIncome().compareTo(golden.totalIncome()) == 0
                && boundary.ageAtLeast() == ageClass(golden, golden.regime(), year);
    }

    /** The age the hand case's slab ladder starts at: the highest age variant the generator found at or below its age. */
    private static int ageClass(GoldenCase golden, RegimeCode regime, TaxYearId year) {
        int age = golden.ageAtYearEnd().orElse(0);
        return generated.stream().filter(boundary -> boundary.regime().equals(regime) && boundary.year().equals(year))
                .mapToInt(Boundary::ageAtLeast).filter(start -> start <= age).max().orElse(0);
    }

    private static Awaiting key(Boundary boundary) {
        return new Awaiting(boundary.regime(), boundary.year(), boundary.totalIncome(), boundary.ageAtLeast());
    }

    private static String describe(Boundary boundary) {
        return "{\"regime\": \"%s\", \"taxYear\": \"%s\", \"totalIncome\": \"%s\", \"ageAtLeast\": %d, \"why\": \"%s (%s)\"}"
                .formatted(boundary.regime().value(), boundary.year().label(), boundary.totalIncome(),
                        boundary.ageAtLeast(), boundary.kind(), boundary.rule().value());
    }

    private static List<Awaiting> awaiting() {
        try (InputStream in = BoundaryCoverageTest.class.getClassLoader().getResourceAsStream(AWAITING)) {
            JsonNode file = MAPPER.readTree(in);
            List<Awaiting> entries = new ArrayList<>();
            file.path("boundaries").forEach(entry -> entries.add(new Awaiting(new RegimeCode(entry.path("regime").asString()),
                    GoldenCase.taxYear(entry.path("taxYear").asString()), Money.of(entry.path("totalIncome").asString()),
                    entry.path("ageAtLeast").asInt())));
            return entries;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + AWAITING, e);
        }
    }
}
