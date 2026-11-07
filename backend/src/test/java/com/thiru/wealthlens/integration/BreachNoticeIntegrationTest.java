package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import com.thiru.wealthlens.auth.entity.UserDetail;
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
 * Breach intimation over HTTP and Mongo (W-237c, C1a – C4a), on a clock the test sets. Times counted by hand: aware at
 * 2027-06-10T08:00Z with {@code board-report-hours: 72} (application-integration-test.yaml) → the Board's report is due
 * 2027-06-13T08:00Z (three days of 24 hours); the notice is posted at 2027-06-10T09:00Z.
 */
class BreachNoticeIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER = "owner@example.com";
    private static final String ANU = "anu@example.com";
    private static final String BALA = "bala@example.com";
    private static final String CHITRA = "chitra@example.com";
    private static final String TEXT = """
            {"description": "On 9 June a backup was readable without a password for six hours",
             "consequences": "Your email and holdings may have been seen",
             "mitigation": "The backup is closed and its key replaced",
             "safetyMeasures": "Watch for messages asking for your password"}""";

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
    void threeRegisteredUsersOnTheDayOfPosting() {
        for (String email : new String[] {CHITRA, ANU, BALA}) {
            UserDetail user = new UserDetail();
            user.setEmail(email);
            user.setRoles("USER");
            mongoTemplate.save(user);
        }
        clock.set("2027-06-10T09:00:00Z");
    }

    private ValidatableResponse superUser(String path, String body) {
        return given().header("Authorization", "Bearer " + generateToken(OWNER, "SUPER_USER")).contentType(ContentType.JSON)
                .body(body).when().post("/privacy/breach-notices" + path).then();
    }

    private String postedToEveryone() {
        return superUser("", "{\"text\": " + TEXT + ", \"becameAwareAt\": \"2027-06-10T08:00:00\","
                + " \"scope\": \"EVERY_REGISTERED_USER\"}").statusCode(201).extract().path("id");
    }

    /** RestAssured's GET throws inside Groovy here, so reads go through the JDK client (as in W-237b). */
    private HttpResponse<String> read(String path, String email, String role) throws IOException, InterruptedException {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create(RestAssured.baseURI + ":" + RestAssured.port + "/privacy/breach-notices" + path))
                .header("Authorization", "Bearer " + generateToken(email, role)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode json(HttpResponse<String> response) {
        return JsonMapper.builder().build().readTree(response.body());
    }

    @Test
    void post_whenEveryRegisteredUserIsAffected_reachesEachAndDatesTheBoardsReport() {
        // When / Then
        superUser("", "{\"text\": " + TEXT + ", \"becameAwareAt\": \"2027-06-10T08:00:00\","
                + " \"scope\": \"EVERY_REGISTERED_USER\"}").statusCode(201)
                .body("affected", contains(ANU, BALA, CHITRA))
                .body("postedAt", equalTo("2027-06-10T09:00:00"))
                .body("board.reportDueAt", equalTo("2027-06-13T08:00:00"))
                .body("board.firstIntimatedAt", nullValue())
                .body("board.reportSentAt", nullValue());
    }

    @Test
    void post_whenListed_reachesOnlyThoseNamed() {
        // When / Then
        superUser("", "{\"text\": " + TEXT + ", \"becameAwareAt\": \"2027-06-10T08:00:00\", \"scope\": \"LISTED\","
                + " \"emails\": [\"" + BALA + "\", \"" + BALA + "\"]}").statusCode(201)
                .body("affected", contains(BALA));
    }

    @Test
    void post_whenAPartOfRuleSevenOneIsMissing_isABadRequest() {
        // When / Then
        superUser("", "{\"text\": {\"description\": \"a\", \"consequences\": \"b\", \"mitigation\": \"c\"},"
                + " \"becameAwareAt\": \"2027-06-10T08:00:00\", \"scope\": \"EVERY_REGISTERED_USER\"}").statusCode(400);
    }

    @Test
    void post_whenAwarenessIsInTheFuture_isABadRequest() {
        // When / Then — the clock reads 09:00
        superUser("", "{\"text\": " + TEXT + ", \"becameAwareAt\": \"2027-06-10T09:00:01\","
                + " \"scope\": \"EVERY_REGISTERED_USER\"}").statusCode(400);
    }

    @Test
    void post_whenNotASuperUser_isForbidden() {
        // When / Then
        given().header("Authorization", "Bearer " + generateToken(ANU)).contentType(ContentType.JSON)
                .body("{\"text\": " + TEXT + ", \"becameAwareAt\": \"2027-06-10T08:00:00\", \"scope\": \"LISTED\","
                        + " \"emails\": [\"" + ANU + "\"]}")
                .when().post("/privacy/breach-notices").then().statusCode(403);
    }

    @Test
    void noticesFor_whenAnAffectedUserReads_showsTheNoticeWithTheContactAndRecordsTheRead()
            throws IOException, InterruptedException {
        // Given
        String id = postedToEveryone();
        clock.set("2027-06-10T12:00:00Z");

        // When
        HttpResponse<String> mine = read("/user/" + ANU, ANU, "USER");

        // Then — r.7(1)(a) – (e)
        assertThat(mine.statusCode()).isEqualTo(200);
        JsonNode notice = json(mine).get(0);
        assertThat(notice.get("id").asString()).isEqualTo(id);
        assertThat(notice.get("text").get("safetyMeasures").asString()).isEqualTo("Watch for messages asking for your password");
        assertThat(notice.get("contact").asString()).isEqualTo("privacy@example.com");
        JsonNode lines = json(read("/" + id + "/report", OWNER, "SUPER_USER")).get("intimations");
        assertThat(lines.get(0).get("email").asString()).isEqualTo(ANU);
        assertThat(lines.get(0).get("readAt").asString()).isEqualTo("2027-06-10T12:00:00");
        assertThat(lines.get(1).get("readAt").isNull()).isTrue();
        assertThat(lines.get(2).get("readAt").isNull()).isTrue();
    }

    @Test
    void noticesFor_whenTheTokenIsAnotherUsers_isRefused() throws IOException, InterruptedException {
        // Given
        postedToEveryone();

        // When / Then
        assertThat(read("/user/" + ANU, BALA, "USER").statusCode()).isEqualTo(401);
    }

    @Test
    void emailed_whenRecordedTwice_keepsTheFirstTimeAndRefusesAnUnaffectedUser() {
        // Given
        String id = postedToEveryone();
        clock.set("2027-06-10T13:00:00Z");
        superUser("/" + id + "/emailed", "{\"emails\": [\"" + BALA + "\"]}").statusCode(200);
        clock.set("2027-06-10T14:00:00Z");

        // When / Then
        superUser("/" + id + "/emailed", "{\"emails\": [\"" + BALA + "\"]}").statusCode(200)
                .body("intimations.emailedAt", contains(nullValue(), equalTo("2027-06-10T13:00:00"), nullValue()));
        superUser("/" + id + "/emailed", "{\"emails\": [\"nobody@example.com\"]}").statusCode(400);
    }

    @Test
    void boardSteps_whenRecorded_carryTheirTimesAnExtensionMovesTheDueTimeAndNoneRepeats() {
        // Given
        String id = postedToEveryone();
        clock.set("2027-06-10T10:00:00Z");

        // When / Then
        superUser("/" + id + "/board-intimated", "").statusCode(200)
                .body("board.firstIntimatedAt", equalTo("2027-06-10T10:00:00"));
        superUser("/" + id + "/board-intimated", "").statusCode(400);
        superUser("/" + id + "/board-extension", "{\"reportDueAt\": \"2027-06-12T08:00:00\", \"reference\": \"DPB-1\"}")
                .statusCode(400);
        superUser("/" + id + "/board-extension", "{\"reportDueAt\": \"2027-06-16T08:00:00\", \"reference\": \"DPB-1\"}")
                .statusCode(200).body("board.reportDueAt", equalTo("2027-06-16T08:00:00"))
                .body("board.extensionReference", equalTo("DPB-1"));
        clock.set("2027-06-15T17:30:00Z");
        superUser("/" + id + "/board-report-sent", "").statusCode(200)
                .body("board.reportSentAt", equalTo("2027-06-15T17:30:00"));
        superUser("/" + id + "/board-report-sent", "").statusCode(400);
    }
}
