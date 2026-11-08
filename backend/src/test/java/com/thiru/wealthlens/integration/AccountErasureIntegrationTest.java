package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.thiru.wealthlens.auth.dto.LoginRequest;
import com.thiru.wealthlens.auth.dto.RegistrationRequest;
import com.thiru.wealthlens.auth.entity.UserDetail;
import com.thiru.wealthlens.shared.privacy.AccountErasureRequest;
import com.thiru.wealthlens.shared.privacy.BreachNotice;
import com.thiru.wealthlens.shared.privacy.PersonalDataHolder;
import com.thiru.wealthlens.taxplanning.jobs.AccountErasure;
import com.thiru.wealthlens.taxplanning.jobs.TaxJobRunner;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Erasure of a whole account over HTTP and Mongo (W-237d, C1a – C7a), on a clock the test sets. Times counted by hand: asked
 * at 2027-06-10T08:00Z with {@code erasure-hold: P1Y} (application-integration-test.yaml) → erased from 2028-06-10T08:00Z, one
 * calendar year later, not a second before.
 *
 * <p>The guard: every {@code @Document} in the app with a field naming a person ({@code email}, or {@code principal} for the
 * access log) is given a row for Anu and one for Bala; after the erasure none of Anu's is left and all of Bala's are. A new
 * per-user collection that no {@link PersonalDataHolder} erases fails here without anyone listing it.
 */
class AccountErasureIntegrationTest extends AbstractIntegrationTest {

    private static final String ANU = "anu@example.com";
    private static final String BALA = "bala@example.com";
    private static final String OWNER = "owner@example.com";
    private static final String PASSWORD = "anu-password-1";
    private static final String ASKED = "2027-06-10T08:00:00Z";
    private static final String HOLD_ENDS = "2028-06-10T08:00:00Z";

    /** Fields that name the person a row belongs to. */
    private static final List<String> PERSON_FIELDS = List.of("email", "principal");

    /** The record that the erasure was done (C2a): it outlives the account by design. */
    private static final String KEPT = "privacy_erasure_requests";

    @TestConfiguration
    static class SetDayAndAFailingHolder {

        @Bean
        @Primary
        TaxJobsIntegrationTest.SettableClock settableClock() {
            return new TaxJobsIntegrationTest.SettableClock();
        }

        @Bean
        FailingHolder failingHolder() {
            return new FailingHolder();
        }
    }

    /** Fails the next erasure once, just before the login would go, as a store that is down would. */
    static class FailingHolder implements PersonalDataHolder {

        private final AtomicBoolean failNext = new AtomicBoolean();

        /** Stands in for a store the notice already describes. */
        @Override
        public String noticePart() {
            return "portfolio";
        }

        @Override
        public void erase(String email) {
            if (failNext.getAndSet(false)) {
                throw new IllegalStateException("store unavailable");
            }
        }

        @Override
        public int getOrder() {
            return LOWEST_PRECEDENCE - 1;
        }
    }

    @Autowired
    private TaxJobsIntegrationTest.SettableClock clock;

    @Autowired
    private TaxJobRunner runner;

    @Autowired
    private AccountErasure accountErasure;

