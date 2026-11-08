package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.thiru.wealthlens.auth.dto.AuthHelper;
import com.thiru.wealthlens.auth.dto.LoginRequest;
import com.thiru.wealthlens.auth.dto.RegistrationRequest;
import com.thiru.wealthlens.auth.entity.UserDetail;
import com.thiru.wealthlens.shared.privacy.AccountErasureRequest;
import com.thiru.wealthlens.shared.privacy.AccountErasureService;
import com.thiru.wealthlens.shared.privacy.ErasureCause;
import com.thiru.wealthlens.shared.privacy.ErasureReapplication;
import com.thiru.wealthlens.shared.privacy.PersonalDataHolder;
import com.thiru.wealthlens.shared.privacy.PrivacySettings;
import com.thiru.wealthlens.taxplanning.jobs.AccountErasure;
import com.thiru.wealthlens.taxplanning.jobs.TaxJobRunner;
import io.restassured.http.ContentType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Erasure once an account's purpose has lapsed (W-249 C1a – C3a), on a clock the test sets. Times counted by hand from
 * {@code purpose-lapse: P3Y} (application-integration-test.yaml): last signed in 2027-06-10T08:00Z → lapses
 * 2030-06-10T08:00Z, three calendar years later, not a second before; signed in again 2028-01-01T00:00Z → lapses
 * 2031-01-01T00:00Z.
 */
class PurposeLapseIntegrationTest extends AbstractIntegrationTest {

    private static final String ANU = "anu@example.com";
    private static final String BALA = "bala@example.com";
    private static final String PASSWORD = "anu-password-1";
    private static final String SIGNED_IN = "2027-06-10T08:00:00Z";
    private static final String LAPSES = "2030-06-10T08:00:00Z";
    private static final LocalDateTime LAPSED_AT = LocalDateTime.parse("2030-06-10T08:00:00");

    @TestConfiguration
    static class SetDayAndAStoreDownOnce {

        @Bean
        @Primary
        TaxJobsIntegrationTest.SettableClock settableClock() {
            return new TaxJobsIntegrationTest.SettableClock();
        }

        @Bean
        StoreDownOnce storeDownOnce() {
            return new StoreDownOnce();
        }
    }

    /** Fails the next erasure once, just before the login would go, as a store that is down would. */
    static class StoreDownOnce implements PersonalDataHolder {

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
    private AccountErasureService erasures;

    @Autowired
    private StoreDownOnce storeDownOnce;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PrivacySettings privacy;

    private void anAccount(String email, LocalDateTime lastSignedInAt) {
        UserDetail user = new UserDetail();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRoles("USER");
        user.setLastSignedInAt(lastSignedInAt);
        mongoTemplate.save(user);
    }

    private void anuSignsIn() {
        LoginRequest request = new LoginRequest();
        request.setEmail(ANU);
        request.setPassword(PASSWORD);
        given().contentType(ContentType.JSON).body(request).when().post("/auth/login").then().statusCode(200);
    }

    private LocalDateTime lastSignedInAt(String email) {
        return mongoTemplate.findById(email, UserDetail.class).getLastSignedInAt();
    }

    private List<AccountErasureRequest> requestsOf(String email) {
        return mongoTemplate.find(query(where("email").is(email)), AccountErasureRequest.class);
    }

    @Test
    void register_whenAnAccountIsCreated_startsItsCountAtTheRegistration() {
        // Given
        clock.set(SIGNED_IN);
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail(ANU);
        request.setPassword(PASSWORD);
        request.setRole(AuthHelper.Role.USER);
        request.setPrivacyNoticeVersion(privacy.notice().version());

        // When
        given().contentType(ContentType.JSON).body(request).when().post("/auth/register").then().statusCode(200);

        // Then
        assertThat(lastSignedInAt(ANU)).isEqualTo(LocalDateTime.parse("2027-06-10T08:00:00"));
    }

    @Test
    void lapsed_whenOneSecondBeforeTheLapse_erasesNothing() {
        // Given
        anAccount(ANU, LocalDateTime.parse("2027-06-10T08:00:00"));
        clock.set("2030-06-10T07:59:59Z");

        // When
        runner.run(accountErasure);

        // Then
        assertThat(mongoTemplate.findById(ANU, UserDetail.class)).isNotNull();
        assertThat(requestsOf(ANU)).isEmpty();
    }

    @Test
    void lapsed_whenTheLapseIsReached_erasesTheAccountAtOnceAndRecordsItAsALapse() {
        // Given
        anAccount(ANU, LocalDateTime.parse("2027-06-10T08:00:00"));
        anAccount(BALA, LocalDateTime.parse("2027-06-10T08:00:01"));
        clock.set(LAPSES);

        // When
        runner.run(accountErasure);

        // Then — Bala lapses a second later, so is untouched
        assertThat(mongoTemplate.findById(ANU, UserDetail.class)).isNull();
        assertThat(mongoTemplate.findById(BALA, UserDetail.class)).isNotNull();
        assertThat(requestsOf(ANU)).singleElement().satisfies(request -> {
            assertThat(request.getCause()).isEqualTo(ErasureCause.PURPOSE_LAPSED);
            assertThat(request.getRequestedAt()).isEqualTo(LAPSED_AT);
            assertThat(request.getErasedAt()).isEqualTo(LAPSED_AT);
        });
        assertThat(erasures.receipt(ANU).erasedFrom()).isEqualTo(LAPSED_AT);
    }

