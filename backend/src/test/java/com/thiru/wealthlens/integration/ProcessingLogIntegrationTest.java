package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.thiru.wealthlens.auth.dto.LoginRequest;
import com.thiru.wealthlens.auth.entity.UserDetail;
import com.thiru.wealthlens.shared.privacy.AccountErasureRequest;
import com.thiru.wealthlens.shared.privacy.BreachIntimation;
import com.thiru.wealthlens.shared.privacy.Grievance;
import com.thiru.wealthlens.shared.privacy.PrivacyRecordErasure;
import com.thiru.wealthlens.shared.privacy.ProcessingRecord;
import com.thiru.wealthlens.taxplanning.jobs.LogErasure;
import com.thiru.wealthlens.taxplanning.jobs.TaxJobRunner;
import com.thiru.wealthlens.taxplanning.protection.AccessRecord;
import com.thiru.wealthlens.taxplanning.protection.ReadSubject;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The processing log over HTTP and Mongo, and the year after which logs and privacy records are erased (W-237e, C1a – C4a), on
 * a clock the test sets. Times counted by hand: processed at 2027-06-10T08:00Z with {@code log-retention: P1Y}
 * (application-integration-test.yaml) → erased from 2028-06-10T08:00Z, one calendar year later, not a second before.
 */
class ProcessingLogIntegrationTest extends AbstractIntegrationTest {

    private static final String ANU = "anu@example.com";
    private static final String BALA = "bala@example.com";
    private static final String OWNER = "owner@example.com";
    private static final String PASSWORD = "anu-password-1";
    private static final String PROCESSED = "2027-06-10T08:00:00Z";
    private static final LocalDateTime PROCESSED_UTC = LocalDateTime.parse("2027-06-10T08:00:00");
    private static final String A_SECOND_BEFORE_THE_YEAR = "2028-06-10T07:59:59Z";
    private static final String THE_YEAR_ENDS = "2028-06-10T08:00:00Z";
    private static final String GRIEVANCES_ROUTE = "/privacy/grievances/user/{email}";

    @TestConfiguration
    static class SetDay {

        @Bean
        @Primary
        TaxJobsIntegrationTest.SettableClock settableClock() {
            return new TaxJobsIntegrationTest.SettableClock();
        }
    }

    @Autowired
    private TaxJobsIntegrationTest.SettableClock clock;

    @Autowired
    private TaxJobRunner runner;

    @Autowired
    private LogErasure logErasure;

    @Autowired
    private PrivacyRecordErasure privacyRecordErasure;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void anuHasAnAccountOnTheDay() {
        clock.set(PROCESSED);
        UserDetail user = new UserDetail();
        user.setEmail(ANU);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRoles("USER");
        mongoTemplate.save(user);
    }

    private List<ProcessingRecord> records() {
        return mongoTemplate.findAll(ProcessingRecord.class);
    }

    /**
     * The log once it holds {@code count} records. The filter writes after the response (W-237e), so a read straight
     * after a request can come before the write lands (W-328: 0.8 – 31 ms in a 200-run probe).
     */
    private List<ProcessingRecord> recordsOnceWritten(int count) {
        return await().atMost(Duration.ofSeconds(5)).until(this::records, written -> written.size() >= count);
    }

    private static URI uri(String path) {
        return URI.create(RestAssured.baseURI + ":" + RestAssured.port + path);
    }

    /** A GET by the token's holder, as the other privacy tests send it; answers the status. */
    private static int read(String path, String token) throws IOException, InterruptedException {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri(path)).header("Authorization", "Bearer " + token)
                .GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private void login(String password, int expectedStatus) {
        LoginRequest request = new LoginRequest();
        request.setEmail(ANU);
        request.setPassword(password);
        given().contentType(ContentType.JSON).body(request).when().post("/auth/login").then().statusCode(expectedStatus);
    }

