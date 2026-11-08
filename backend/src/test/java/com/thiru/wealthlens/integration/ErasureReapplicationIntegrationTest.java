package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.thiru.wealthlens.auth.entity.UserDetail;
import com.thiru.wealthlens.shared.privacy.AccountErasureRequest;
import com.thiru.wealthlens.taxplanning.jobs.AccountErasure;
import com.thiru.wealthlens.taxplanning.jobs.LogErasure;
import com.thiru.wealthlens.taxplanning.jobs.RetentionPurge;
import com.thiru.wealthlens.taxplanning.jobs.TaxJobRunner;
import com.thiru.wealthlens.taxplanning.profile.ProfileErasure;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import java.time.LocalDateTime;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A restore from a snapshot must not bring an erased person back (W-237f C2a′), on a clock the test sets. A restore is
 * simulated by putting back the rows a snapshot taken before the erasure would hold, while the erasure records, exported
 * before the restore and imported after, stay as they were.
 *
 * <p>Times by hand, with {@code erasure-hold} and {@code log-retention} both P1Y (application-integration-test.yaml): asked at
 * 2027-06-10T08:00Z → erased by the run at 2028-06-10T08:00Z; the snapshot is from 2028-06-01T00:00Z, between the two. An
 * erasure record done at 2028-06-10T08:00Z is kept by a run at 2029-06-10T07:59:59Z and erased at 2029-06-10T08:00:00Z.
 */
class ErasureReapplicationIntegrationTest extends AbstractIntegrationTest {

    private static final String ANU = "anu@example.com";
    private static final String BALA = "bala@example.com";
    private static final String OWNER = "owner@example.com";
    private static final String ASKED = "2027-06-10T08:00:00Z";
    private static final String ERASED = "2028-06-10T08:00:00Z";
    private static final String AFTER_THE_RESTORE = "2028-06-12T09:00:00Z";
    private static final String SNAPSHOT = "2028-06-01T00:00:00";
    private static final String PROFILE = "profile-anu-1";

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
    private AccountErasure accountErasure;

    @Autowired
    private RetentionPurge retentionPurge;

    @Autowired
    private LogErasure logErasure;

    @BeforeEach
    void theDayAnuAsks() {
        clock.set(ASKED);
    }

    /** What a snapshot from before the erasure holds of a person: the login and a holding. */
    private void restoredAccountOf(String email) {
        UserDetail user = new UserDetail();
        user.setEmail(email);
        user.setPassword("not-read-here");
        user.setRoles("USER");
        mongoTemplate.save(user);
        mongoTemplate.getCollection("assets").insertOne(new Document("_id", email + "-asset").append("email", storedIdentifier(email)));
    }

    private long rowsOf(String email) {
        return mongoTemplate.getCollection("assets").countDocuments(new Document("email", storedIdentifier(email)))
                + mongoTemplate.count(query(where("email").is(email)), UserDetail.class);
    }

    private void accountErasedAt(String email, String asked, String erased) {
        restoredAccountOf(email);
        clock.set(asked);
        given().header("Authorization", "Bearer " + generateToken(email)).when().post("/privacy/erasure/user/" + email).then()
                .statusCode(202);
        clock.set(erased);
        runner.run(accountErasure);
    }

    private void restoredProfile(String id) {
        TaxpayerProfile profile = new TaxpayerProfile();
        profile.setId(id);
        profile.setEmail(ANU);
        mongoTemplate.save(profile);
    }

    private void profileErasureAskedByAnu() {
        given().header("Authorization", "Bearer " + generateToken(ANU)).when()
                .post("/tax/user/" + ANU + "/profiles/" + PROFILE + "/erasure").then().statusCode(202);
    }

    private ValidatableResponse reapply(String body, String role) {
        return given().header("Authorization", "Bearer " + generateToken(OWNER, role)).contentType(ContentType.JSON)
                .body(body).when().post("/privacy/erasure/reapply").then();
    }

    private ValidatableResponse reapplySince(String snapshotTakenAt) {
        return reapply("{\"snapshotTakenAt\": \"" + snapshotTakenAt + "\"}", "SUPER_USER");
    }

    private AccountErasureRequest requestOf(String email) {
        return mongoTemplate.findOne(query(where("email").is(email)), AccountErasureRequest.class);
    }

    @Test
    void reapply_whenAnAccountWasErasedAfterTheSnapshot_erasesTheRestoredRowsAndKeepsTheFirstErasedAt() {
        // Given
        accountErasedAt(ANU, ASKED, ERASED);
        assertThat(rowsOf(ANU)).isZero();
        restoredAccountOf(ANU);
        clock.set(AFTER_THE_RESTORE);

        // When
        reapplySince(SNAPSHOT).statusCode(200).body("accountsErased", contains(ANU)).body("accountsHeld", empty());

        // Then
        assertThat(rowsOf(ANU)).isZero();
        assertThat(requestOf(ANU).getErasedAt()).isEqualTo(LocalDateTime.parse("2028-06-10T08:00:00"));
    }

