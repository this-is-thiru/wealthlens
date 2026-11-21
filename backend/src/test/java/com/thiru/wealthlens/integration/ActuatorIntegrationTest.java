package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.RestAssured;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/**
 * Health and readiness for an admin (W-359a, D-853): a {@code SUPER_USER} reads them, anyone else is refused, and no other
 * Actuator endpoint is served. Over {@code java.net.http}: RestAssured's GET throws an NPE in this suite (session 59).
 */
class ActuatorIntegrationTest extends AbstractIntegrationTest {

    private static HttpResponse<String> get(String path, String token) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(RestAssured.baseURI + ":" + RestAssured.port + path)).GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void health_whenAnAdminAsks_isUp() throws Exception {
        // When
        HttpResponse<String> health = get("/actuator/health", generateToken("admin-health@example.com", "SUPER_USER"));
        HttpResponse<String> readiness = get("/actuator/health/readiness", generateToken("admin-health@example.com", "SUPER_USER"));

        // Then
        assertThat(health.statusCode()).as(health.body()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"");
        assertThat(readiness.statusCode()).as(readiness.body()).isEqualTo(200);
    }

    @Test
    void health_whenAPlainUserOrNoOneAsks_isRefused() throws Exception {
        // When
        int user = get("/actuator/health", generateToken("user-health@example.com")).statusCode();
        int anonymous = get("/actuator/health", null).statusCode();

        // Then
        assertThat(user).isEqualTo(403);
        assertThat(anonymous).isEqualTo(401);
    }

    @Test
    void actuator_whenAnyOtherEndpointIsAsked_isNotServed() throws Exception {
        // When
        int env = get("/actuator/env", generateToken("admin-health@example.com", "SUPER_USER")).statusCode();
        int beans = get("/actuator/beans", generateToken("admin-health@example.com", "SUPER_USER")).statusCode();

        // Then
        assertThat(env).isEqualTo(404);
        assertThat(beans).isEqualTo(404);
    }
}
