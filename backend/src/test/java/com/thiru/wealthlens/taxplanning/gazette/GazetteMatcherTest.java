package com.thiru.wealthlens.taxplanning.gazette;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.ProvisionCitation;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * The matcher on pages cut from real e-Gazette copies ({@code taxplanning/gazette/SOURCES.md}; W-236 E1a, E3). Whether
 * each is a lead, and the sections it names, were read by hand from the copies
 * ({@code sources/2026-11-07-gazette-watch-window.md}).
 */
class GazetteMatcherTest {

    static final GazetteWatchSettings SETTINGS = new GazetteWatchSettings(false, "http://localhost", "test",
            Duration.ofSeconds(1), Duration.ZERO, 400, 20, DataSize.ofMegabytes(40),
            List.of("central board of direct taxes"), List.of("income-tax act", "department of revenue"));

    /** Rules citing the sections the fixtures name, as the shipped files cite them (ProvisionsCitedTest). */
    private static final List<ProvisionCitation> CITED = List.of(
            new ProvisionCitation("1961", "2", "CG-TERM-LISTED-SECURITY"),
            new ProvisionCitation("1961", "10", "OS-DEATH-GRATUITY"),
            new ProvisionCitation("2025", "19", "SAL-STANDARD-DEDUCTION-NEW-TAX-REGIME"));

    private final GazetteMatcher matcher = new GazetteMatcher(SETTINGS, CITED);

    @Test
    void match_whenTheFinanceActsPageNamesTheBoard_isALeadNamingTheSectionOfTheAct() throws IOException {
        // Given — 271439 p.72: '"Board" means the Central Board of Direct Taxes'; 'clause (9) of section 2 of the
        // Income-tax Act, 1961'
        byte[] page = fixture("finance-act-2026-cbdt-page.pdf");

        // When
        Optional<GazetteMatch> match = matcher.match(page);

        // Then
        assertThat(match).hasValueSatisfying(lead -> {
            assertThat(lead.page()).isEqualTo(1);
            assertThat(lead.matchedTerms()).contains("central board of direct taxes");
            assertThat(lead.possiblyAffected()).contains("1961 s.2 CG-TERM-LISTED-SECURITY");
        });
    }

    @Test
    void match_whenTheRulesEnglishNotification_isALeadWithNoShippedRuleCitingItsSection() throws IOException {
        // Given — 271092 p.1533: 'section 533 of the Income-tax Act, 2025', which no shipped rule cites
        byte[] page = fixture("income-tax-rules-2026-page-1533.pdf");

        // When
        Optional<GazetteMatch> match = matcher.match(page);

        // Then
        assertThat(match).hasValueSatisfying(lead -> {
            assertThat(lead.matchedTerms()).contains("central board of direct taxes");
            assertThat(lead.possiblyAffected()).isEmpty();
        });
    }

    @Test
    void match_whenACbdtNotificationAmendsSection10_isALeadNamingItsRules() throws IOException {
        // Given — 271421: 'clause (46A) of section 10 of the Income-tax Act, 1961'; its English is on page 2
        byte[] copy = fixture("cbdt-notification-271421.pdf");

        // When
        Optional<GazetteMatch> match = matcher.match(copy);

        // Then
        assertThat(match).hasValueSatisfying(lead -> {
            assertThat(lead.page()).isEqualTo(2);
            assertThat(lead.possiblyAffected()).contains("1961 s.10 OS-DEATH-GRATUITY");
        });
    }

    @Test
    void match_whenARevenueNoticeNamesOnlyAFinanceAct_isNotALead() throws IOException {
        // Given — 271497: the Department of Revenue, a Finance Act, no income-tax and no Board
        byte[] copy = fixture("revenue-notice-271497.pdf");

        // When / Then
        assertThat(matcher.match(copy)).isEmpty();
    }

    @Test
    void match_whenAHealthMinistryNotification_isNotALead() throws IOException {
        // Given — 271093
        byte[] copy = fixture("health-notification-271093.pdf");

        // When / Then
        assertThat(matcher.match(copy)).isEmpty();
    }

    static byte[] fixture(String name) throws IOException {
        try (InputStream in = GazetteMatcherTest.class.getResourceAsStream("/taxplanning/gazette/" + name)) {
            return in.readAllBytes();
        }
    }
}