    @Test
    void reapply_whenCalledAgain_changesNothing() {
        // Given
        accountErasedAt(ANU, ASKED, ERASED);
        restoredAccountOf(ANU);
        clock.set(AFTER_THE_RESTORE);
        reapplySince(SNAPSHOT).statusCode(200);

        // When
        reapplySince(SNAPSHOT).statusCode(200).body("accountsErased", contains(ANU));

        // Then
        assertThat(rowsOf(ANU)).isZero();
        assertThat(requestOf(ANU).getErasedAt()).isEqualTo(LocalDateTime.parse("2028-06-10T08:00:00"));
    }

    @Test
    void reapply_whenAnAccountWasErasedBeforeTheSnapshotAndRegisteredAgain_leavesTheNewAccountWhole() {
        // Given — Bala asked 2027-05-01, erased by the run of 2028-05-01, registered again on 2028-05-15
        accountErasedAt(BALA, "2027-05-01T08:00:00Z", "2028-05-01T08:00:00Z");
        clock.set("2028-05-15T08:00:00Z");
        restoredAccountOf(BALA);
        clock.set(AFTER_THE_RESTORE);

        // When
        reapplySince(SNAPSHOT).statusCode(200).body("accountsErased", empty());

        // Then
        assertThat(rowsOf(BALA)).isEqualTo(2);
    }

    @Test
    void reapply_whenAnAccountWasAskedForAfterTheSnapshot_holdsItsRestoredProfileFromTheFirstRequest() {
        // Given — the snapshot predates the request, so the restored profile is not held
        restoredAccountOf(ANU);
        given().header("Authorization", "Bearer " + generateToken(ANU)).when().post("/privacy/erasure/user/" + ANU).then()
                .statusCode(202);
        restoredProfile(PROFILE);
        clock.set("2027-06-12T09:00:00Z");

        // When
        reapplySince("2027-06-01T00:00:00").statusCode(200).body("accountsHeld", contains(ANU));

        // Then
        assertThat(mongoTemplate.findById(PROFILE, TaxpayerProfile.class).getErasureRequestedAt())
                .isEqualTo(LocalDateTime.parse("2027-06-10T08:00:00"));
    }

    @Test
    void reapply_whenAProfileWasErasedAfterTheSnapshot_erasesTheRestoredProfile() {
        // Given
        restoredProfile(PROFILE);
        profileErasureAskedByAnu();
        clock.set(ERASED);
        runner.run(retentionPurge);
        assertThat(mongoTemplate.findById(PROFILE, TaxpayerProfile.class)).isNull();
        restoredProfile(PROFILE);
        clock.set(AFTER_THE_RESTORE);

        // When
        reapplySince(SNAPSHOT).statusCode(200).body("profiles", contains(PROFILE)).body("accountsErased", empty());

        // Then
        assertThat(mongoTemplate.findById(PROFILE, TaxpayerProfile.class)).isNull();
        assertThat(mongoTemplate.findOne(query(where("profile_id").is(PROFILE)), ProfileErasure.class).getErasedAt())
                .isEqualTo(LocalDateTime.parse("2028-06-10T08:00:00"));
    }

    @Test
    void reapply_whenAProfileWasAskedForAfterTheSnapshot_holdsTheRestoredProfileFromTheRequest() {
        // Given
        restoredProfile(PROFILE);
        profileErasureAskedByAnu();
        restoredProfile(PROFILE);
        clock.set("2027-06-12T09:00:00Z");

        // When
        reapplySince("2027-06-01T00:00:00").statusCode(200).body("profiles", contains(PROFILE));

        // Then
        assertThat(mongoTemplate.findById(PROFILE, TaxpayerProfile.class).getErasureRequestedAt())
                .isEqualTo(LocalDateTime.parse("2027-06-10T08:00:00"));
    }

    @Test
    void logRetention_whenAProfileErasureRecordReachesItsYear_erasesItAndNotBefore() {
        // Given
        restoredProfile(PROFILE);
        profileErasureAskedByAnu();
        clock.set(ERASED);
        runner.run(retentionPurge);

        // When — a second before the year
        clock.set("2029-06-10T07:59:59Z");
        runner.run(logErasure);

        // Then
        assertThat(mongoTemplate.count(query(where("profile_id").is(PROFILE)), ProfileErasure.class)).isEqualTo(1);

        // When — at the year
        clock.set("2029-06-10T08:00:00Z");
        runner.run(logErasure);

        // Then
        assertThat(mongoTemplate.count(query(where("profile_id").is(PROFILE)), ProfileErasure.class)).isZero();
    }

    @Test
    void reapply_whenTheTimeIsMissingOrInTheFuture_isRefused() {
        // Given
        clock.set(AFTER_THE_RESTORE);

        // When / Then
        reapply("{}", "SUPER_USER").statusCode(400);
        reapplySince("2028-06-12T09:00:01").statusCode(400);
    }

    @Test
    void reapply_whenNotTheOwner_isForbidden() {
        // When / Then
        reapply("{\"snapshotTakenAt\": \"" + SNAPSHOT + "\"}", "USER").statusCode(403);
    }
}