    @Test
    void request_whenAnuReadsHerOwnData_recordsWhoWhoseRouteResultAddressAndTime() throws IOException, InterruptedException {
        // Given / When
        assertThat(read("/privacy/grievances/user/" + ANU, generateToken(ANU))).isEqualTo(200);

        // Then
        assertThat(recordsOnceWritten(1)).singleElement().satisfies(record -> {
            assertThat(record.getPrincipal()).isEqualTo(ANU);
            assertThat(record.getSubjectEmail()).isEqualTo(ANU);
            assertThat(record.getMethod()).isEqualTo("GET");
            assertThat(record.getRoute()).isEqualTo(GRIEVANCES_ROUTE);
            assertThat(record.getStatus()).isEqualTo(200);
            assertThat(record.getRemoteAddress()).isEqualTo("127.0.0.1");
            assertThat(record.getRecordedAt()).isEqualTo(PROCESSED_UTC);
        });
    }

    @Test
    void request_whenAQueryCarriesAValue_storesTheRouteAndNeverTheValue() throws IOException, InterruptedException {
        // Given / When
        assertThat(read("/privacy/grievances/user/" + ANU + "?pan=ABCPE1234F", generateToken(ANU))).isEqualTo(200);

        // Then
        recordsOnceWritten(1);
        Document stored = mongoTemplate.getCollection("privacy_processing_log").find().first();
        assertThat(stored).isNotNull();
        assertThat(stored.toJson()).doesNotContain("ABCPE1234F").contains(GRIEVANCES_ROUTE);
    }

    @Test
    void request_whenTheOwnerReadsBalasData_recordsTheOwnerAsPrincipalAndBalaAsSubject() throws IOException, InterruptedException {
        // Given / When
        assertThat(read("/privacy/grievances/user/" + BALA, generateToken(OWNER, "SUPER_USER"))).isEqualTo(200);

        // Then
        assertThat(recordsOnceWritten(1)).singleElement().satisfies(record -> {
            assertThat(record.getPrincipal()).isEqualTo(OWNER);
            assertThat(record.getSubjectEmail()).isEqualTo(BALA);
        });
    }

    @Test
    void login_whenThePasswordIsWrong_recordsTheAttemptWithTheRefusal() {
        // Given / When — a wrong password is a BadCredentialsException, which ControllerAdviser answers 401
        login("not-anus-password", 401);

        // Then
        assertThat(recordsOnceWritten(1)).singleElement().satisfies(record -> {
            assertThat(record.getPrincipal()).isEqualTo(ANU);
            assertThat(record.getSubjectEmail()).isEqualTo(ANU);
            assertThat(record.getMethod()).isEqualTo("POST");
            assertThat(record.getRoute()).isEqualTo("/auth/login");
            assertThat(record.getStatus()).isEqualTo(401);
        });
    }

    @Test
    void login_whenThePasswordIsRight_recordsTheAttemptWithItsSuccess() {
        // Given / When
        login(PASSWORD, 200);

        // Then
        assertThat(recordsOnceWritten(1)).singleElement().satisfies(record -> {
            assertThat(record.getPrincipal()).isEqualTo(ANU);
            assertThat(record.getStatus()).isEqualTo(200);
        });
    }

    @Test
    void request_whenNobodyIsNamed_recordsNothing() throws IOException, InterruptedException {
        // Given / When — no token: refused, and there is nobody whose access it shows
        assertThat(HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri("/privacy/grievances/user/" + ANU)).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);

