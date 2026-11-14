package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;
import com.sun.net.httpserver.HttpServer;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxplanning.gazette.GazetteCopies;
import com.thiru.wealthlens.taxplanning.gazette.GazetteCursor;
import com.thiru.wealthlens.taxplanning.gazette.GazetteCursorRepository;
import com.thiru.wealthlens.taxplanning.gazette.GazetteLead;
import com.thiru.wealthlens.taxplanning.gazette.GazetteLeadRepository;
import com.thiru.wealthlens.taxplanning.gazette.GazetteWatch;
import com.thiru.wealthlens.taxplanning.gazette.GazetteWatchSettings;
import com.thiru.wealthlens.taxplanning.jobs.JobContext;
import com.thiru.wealthlens.taxplanning.jobs.JobFailureRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;

/**
 * The Gazette watch end to end against a local e-Gazette and the test Mongo (W-236): where the cursor moves, when a run
 * stops, the year roll, a second run adding nothing, a failure holding the cursor, and a copy too large to read. No test
 * here reaches egazette.gov.in; the endpoints are {@code GazetteWatchEndpointIntegrationTest}'s.
 */
class GazetteWatchIntegrationTest extends AbstractIntegrationTest {

    private static final String LEAD_TEXT = "MINISTRY OF FINANCE (Department of Revenue) (CENTRAL BOARD OF DIRECT TAXES)"
            + " NOTIFICATION. In exercise of the powers conferred by section 10 of the Income-tax Act, 1961.";

    private static final String OTHER_TEXT = "MINISTRY OF HEALTH AND FAMILY WELFARE NOTIFICATION.";

    private static final Clock IN_2026 = Clock.fixed(Instant.parse("2026-11-07T00:00:00Z"), ZoneOffset.UTC);

    private static final Clock IN_2027 = Clock.fixed(Instant.parse("2027-01-05T00:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private GazetteCursorRepository cursors;

    @Autowired
    private GazetteLeadRepository leads;

    @Autowired
    private JobFailureRepository failures;

    @Autowired
    private TaxCalculator calculator;

    private final Map<String, byte[]> published = new ConcurrentHashMap<>();

    private HttpServer gazette;

    @BeforeEach
    void startGazette() throws IOException {
        published.clear();
        gazette = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        gazette.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            byte[] body = published.get(path);
            int status = path.endsWith("/500.pdf") ? 500 : body == null ? 404 : 200;
            byte[] answer = body == null ? "<html>not found</html>".getBytes() : body;
            exchange.sendResponseHeaders(status, answer.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(answer);
            }
        });
        gazette.start();
    }

    @AfterEach
    void stopGazette() {
        gazette.stop(0);
    }

    @Test
    void run_whenNoCursorIsSeeded_refusesAndReadsNothing() {
        // When
        watch(DataSize.ofMegabytes(40)).run(context(IN_2026));

        // Then
        assertThat(failures.findAll()).singleElement()
                .satisfies(failure -> assertThat(failure.getReason()).contains("no cursor"));
        assertThat(leads.findAll()).isEmpty();
    }

    @Test
    void run_whenCopiesFollowTheCursor_recordsTheLeadsAndStopsAfterThreeMisses() throws DocumentException {
        // Given — 101 a lead, 102 not, 103 missing, 104 a lead, then nothing
        seed(2026, 100);
        publish(2026, 101, LEAD_TEXT);
        publish(2026, 102, OTHER_TEXT);
        publish(2026, 104, LEAD_TEXT);

        // When
        watch(DataSize.ofMegabytes(40)).run(context(IN_2026));

        // Then — the cursor at the last copy read; two leads, each naming the Board and section 10's rules
        assertThat(cursors.findById(GazetteCursor.ID)).hasValueSatisfying(cursor -> {
            assertThat(cursor.getYear()).isEqualTo(2026);
            assertThat(cursor.getLastNumber()).isEqualTo(104);
        });
        assertThat(leads.findAll()).extracting(GazetteLead::getNumber).containsExactlyInAnyOrder(101L, 104L);
        assertThat(leads.findAll()).allSatisfy(lead -> {
            assertThat(lead.getPage()).isEqualTo(1);
            assertThat(lead.getMatchedTerms()).contains("central board of direct taxes");
            assertThat(lead.getPossiblyAffected()).contains("1961 s.10 OS-DEATH-GRATUITY");
        });
    }

