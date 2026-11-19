package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/**
 * A request body carrying a property its DTO does not have is refused, not quietly trimmed (W-202, D-406).
 *
 * <p>W-201's probe sent an export's filter under {@code filters} where {@code EntityExportRequest} reads
 * {@code queryFilters}: the API answered 200 and exported every row, the filter simply gone. A misspelt or retired
 * field should cost the client a 400 that names it, not a plausible wrong answer.
 */
class RequestBodyStrictnessIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "strictness@test.com";
    private static final String EXPORT = "/portfolio/user/" + EMAIL + "/stocks/download";

    @Test
    void post_whenTheBodyHasAPropertyTheRequestDoesNotHave_answers400NamingIt() {
        // Given
        String body = "{\"entityName\": \"assets\", \"emailAddresses\": [\"" + EMAIL + "\"], \"filters\": []}";

        // When / Then
        given().header("Authorization", "Bearer " + generateToken(EMAIL)).contentType(ContentType.JSON).body(body)
                .when().post(EXPORT)
                .then().statusCode(400).body("message", containsString("filters"));
    }

    @Test
    void post_whenTheSameBodyUsesTheRequestsOwnProperty_answers200() {
        // Given
        String body = "{\"entityName\": \"assets\", \"emailAddresses\": [\"" + EMAIL + "\"], \"queryFilters\": []}";

        // When / Then
        given().header("Authorization", "Bearer " + generateToken(EMAIL)).contentType(ContentType.JSON).body(body)
                .when().post(EXPORT)
                .then().statusCode(equalTo(200));
    }
}