        // Then — still nothing after ten times the slowest write measured (W-328)
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(1)).until(this::records, List::isEmpty);
    }

    @Test
    void run_whenAYearHasNotQuitePassed_erasesNothing() {
        // Given
        everyKindProcessedOnTheDay();

        // When
        clock.set(A_SECOND_BEFORE_THE_YEAR);
        runner.run(logErasure);

        // Then
        assertThat(records()).hasSize(1);
        assertThat(mongoTemplate.findAll(AccessRecord.class)).hasSize(1);
        assertThat(mongoTemplate.findAll(Grievance.class)).hasSize(2);
        assertThat(mongoTemplate.findAll(BreachIntimation.class)).hasSize(2);
        assertThat(mongoTemplate.findAll(AccountErasureRequest.class)).hasSize(1);
    }

    @Test
    void run_whenTheYearHasPassed_erasesWhatWasLastProcessedThenAndKeepsTheRest() {
        // Given
        everyKindProcessedOnTheDay();

        // When
        clock.set(THE_YEAR_ENDS);
        runner.run(logErasure);

        // Then — kept: the unanswered grievance, and the intimation emailed a day after the day
        assertThat(records()).isEmpty();
        assertThat(mongoTemplate.findAll(AccessRecord.class)).isEmpty();
        assertThat(mongoTemplate.findAll(Grievance.class)).extracting(Grievance::getId).containsExactly("unanswered");
        assertThat(mongoTemplate.findAll(BreachIntimation.class)).extracting(BreachIntimation::getId)
                .containsExactly("emailed-later");
        assertThat(mongoTemplate.findAll(AccountErasureRequest.class)).isEmpty();
    }

    @Test
    void run_whenRunAgain_changesNothing() {
        // Given
        everyKindProcessedOnTheDay();
        clock.set(THE_YEAR_ENDS);
        runner.run(logErasure);

        // When
        runner.run(logErasure);

        // Then
        assertThat(mongoTemplate.findAll(Grievance.class)).extracting(Grievance::getId).containsExactly("unanswered");
        assertThat(mongoTemplate.findAll(BreachIntimation.class)).extracting(BreachIntimation::getId)
                .containsExactly("emailed-later");
    }

    @Test
    void erase_whenAnusAccountGoes_takesHerOwnRequestsAndKeepsTheOwnersAboutHer() throws IOException, InterruptedException {
        // Given
        assertThat(read("/privacy/grievances/user/" + ANU, generateToken(ANU))).isEqualTo(200);
        assertThat(read("/privacy/grievances/user/" + ANU, generateToken(OWNER, "SUPER_USER"))).isEqualTo(200);
        recordsOnceWritten(2);

        // When — both written first, or a late one would survive the erasure
        privacyRecordErasure.erase(ANU);

        // Then
        assertThat(records()).singleElement().satisfies(record -> {
            assertThat(record.getPrincipal()).isEqualTo(OWNER);
            assertThat(record.getSubjectEmail()).isEqualTo(ANU);
        });
        assertThat(mongoTemplate.find(query(where("principal").is(ANU)), ProcessingRecord.class)).isEmpty();
    }

    /** One of each kind whose last processing was the day, plus the two that must outlast it. */
    private void everyKindProcessedOnTheDay() {
        ProcessingRecord record = new ProcessingRecord();
        record.setId(UUID.randomUUID().toString());
        record.setPrincipal(ANU);
        record.setSubjectEmail(ANU);
        record.setRecordedAt(PROCESSED_UTC);
        mongoTemplate.insert(record);

        AccessRecord read = new AccessRecord();
        read.setId(UUID.randomUUID().toString());
        read.setPrincipal(ANU);
        read.setSubject(ReadSubject.PROFILE);
        read.setReadAt(Instant.parse(PROCESSED));
        mongoTemplate.insert(read);

        mongoTemplate.insert(grievance("answered", PROCESSED_UTC.minusDays(10), PROCESSED_UTC));
        mongoTemplate.insert(grievance("unanswered", LocalDateTime.parse("2026-12-01T00:00:00"), null));

        mongoTemplate.insert(intimation("read-on-the-day", PROCESSED_UTC.minusDays(2), PROCESSED_UTC, null));
        mongoTemplate.insert(intimation("emailed-later", PROCESSED_UTC.minusDays(2), null, PROCESSED_UTC.plusDays(1)));

        AccountErasureRequest erased = new AccountErasureRequest();
        erased.setId(UUID.randomUUID().toString());
        erased.setEmail(BALA);
        erased.setRequestedAt(PROCESSED_UTC.minusYears(1));
        erased.setErasedAt(PROCESSED_UTC);
        mongoTemplate.insert(erased);
    }

    private static Grievance grievance(String id, LocalDateTime receivedAt, LocalDateTime answeredAt) {
        Grievance grievance = new Grievance();
        grievance.setId(id);
        grievance.setEmail(ANU);
        grievance.setReceivedAt(receivedAt);
        grievance.setAnsweredAt(answeredAt);
        return grievance;
    }

    private static BreachIntimation intimation(String id, LocalDateTime postedAt, LocalDateTime readAt,
            LocalDateTime emailedAt) {
        BreachIntimation intimation = new BreachIntimation();
        intimation.setId(id);
        intimation.setEmail(BALA);
        intimation.setPostedAt(postedAt);
        intimation.setReadAt(readAt);
        intimation.setEmailedAt(emailedAt);
        return intimation;
    }
}