    @Test
    void run_whenRunAgainOverTheSameNumbers_addsNoSecondLead() throws DocumentException {
        // Given — a first run, then the cursor set back
        seed(2026, 100);
        publish(2026, 101, LEAD_TEXT);
        watch(DataSize.ofMegabytes(40)).run(context(IN_2026));
        seed(2026, 100);

        // When
        watch(DataSize.ofMegabytes(40)).run(context(IN_2026));

        // Then
        assertThat(leads.findAll()).hasSize(1);
        assertThat(cursors.findById(GazetteCursor.ID).orElseThrow().getLastNumber()).isEqualTo(101);
    }

    @Test
    void run_whenTheYearHasTurned_continuesTheNumbersInTheNextYearsFolder() throws DocumentException {
        // Given — 2026's folder ends at 104; 105 is published in 2027's
        seed(2026, 104);
        publish(2027, 105, LEAD_TEXT);

        // When
        watch(DataSize.ofMegabytes(40)).run(context(IN_2027));

        // Then
        assertThat(cursors.findById(GazetteCursor.ID)).hasValueSatisfying(cursor -> {
            assertThat(cursor.getYear()).isEqualTo(2027);
            assertThat(cursor.getLastNumber()).isEqualTo(105);
        });
        assertThat(leads.findAll()).extracting(GazetteLead::getUrl).singleElement().asString().endsWith("/2027/105.pdf");
    }

    @Test
    void run_whenACopyCannotBeFetched_failsWithTheCursorBeforeIt() throws DocumentException {
        // Given — 499 published, 500 answers 500
        seed(2026, 498);
        publish(2026, 499, OTHER_TEXT);

        // When / Then
        assertThatThrownBy(() -> watch(DataSize.ofMegabytes(40)).run(context(IN_2026)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("/2026/500.pdf answered 500");
        assertThat(cursors.findById(GazetteCursor.ID).orElseThrow().getLastNumber()).isEqualTo(499);
    }

    @Test
    void run_whenACopyIsLargerThanTheWatchReads_recordsALeadToOpenByHand() throws DocumentException {
        // Given — a 100-byte limit, which any PDF passes
        seed(2026, 100);
        publish(2026, 101, OTHER_TEXT);

        // When
        watch(DataSize.ofBytes(100)).run(context(IN_2026));

        // Then
        assertThat(leads.findAll()).singleElement().satisfies(lead -> {
            assertThat(lead.getPage()).isZero();
            assertThat(lead.getNote()).contains("too large to read");
        });
    }

    private GazetteWatch watch(DataSize maxDocumentSize) {
        GazetteWatchSettings settings = new GazetteWatchSettings(true,
                "http://localhost:" + gazette.getAddress().getPort(), "WealthLens-GazetteWatch/test",
                Duration.ofSeconds(5), Duration.ZERO, 50, 3, maxDocumentSize,
                List.of("central board of direct taxes"), List.of("income-tax act", "department of revenue"));
        return new GazetteWatch(cursors, leads, new GazetteCopies(RestClient.create(), settings), settings, calculator);
    }

    private JobContext context(Clock clock) {
        return new JobContext("gazette-watch", clock, () -> true, failures);
    }

    private void seed(int year, long lastNumber) {
        GazetteCursor cursor = new GazetteCursor();
        cursor.setId(GazetteCursor.ID);
        cursor.setYear(year);
        cursor.setLastNumber(lastNumber);
        cursors.save(cursor);
    }

    private void publish(int year, long number, String text) throws DocumentException {
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, pdf);
        document.open();
        document.add(new Paragraph(text));
        document.close();
        published.put("/" + year + "/" + number + ".pdf", pdf.toByteArray());
    }
}
