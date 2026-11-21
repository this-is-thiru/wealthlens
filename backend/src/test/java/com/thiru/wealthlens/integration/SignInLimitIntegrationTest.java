package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.auth.entity.UserDetail;
import com.thiru.wealthlens.shared.privacy.AccountErasureRequest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sign-in hardening (W-358d2, D-884): guessing is limited by address and client, so a guesser is slowed while nobody can lock a person
 * out, and registration no longer tells a stranger whether an address has an account. On a clock the test sets; each case starts on a
 * later day than every case run before it (JUnit does not run them in source order), so no count is ever in a case's future. The per-client limit is the shipped 30 here (the integration profile raises it for the other classes).
 */
@TestPropertySource(properties = "app.auth.sign-in-limit.calls-per-client=30")
class SignInLimitIntegrationTest extends AbstractIntegrationTest {

    private static final String KNOWN = "known@example.com";
    private static final String RIGHT = "right-password-1";
    private static final String LOGIN = "/api/v1/public/auth/login";
    private static final String REGISTER = "/api/v1/public/auth/register";
    private static final AtomicInteger DAYS = new AtomicInteger();

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
    private PasswordEncoder passwordEncoder;

    @Test
    void login_whenFivePasswordsWereWrong_answers429UntilTheWindowPasses() {
        // Given — five wrong passwords for one address from this client, at one moment
        Instant day = aFreshDay();
        known();
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(login(KNOWN, "wrong").statusCode()).isEqualTo(401);
        }

        // When
        Response sixth = login(KNOWN, "wrong");
        Response rightButBlocked = login(KNOWN, RIGHT);
        clock.set(day.plus(Duration.ofMinutes(15)).toString());
        Response afterTheWindow = login(KNOWN, RIGHT);

        // Then — refused until 15 minutes after the first failure, even with the right password
        assertThat(sixth.statusCode()).isEqualTo(429);
        assertThat(sixth.header("Retry-After")).isEqualTo("900");
        assertThat(rightButBlocked.statusCode()).isEqualTo(429);
        assertThat(afterTheWindow.statusCode()).isEqualTo(200);
    }

    @Test
    void login_whenAnotherAddressFailedFiveTimes_stillSignsThisOneIn() {
        // Given — someone guessing at another address cannot lock this one out
        aFreshDay();
        known();
        for (int attempt = 0; attempt < 5; attempt++) {
            login("other@example.com", "wrong");
        }

        // When / Then
        assertThat(login(KNOWN, RIGHT).statusCode()).isEqualTo(200);
    }

    @Test
    void login_whenSignedInAfterFailures_startsCountingAgain() {
        // Given — four failures, a sign-in, four more
        aFreshDay();
        known();
        for (int attempt = 0; attempt < 4; attempt++) {
            login(KNOWN, "wrong");
        }
        assertThat(login(KNOWN, RIGHT).statusCode()).isEqualTo(200);
        for (int attempt = 0; attempt < 4; attempt++) {
            login(KNOWN, "wrong");
        }

        // When / Then — the success cleared the first four, so a fifth wrong password is still only wrong
        assertThat(login(KNOWN, "wrong").statusCode()).isEqualTo(401);
    }

    @Test
    void authRoutes_whenOneClientCallsThirtyOneTimes_answers429() {
        // Given
        aFreshDay();
        for (int call = 0; call < 30; call++) {
            assertThat(refresh().statusCode()).isEqualTo(401);
        }

        // When
        Response thirtyFirst = refresh();

        // Then
        assertThat(thirtyFirst.statusCode()).isEqualTo(429);
        assertThat(thirtyFirst.header("Retry-After")).isEqualTo("900");
    }

    @Test
    void register_whenTheAddressIsTaken_answersWithoutNamingIt() throws IOException, InterruptedException {
        // Given
        aFreshDay();
        known();

        // When
        Response answer = register(KNOWN, version());

        // Then
        assertThat(answer.statusCode()).isEqualTo(400);
        assertThat(answer.jsonPath().getString("detail")).isEqualTo("This email cannot be registered").doesNotContain(KNOWN);
    }

    @Test
    void register_whenTheAddressIsHeldForErasure_answersAsWhenTaken() throws IOException, InterruptedException {
        // Given — the login already gone, the erasure not yet recorded done (W-237d)
        aFreshDay();
        AccountErasureRequest held = new AccountErasureRequest();
        held.setEmail("held@example.com");
        held.setRequestedAt(LocalDateTime.parse("2027-01-01T08:00:00"));
        mongoTemplate.save(held);

        // When
        Response answer = register("held@example.com", version());

        // Then — no word of an erasure
        assertThat(answer.statusCode()).isEqualTo(400);
        assertThat(answer.jsonPath().getString("detail")).isEqualTo("This email cannot be registered");
    }

    @Test
    void register_whenTheVersionIsWrong_saysSoBeforeLookingAtTheAddress() {
        // Given — a taken address and a wrong notice version
        aFreshDay();
        known();

        // When
        Response answer = register(KNOWN, "not-the-version");

        // Then — the same answer a free address gets, so the version check reveals nothing
        assertThat(answer.statusCode()).isEqualTo(400);
        assertThat(answer.jsonPath().getString("detail")).contains("privacy notice").doesNotContain("cannot be registered");
    }

    private Instant aFreshDay() {
        Instant day = Instant.parse("2027-01-01T08:00:00Z").plus(Duration.ofDays(DAYS.incrementAndGet()));
        clock.set(day.toString());
        return day;
    }

    private void known() {
        UserDetail user = new UserDetail();
        user.setEmail(KNOWN);
        user.setPassword(passwordEncoder.encode(RIGHT));
        user.setRoles("USER");
        mongoTemplate.save(user);
    }

    private static Response login(String email, String password) {
        return given().contentType(ContentType.JSON).body("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .when().post(LOGIN);
    }

    private static Response refresh() {
        return given().contentType(ContentType.JSON).body("{\"refresh_token\":\"not-a-token\"}").when().post("/api/v1/public/auth/token/refresh");
    }

    private static Response register(String email, String noticeVersion) {
        return given().contentType(ContentType.JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"p-1\",\"privacyNoticeVersion\":\"" + noticeVersion + "\"}")
                .when().post(REGISTER);
    }

    /** Read with java.net.http: RestAssured's GET meets a Groovy NullPointerException in this suite (as in PropertySaleIntegrationTest). */
    private static String version() throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            String body = client.send(HttpRequest.newBuilder(URI.create(RestAssured.baseURI + ":" + RestAssured.port + "/api/v1/public/privacy/rights"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString()).body();
            return JsonMapper.builder().build().readTree(body).get("version").asString();
        }
    }
}