    @Autowired
    private FailingHolder failingHolder;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void twoAccountsOnTheDayAnuAsks() {
        clock.set(ASKED);
        for (String email : List.of(ANU, BALA)) {
            UserDetail user = new UserDetail();
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(PASSWORD));
            user.setRoles("USER");
            mongoTemplate.save(user);
        }
    }

    private ValidatableResponse requestErasure() {
        return given().header("Authorization", "Bearer " + generateToken(ANU)).when().post("/privacy/erasure/user/" + ANU)
                .then();
    }

    private ValidatableResponse login() {
        LoginRequest request = new LoginRequest();
        request.setEmail(ANU);
        request.setPassword(PASSWORD);
        return given().contentType(ContentType.JSON).body(request).when().post("/auth/login").then();
    }

    /** RestAssured's GET throws inside Groovy here, so reads go through the JDK client (as in W-237b). */
    private HttpResponse<String> receiptAsOwner() throws IOException, InterruptedException {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create(RestAssured.baseURI + ":" + RestAssured.port + "/privacy/erasure/user/" + ANU))
                .header("Authorization", "Bearer " + generateToken(OWNER, "SUPER_USER")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Each per-person collection, and the field in it that names the person. */
    private Map<String, String> personCollections() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(org.springframework.data.mongodb.core.mapping.Document.class));
        Map<String, String> found = new TreeMap<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.thiru.wealthlens")) {
            MongoPersistentEntity<?> entity = mongoTemplate.getConverter().getMappingContext()
                    .getRequiredPersistentEntity(classOf(candidate));
            PERSON_FIELDS.stream().map(entity::getPersistentProperty).filter(Objects::nonNull)
                    .map(MongoPersistentProperty::getFieldName).findFirst()
                    .ifPresent(field -> found.put(entity.getCollection(), field));
        }
        found.remove(KEPT);
        return found;
    }

    private static Class<?> classOf(BeanDefinition candidate) {
        try {
            return Class.forName(candidate.getBeanClassName());
        } catch (ClassNotFoundException missing) {
            throw new IllegalStateException(missing);
        }
    }

    private void aRowInEveryCollectionFor(String email) {
        personCollections().forEach((collection, field) -> {
            if (count(collection, field, email) == 0) {
                // A string id, as the entities' own ids are, so the app reads and deletes the row as one of its own; and a
                // value of its own in each other uniquely indexed field, so two seeded rows never collide on an absent one.
                Document row = "_id".equals(field) ? new Document(field, email)
                        : new Document("_id", UUID.randomUUID().toString()).append(field, email);
                for (Document index : mongoTemplate.getCollection(collection).listIndexes()) {
                    if (Boolean.TRUE.equals(index.getBoolean("unique"))) {
                        index.get("key", Document.class).keySet().stream().filter(key -> !row.containsKey(key))
                                .forEach(key -> row.append(key, UUID.randomUUID().toString()));
                    }
                }
                mongoTemplate.getCollection(collection).insertOne(row);
            }
        });
    }

    private long count(String collection, String field, String email) {
        return mongoTemplate.getCollection(collection).countDocuments(new Document(field, email));
    }

    private Map<String, Long> rowsOf(String email) {
        Map<String, Long> rows = new TreeMap<>();
        personCollections().forEach((collection, field) -> rows.put(collection, count(collection, field, email)));
        return rows;
    }

    private void aBreachNoticeToBoth() {
        BreachNotice notice = new BreachNotice();
        notice.setId("notice-1");
        notice.setAffected(new ArrayList<>(List.of(ANU, BALA)));
        mongoTemplate.save(notice);
    }

    private AccountErasureRequest theRequest() {
        return mongoTemplate.findOne(query(where("email").is(ANU)), AccountErasureRequest.class);
    }

    @Test
    void request_whenAsked_answers202WithTheHoldsEndAndTheContact() {
        // When / Then — 08:00 plus one calendar year
        requestErasure().statusCode(202).body("email", equalTo(ANU)).body("requestedAt", equalTo("2027-06-10T08:00:00"))
                .body("erasedFrom", equalTo("2028-06-10T08:00:00")).body("contact", equalTo("privacy@example.com"));
    }

    @Test
    void request_whenAskedTwice_keepsTheFirstRequest() {
        // Given
        requestErasure().statusCode(202);
        clock.set("2027-06-11T08:00:00Z");

        // When — the owner asks again on Anu's path, since Anu's token no longer works
        given().header("Authorization", "Bearer " + generateToken(OWNER, "SUPER_USER")).when()
                .post("/privacy/erasure/user/" + ANU).then().statusCode(202)
                .body("requestedAt", equalTo("2027-06-10T08:00:00"));

        // Then
        assertThat(mongoTemplate.count(query(where("email").is(ANU)), AccountErasureRequest.class)).isEqualTo(1);
    }

    @Test
    void request_whenHeld_refusesTheTokenTheLoginAndARegistrationAtOnce() {
        // Given
        login().statusCode(200);
        requestErasure().statusCode(202);
        RegistrationRequest again = new RegistrationRequest();
        again.setEmail(ANU);
        again.setPassword(PASSWORD);

        // When / Then — the token was issued before the request and has not expired
        given().header("Authorization", "Bearer " + generateToken(ANU)).contentType(ContentType.JSON)
                .body("{\"text\": \"Where is my data?\"}").when().post("/privacy/grievances/user/" + ANU).then().statusCode(401);
        login().statusCode(401);
        given().contentType(ContentType.JSON).body(again).when().post("/auth/register").then().statusCode(400);
    }

    @Test
    void request_whenTheAccountHasATaxProfile_holdsItFromTheMomentOfTheRequest() {
        // Given
        TaxpayerProfile profile = new TaxpayerProfile();
        profile.setEmail(ANU);
        mongoTemplate.save(profile);

        // When
        requestErasure().statusCode(202);

        // Then
        assertThat(mongoTemplate.findById(profile.getId(), TaxpayerProfile.class).getErasureRequestedAt())
                .isEqualTo(LocalDateTime.parse("2027-06-10T08:00:00"));
    }

    @Test
    void erase_whenTheHoldHasNotPassed_erasesNothing() {
        // Given
        aRowInEveryCollectionFor(ANU);
        requestErasure().statusCode(202);
        Map<String, Long> beforeTheRun = rowsOf(ANU);
        clock.set("2028-06-10T07:59:59Z");

        // When
        runner.run(accountErasure);

        // Then — the request itself adds a processing-log row (W-237e), so the count is compared, not assumed to be one
        assertThat(rowsOf(ANU)).isEqualTo(beforeTheRun);
        assertThat(beforeTheRun).allSatisfy((collection, rows) -> assertThat(rows).as(collection).isPositive());
        assertThat(theRequest().getErasedAt()).isNull();
    }

    @Test
    void erase_whenTheHoldHasPassed_leavesNothingOfThePersonInAnyCollectionAndAllOfTheOthers()
            throws IOException, InterruptedException {
        // Given
        aRowInEveryCollectionFor(ANU);
        aRowInEveryCollectionFor(BALA);
        aBreachNoticeToBoth();
        requestErasure().statusCode(202);
        clock.set(HOLD_ENDS);

        // When
        runner.run(accountErasure);

        // Then
        assertThat(rowsOf(ANU)).allSatisfy((collection, rows) -> assertThat(rows).as(collection).isZero());
        assertThat(rowsOf(BALA)).allSatisfy((collection, rows) -> assertThat(rows).as(collection).isEqualTo(1));
        assertThat(mongoTemplate.findById("notice-1", BreachNotice.class).getAffected()).containsExactly(BALA);
        assertThat(theRequest().getErasedAt()).isEqualTo(LocalDateTime.parse("2028-06-10T08:00:00"));
        HttpResponse<String> receipt = receiptAsOwner();
        assertThat(receipt.statusCode()).isEqualTo(200);
        JsonNode body = JsonMapper.builder().build().readTree(receipt.body());
        assertThat(body.path("erasedFrom").asString()).isEqualTo("2028-06-10T08:00:00");
        assertThat(body.path("contact").asString()).isEqualTo("privacy@example.com");
    }

    @Test
    void erase_whenRunAgain_changesNothing() {
        // Given
        aRowInEveryCollectionFor(ANU);
        aRowInEveryCollectionFor(BALA);
        requestErasure().statusCode(202);
        clock.set(HOLD_ENDS);
        runner.run(accountErasure);
        LocalDateTime erasedAt = theRequest().getErasedAt();
        clock.set("2028-06-11T08:00:00Z");

        // When
        runner.run(accountErasure);

        // Then
        assertThat(rowsOf(ANU)).allSatisfy((collection, rows) -> assertThat(rows).as(collection).isZero());
        assertThat(rowsOf(BALA)).allSatisfy((collection, rows) -> assertThat(rows).as(collection).isEqualTo(1));
        assertThat(theRequest().getErasedAt()).isEqualTo(erasedAt);
    }

    @Test
    void erase_whenAStepFails_leavesTheRequestOpenAndTheNextRunCompletesIt() {
        // Given
        aRowInEveryCollectionFor(ANU);
        requestErasure().statusCode(202);
        clock.set(HOLD_ENDS);
        failingHolder.failNext.set(true);

        // When
        assertThatIllegalStateException().isThrownBy(() -> runner.run(accountErasure)).withMessage("store unavailable");

        // Then — the login goes last, so it is still there, and the account is still held
        assertThat(theRequest().getErasedAt()).isNull();
        assertThat(mongoTemplate.findById(ANU, UserDetail.class)).isNotNull();
        login().statusCode(401);

        // When — the next run
        clock.set("2028-06-11T08:00:00Z");
        runner.run(accountErasure);

        // Then
        assertThat(rowsOf(ANU)).allSatisfy((collection, rows) -> assertThat(rows).as(collection).isZero());
        assertThat(theRequest().getErasedAt()).isEqualTo(LocalDateTime.parse("2028-06-11T08:00:00"));
    }
}
