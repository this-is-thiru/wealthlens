package com.thiru.wealthlens.taxengine.rules.timeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Each consecutive pair of shipped years against its committed review file (W-139, C1a): a change to the law fails here until
 * the file is rewritten and read, in the same commit. {@code -Dpack.review.write=true} rewrites it.
 */
class PackReviewTest {

    private static final Path TIMELINE = Path.of("src/main/resources/rulepacks/timeline");
    private static final Path REVIEWS = Path.of("../docs/taxplanning-build/reviews");

    @Test
    void render_whenTheShippedYearsAreCompiled_matchesEachCommittedReviewFile() throws IOException {
        // Given
        Timeline timeline = TimelineReader.read(TIMELINE);
        List<TaxYearId> years = ClasspathRulePackProvider.shippedYears("rulepacks").stream()
                .sorted(Comparator.comparingInt(TaxYearId::startYear)).toList();
        boolean write = Boolean.getBoolean("pack.review.write");

        for (int i = 1; i < years.size(); i++) {
            // When
            String review = PackReview.render(RulePackCompiler.compile(timeline, years.get(i - 1)),
                    RulePackCompiler.compile(timeline, years.get(i)));
            Path file = REVIEWS.resolve(PackReview.fileName(years.get(i - 1), years.get(i)));
            if (write) {
                Files.writeString(file, review);
            }

            // Then
            assertThat(file).as("%s is missing or stale: rerun with -Dpack.review.write=true and read the change", file)
                    .exists();
            assertThat(Files.readString(file))
                    .as("%s is stale: rerun with -Dpack.review.write=true and read the change", file).isEqualTo(review);
        }
    }

    private static String shippedReview() {
        Timeline timeline = TimelineReader.read(TIMELINE);
        return PackReview.render(RulePackCompiler.compile(timeline, new TaxYearId(2025)),
                RulePackCompiler.compile(timeline, new TaxYearId(2026)));
    }

    private static String section(String review, String heading) {
        int start = review.indexOf("\n## " + heading);
        int end = review.indexOf("\n## ", start + 1);
        return review.substring(start, end < 0 ? review.length() : end);
    }

    @Test
    void render_whenTheShippedYearsAreCompared_splitsTheirSuccessionsIntoThirtyOneLawChangesAndOneHundredEightyNineResourced() {
        // When — the counts a script over every rule file found (plans/W-139a.md)
        String review = shippedReview();

        // Then
        assertThat(review).contains("## Law changed at the boundary — 31 version(s)",
                "## Re-sourced only — 189 version(s)");
        assertThat(section(review, "Law changed").lines().filter(line -> line.startsWith("- `")).count()).isEqualTo(31);
    }

    @Test
    void render_whenARuleIsLawChanged_showsTheLeafAndItsInstrumentAndAResourcedRuleOnlyByName() {
        // When
        String review = shippedReview();

        // Then — PERQ-LOAN-PETTY's 2026 version, read in its rule file: cap.amount 20000 → 200000, Rules 2026 r.15(5)
        assertThat(section(review, "Law changed")).contains("`PERQ-LOAN-PETTY` 2025-04-01 → 2026-04-01: ",
                "`cap.amount` 20000 → 200000", "introduced by: Income-tax Rules, 2026, G.S.R. 198(E), rule 15(5)");
        // ADV-INSTALMENTS: both versions state the same law, only validity, introducedBy and provenance differ
        assertThat(section(review, "Re-sourced only")).contains("`ADV-INSTALMENTS`");
        assertThat(section(review, "Law changed")).doesNotContain("ADV-INSTALMENTS");
        assertThat(section(review, "Other changes")).doesNotContain("ADV-INSTALMENTS");
    }
}
