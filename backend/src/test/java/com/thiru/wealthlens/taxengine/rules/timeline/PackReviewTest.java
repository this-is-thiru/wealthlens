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
}