    @Test
    void lapsed_whenTheJobRunsAfterTheLapse_recordsTheLapseMomentAsTheRequest() {
        // Given
        anAccount(ANU, LocalDateTime.parse("2027-06-10T08:00:00"));
        clock.set("2030-06-11T04:30:00Z");

        // When
        runner.run(accountErasure);

        // Then
        assertThat(requestsOf(ANU)).singleElement().satisfies(request -> {
            assertThat(request.getRequestedAt()).isEqualTo(LAPSED_AT);
            assertThat(request.getErasedAt()).isEqualTo(LocalDateTime.parse("2030-06-11T04:30:00"));
        });
    }

    @Test
    void lapsed_whenRunAgain_changesNothing() {
        // Given
        anAccount(ANU, LocalDateTime.parse("2027-06-10T08:00:00"));
        clock.set(LAPSES);
        runner.run(accountErasure);
        clock.set("2030-06-11T08:00:00Z");

        // When
        runner.run(accountErasure);

        // Then
        assertThat(requestsOf(ANU)).singleElement()
                .satisfies(request -> assertThat(request.getErasedAt()).isEqualTo(LAPSED_AT));
    }

    @Test
    void lapsed_whenTheUserSignsInAgain_movesTheLapseThreeYearsFromThatSignIn() {
        // Given
        anAccount(ANU, LocalDateTime.parse("2027-06-10T08:00:00"));
        clock.set("2028-01-01T00:00:00Z");
        anuSignsIn();
        clock.set(LAPSES);

        // When
        runner.run(accountErasure);

        // Then
        assertThat(mongoTemplate.findById(ANU, UserDetail.class)).isNotNull();
        assertThat(lastSignedInAt(ANU)).isEqualTo(LocalDateTime.parse("2028-01-01T00:00:00"));

        // When — the moved lapse
        clock.set("2031-01-01T00:00:00Z");
        runner.run(accountErasure);

        // Then
        assertThat(mongoTemplate.findById(ANU, UserDetail.class)).isNull();
    }

    @Test
    void lapsed_whenNoSignInIsRecorded_startsCountingAtTheFirstRunAndNeverMovesIt() {
        // Given — an account registered before W-249
        anAccount(ANU, null);
        clock.set("2026-12-01T04:30:00Z");

        // When
        runner.run(accountErasure);
        clock.set("2026-12-02T04:30:00Z");
        runner.run(accountErasure);

        // Then
        assertThat(lastSignedInAt(ANU)).isEqualTo(LocalDateTime.parse("2026-12-01T04:30:00"));
        assertThat(requestsOf(ANU)).isEmpty();
    }

    @Test
    void lapsed_whenTheAccountHasAnOpenRequest_leavesItToThatRequest() {
        // Given — Anu asked on the day of the lapse; the hold ends a year later
        anAccount(ANU, LocalDateTime.parse("2027-06-10T08:00:00"));
        clock.set("2030-06-10T07:00:00Z");
        given().header("Authorization", "Bearer " + generateToken(ANU)).when().post("/privacy/erasure/user/" + ANU).then()
                .statusCode(202);
        clock.set(LAPSES);

        // When
        runner.run(accountErasure);

        // Then
        assertThat(mongoTemplate.findById(ANU, UserDetail.class)).isNotNull();
        assertThat(requestsOf(ANU)).singleElement().satisfies(request -> {
            assertThat(request.getCause()).isEqualTo(ErasureCause.REQUESTED);
            assertThat(request.getErasedAt()).isNull();
        });
    }

    @Test
    void lapsed_whenAStepFails_leavesTheLapseOpenAndTheNextRunCompletesIt() {
        // Given
        anAccount(ANU, LocalDateTime.parse("2027-06-10T08:00:00"));
        clock.set(LAPSES);
        storeDownOnce.failNext.set(true);

        // When
        assertThatIllegalStateException().isThrownBy(() -> runner.run(accountErasure)).withMessage("store unavailable");

        // Then — the login goes last, so it is still there, held
        assertThat(mongoTemplate.findById(ANU, UserDetail.class)).isNotNull();
        assertThat(requestsOf(ANU)).singleElement()
                .satisfies(request -> assertThat(request.getErasedAt()).isNull());

        // When — the next day's run
        clock.set("2030-06-11T08:00:00Z");
        runner.run(accountErasure);

        // Then
        assertThat(mongoTemplate.findById(ANU, UserDetail.class)).isNull();
        assertThat(requestsOf(ANU)).singleElement().satisfies(request -> assertThat(request.getErasedAt())
                .isEqualTo(LocalDateTime.parse("2030-06-11T08:00:00")));
    }

    @Test
    void reapply_whenASnapshotPredatesALapseErasure_erasesTheRestoredAccountAgain() {
        // Given — erased at the lapse, then a snapshot from 2030-06-01 restored
        anAccount(ANU, LocalDateTime.parse("2027-06-10T08:00:00"));
        clock.set(LAPSES);
        runner.run(accountErasure);
        anAccount(ANU, LocalDateTime.parse("2027-06-10T08:00:00"));
        clock.set("2030-07-01T00:00:00Z");

        // When
        erasures.reapply(new ErasureReapplication(LocalDateTime.parse("2030-06-01T00:00:00")));

        // Then
        assertThat(mongoTemplate.findById(ANU, UserDetail.class)).isNull();
    }
}
