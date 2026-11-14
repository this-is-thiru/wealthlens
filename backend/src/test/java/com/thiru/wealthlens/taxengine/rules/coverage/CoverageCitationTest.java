package com.thiru.wealthlens.taxengine.rules.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.temporal.Provision;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.timeline.Timeline;
import com.thiru.wealthlens.taxengine.rules.timeline.TimelineReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The register against the rules' citations (W-184): a MODELLED row names the rules that carry its
 * section out, and each of those rules must name that section — or the pairing must stand in
 * {@code coverage/citation-baseline.json} with the reason it stands. Every rule that names a MODELLED
 * section must in turn be named by some MODELLED row, so no rule the engine can reach is invisible to
 * the register.
 *
 * <p>The baseline is the exception list, and it may only shrink: it is compared as an exact set, so a
 * mismatch missing from it fails as a new defect and an entry that no longer mismatches fails as stale.
 */
class CoverageCitationTest {

    private static final Path TIMELINE = Path.of("src/main/resources/rulepacks/timeline");
    private static final Path BASELINE = Path.of("src/test/resources/coverage/citation-baseline.json");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void listings_whenAModelledRowNamesARule_restOnACitationOrOnABaselinedReason() {
        // Given
        Timeline timeline = TimelineReader.read(TIMELINE);

        // When
        Set<String> unbacked = listingsWithoutACitation(timeline);

        // Then
        assertThat(unbacked)
                .as("each listing must name its row's section, or stand in %s with its reason", BASELINE)
                .isEqualTo(baselined("listed"));
    }

    @Test
    void everyRule_whenItNamesAModelledSection_isNamedBySomeModelledRow() {
        // Given
        Timeline timeline = TimelineReader.read(TIMELINE);

        // When
        Set<String> orphans = orphanedRules(timeline);

        // Then
        assertThat(orphans)
                .as("a rule that names a MODELLED section must be named by a MODELLED row, or stand in %s", BASELINE)
                .isEqualTo(baselined("orphaned"));
    }

    /** Every (row, rule) pairing where the row is MODELLED and the rule names none of the row's sections. */
    private static Set<String> listingsWithoutACitation(Timeline timeline) {
        CoverageRegister register = CoverageRegister.of(timeline);
        Set<String> unbacked = new TreeSet<>();
        register.sections().forEach((section, coverage) -> {
            if (!(coverage instanceof Coverage.Modelled modelled)) {
                return;
            }
            modelled.rules().stream()
                    .filter(rule -> !names(timeline.rules().get(rule), section))
                    .forEach(rule -> unbacked.add(section.act() + "|s." + section.section() + "|" + rule));
        });
        return unbacked;
    }

    private static boolean names(RuleRow row, SectionRef section) {
        return row != null
                && row.versions().stream()
                        .flatMap(version -> SectionRef.namedBy(provisionFor(version, section.act())).stream())
                        .anyMatch(section::equals);
    }

    /** Every rule named by no register row at all, where a section the rule names is MODELLED. */
    private static Set<String> orphanedRules(Timeline timeline) {
        CoverageRegister register = CoverageRegister.of(timeline);
        Set<RuleId> named = new HashSet<>();
        register.sections().forEach((section, coverage) -> {
            if (coverage instanceof Coverage.Modelled modelled) {
                named.addAll(modelled.rules());
            }
        });
        Set<String> orphans = new TreeSet<>();
        timeline.rules().forEach((rule, row) -> {
            if (named.contains(rule)) {
                return;
            }
            boolean namesAModelledSection = row.versions().stream()
                    .flatMap(version -> Stream.concat(
                            SectionRef.namedBy(version.provision1961()).stream(),
                            SectionRef.namedBy(version.provision2025()).stream()))
                    .anyMatch(section -> register.sections().get(section) instanceof Coverage.Modelled);
            if (namesAModelledSection) {
                orphans.add(rule.toString());
            }
        });
        return orphans;
    }

    private static Provision provisionFor(RuleVersion version, SectionRef.Act act) {
        return act == SectionRef.Act.INCOME_TAX_ACT_1961 ? version.provision1961() : version.provision2025();
    }

    /**
     * The baseline's keys for one of its two lists. An absent file reads as an empty list: the first run
     * then reports every live mismatch by name, and a deleted baseline fails rather than passing.
     */
    private static Set<String> baselined(String field) {
        if (!Files.exists(BASELINE)) {
            return new TreeSet<>();
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(Files.readString(BASELINE));
        } catch (IOException e) {
            throw new UncheckedIOException(BASELINE + " is unreadable", e);
        }
        JsonNode entries = root.path(field);
        Set<String> keys = new TreeSet<>();
        for (JsonNode entry : entries) {
            assertThat(entry.path("reason").asText())
                    .as("every entry of %s lists %s states why it stands, in its reason", BASELINE, field)
                    .isNotBlank();
            keys.add(field.equals("orphaned")
                    ? entry.path("rule").asText()
                    : entry.path("act").asText() + "|s." + entry.path("section").asText() + "|"
                            + entry.path("rule").asText());
        }
        assertThat(keys).as("%s lists %s names an entry twice", BASELINE, field).hasSize(entries.size());
        return keys;
    }
}
