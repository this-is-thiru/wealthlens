package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The API's contract, generated from the code that serves it, for an admin only (W-359b, D-853 C5a as narrowed by C1a: the spec, no UI,
 * since a browser cannot carry a bearer token to a page).
 */
class OpenApiIntegrationTest extends AbstractIntegrationTest {

    static final String SPEC = "/api/v1/admin/docs/openapi";

    @Test
    void spec_whenAskedByEachCaller_answersOnlyAnAdmin() throws IOException, InterruptedException {
        // When
        int admin = fetch(generateToken("admin@example.com", "SUPER_USER")).statusCode();
        int user = fetch(generateToken("user@example.com", "USER")).statusCode();
        int nobody = fetch(null).statusCode();

        // Then
        assertThat(List.of(admin, user, nobody)).containsExactly(200, 403, 401);
    }

    @Test
    void spec_whenRead_namesNoEmailParameter() throws IOException, InterruptedException {
        // Given — a /me route's user comes from the token (@CallerEmail), so no caller ever sends an email
        JsonNode spec = spec();

        // When
        List<String> naming = new ArrayList<>();
        for (Map.Entry<String, JsonNode> path : spec.get("paths").properties()) {
            for (Map.Entry<String, JsonNode> operation : path.getValue().properties()) {
                operation.getValue().path("parameters").forEach(parameter -> {
                    if ("email".equals(parameter.path("name").asString()) && !"path".equals(parameter.path("in").asString())) {
                        naming.add(operation.getKey() + " " + path.getKey());
                    }
                });
            }
        }

        // Then
        assertThat(naming).as("operations documenting an email the token already carries").isEmpty();
    }

    @Test
    void spec_whenAListIsPaged_documentsPageAndSizeOnly() throws IOException, InterruptedException {
        // Given — D-861 C3a: a page and a size are read, a sort is not
        JsonNode operation = spec().get("paths").get("/api/v1/me/portfolio/transactions").get("get");

        // When
        List<String> names = new ArrayList<>();
        operation.path("parameters").forEach(parameter -> names.add(parameter.path("name").asString()));

        // Then
        assertThat(names).contains("page", "size").doesNotContain("pageable", "sort");
    }

    static JsonNode spec(String adminToken) throws IOException, InterruptedException {
        HttpResponse<String> response = fetch(adminToken);
        assertThat(response.statusCode()).as("the spec for an admin").isEqualTo(200);
        return JsonMapper.builder().build().readTree(response.body());
    }

    private JsonNode spec() throws IOException, InterruptedException {
        return spec(generateToken("admin@example.com", "SUPER_USER"));
    }

    private static HttpResponse<String> fetch(String token) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + io.restassured.RestAssured.port + SPEC));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
