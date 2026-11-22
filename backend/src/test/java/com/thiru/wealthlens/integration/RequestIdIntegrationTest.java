package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.thiru.wealthlens.shared.exception.ControllerAdviser;
import io.restassured.RestAssured;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Every answer names its request by an id the app makes, and the lines logged while serving it carry the same id (W-358d1, D-873
 * C2a): the id is how a masked log line is traced to the processing log's record of whom it concerned.
 */
class RequestIdIntegrationTest extends AbstractIntegrationTest {

    private static final String HEADER = "X-Request-Id";
    private static final String UNREGISTERED = "{\"email\":\"x@example.com\",\"password\":\"p\"}";

    @Test
    void answer_whenAnsweredOkRefusedOrRejected_carriesAFreshRequestId() throws IOException, InterruptedException {
        // When — a public read (200), a /me read with no token (the security chain's 401), a registration with no notice version (400)
        List<HttpResponse<String>> answers = List.of(send(get("/api/v1/public/privacy/rights")), send(get("/api/v1/me/portfolio/stocks")),
                send(register()));

        // Then
        assertThat(answers).extracting(HttpResponse::statusCode).containsExactly(200, 401, 400);
        List<String> ids = answers.stream().map(answer -> answer.headers().firstValue(HEADER).orElse("")).toList();
        assertThat(ids).allSatisfy(id -> assertThat(UUID.fromString(id).toString()).isEqualTo(id)).doesNotHaveDuplicates();
    }

    @Test
    void answer_whenTheCallerSendsAnId_carriesTheAppsOwn() throws IOException, InterruptedException {
        // Given — an id a caller chose could be used to forge or merge log lines
        String chosen = "11111111-1111-1111-1111-111111111111";

        // When
        HttpResponse<String> answer = send(get("/api/v1/public/privacy/rights").header(HEADER, chosen));

        // Then
        String id = answer.headers().firstValue(HEADER).orElseThrow();
        assertThat(id).isNotEqualTo(chosen);
        assertThat(UUID.fromString(id).toString()).isEqualTo(id);
    }

    @Test
    void refusal_whenLogged_carriesTheIdItsAnswerCarries() throws IOException, InterruptedException {
        // Given
        Logger logger = (Logger) LoggerFactory.getLogger(ControllerAdviser.class);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        logger.addAppender(logged);

        // When
        HttpResponse<String> answer;
        try {
            answer = send(register());
        } finally {
            logger.detachAppender(logged);
        }

        // Then
        assertThat(answer.statusCode()).isEqualTo(400);
        assertThat(logged.list).extracting(event -> event.getMDCPropertyMap().get("requestId"))
                .contains(answer.headers().firstValue(HEADER).orElseThrow());
    }

    private static HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(uri(path)).GET();
    }

    private static HttpRequest.Builder register() {
        return HttpRequest.newBuilder(uri("/api/v1/public/auth/register")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(UNREGISTERED));
    }

    private static URI uri(String path) {
        return URI.create(RestAssured.baseURI + ":" + RestAssured.port + path);
    }

    private static HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
