package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Grievance redressal over HTTP and Mongo (W-237b, C1a – C4a), on a clock the test sets. The dates are counted by hand:
 * with {@code response-days: 30} (application-integration-test.yaml), a grievance received at 2027-06-01T10:00Z is to be
 * answered by 2027-07-01T10:00Z (June has 30 days); one received a day later, by 2027-07-02T10:00Z.
 */
class GrievanceIntegrationTest extends AbstractIntegrationTest {

    private static final String USER = "user@example.com";
    private static final String OTHER = "other@example.com";
    private static final String OWNER = "owner@example.com";

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

    @BeforeEach
    void theDayOfTheFirstGrievance() {
        clock.set("2027-06-01T10:00:00Z");
    }

    private ValidatableResponse raise(String tokenFor, String pathEmail, String text) {
        return given().header("Authorization", "Bearer " + generateToken(tokenFor, "USER")).contentType(ContentType.JSON)
                .body("{\"text\": \"" + text + "\"}")
                .when().post("/privacy/grievances/user/" + pathEmail).then();
    }

    private ValidatableResponse answer(String grievanceId, String role, String text) {
        return given().header("Authorization", "Bearer " + generateToken(OWNER, role)).contentType(ContentType.JSON)
                .body("{\"text\": \"" + text + "\"}")
                .when().post("/privacy/grievances/" + grievanceId + "/answer").then();
    }

    /** RestAssured's GET fails inside Groovy here, so reads go through the JDK client, as PrivacyNoticeIntegrationTest's do. */
    private HttpResponse<String> read(String path, String email, String role) throws IOException, InterruptedException {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create(RestAssured.baseURI + ":" + RestAssured.port + path))
                .header("Authorization", "Bearer " + generateToken(email, role)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode json(HttpResponse<String> response) {
        return JsonMapper.builder().build().readTree(response.body());
    }

    @Test
    void raise_whenAUserRaisesAGrievance_answersWithTheDatePromisedAndTheContact() {
        // When / Then
        raise(USER, USER, "My old address is still shown").statusCode(201)
                .body("email", equalTo(USER))
                .body("text", equalTo("My old address is still shown"))
                .body("receivedAt", equalTo("2027-06-01T10:00:00"))
                .body("respondBy", equalTo("2027-07-01T10:00:00"))
                .body("answer", nullValue())
                .body("contact", equalTo("privacy@example.com"));
    }

    @Test
    void raise_whenTheTokenIsAnotherUsers_isRefusedAndNothingIsStored() throws IOException, InterruptedException {
        // When / Then
        raise(OTHER, USER, "Not mine to raise").statusCode(401);
        HttpResponse<String> mine = read("/privacy/grievances/user/" + USER, USER, "USER");
        assertThat(mine.statusCode()).isEqualTo(200);
        assertThat(json(mine).size()).isZero();
    }

    @Test
    void raise_whenThereIsNoToken_isRefused() {
        // When / Then — only the notice is public
        given().contentType(ContentType.JSON).body("{\"text\": \"anonymous\"}")
                .when().post("/privacy/grievances/user/" + USER).then().statusCode(401);
    }

    @Test
    void raise_whenTheTextIsBlank_isABadRequest() {
        // When / Then
        raise(USER, USER, " ").statusCode(400);
    }

    @Test
    void unanswered_whenTwoAreOpenAndOneAnswered_listsTheOpenOnesDueSoonestFirst() throws IOException, InterruptedException {
        // Given — received 06-01, 06-02 and 06-03; the first is answered
        String first = raise(USER, USER, "first").statusCode(201).extract().path("id");
        clock.set("2027-06-03T10:00:00Z");
        raise(OTHER, OTHER, "third").statusCode(201);
        clock.set("2027-06-02T10:00:00Z");
        raise(USER, USER, "second").statusCode(201);
        answer(first, "SUPER_USER", "Corrected").statusCode(200);

        // When / Then
        HttpResponse<String> queue = read("/privacy/grievances", OWNER, "SUPER_USER");
        assertThat(queue.statusCode()).isEqualTo(200);
        assertThat(json(queue).findValuesAsString("text")).containsExactly("second", "third");
        assertThat(json(queue).findValuesAsString("respondBy")).containsExactly("2027-07-02T10:00:00", "2027-07-03T10:00:00");
    }

    @Test
    void unanswered_whenNotASuperUser_isForbidden() throws IOException, InterruptedException {
        // When / Then
        assertThat(read("/privacy/grievances", USER, "USER").statusCode()).isEqualTo(403);
    }

    @Test
    void answer_whenGiven_isReadByItsAuthorWithItsTimeAndASecondAnswerIsRefused() throws IOException, InterruptedException {
        // Given
        String id = raise(USER, USER, "Please correct my name").statusCode(201).extract().path("id");
        clock.set("2027-06-20T09:30:00Z");

        // When
        answer(id, "SUPER_USER", "Corrected on 20 June").statusCode(200)
                .body("answer.text", equalTo("Corrected on 20 June"))
                .body("answer.answeredAt", equalTo("2027-06-20T09:30:00"))
                .body("contact", equalTo("privacy@example.com"));

        // Then
        HttpResponse<String> mine = read("/privacy/grievances/user/" + USER, USER, "USER");
        assertThat(mine.statusCode()).isEqualTo(200);
        assertThat(json(mine).get(0).get("answer").get("text").asString()).isEqualTo("Corrected on 20 June");
        assertThat(json(mine).get(0).get("respondBy").asString()).isEqualTo("2027-07-01T10:00:00");
        assertThat(json(mine).size()).isOne();
        answer(id, "SUPER_USER", "Again").statusCode(400);
    }

    @Test
    void answer_whenNotASuperUser_isForbidden() {
        // Given
        String id = raise(USER, USER, "Mine").statusCode(201).extract().path("id");

        // When / Then
        answer(id, "USER", "I answer myself").statusCode(403);
    }
}
