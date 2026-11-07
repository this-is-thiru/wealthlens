package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxplanning.gazette.GazetteCursor;
import com.thiru.wealthlens.taxplanning.gazette.GazetteCursorRepository;
import io.restassured.RestAssured;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The Gazette watch's two endpoints: {@code SUPER_USER} only, and the cursor validated and recorded with its caller (W-236).
 * Called with the JDK's client, as {@code TaxPlanningControllerIntegrationTest} calls {@code /tax-planning} with a plain
 * client: RestAssured 5.5.7 throws a NullPointerException inside its own response handling on these paths, while the
 * server's answers are correct (403, 200, 400 — seen with this client).
 */
class GazetteWatchEndpointIntegrationTest extends AbstractIntegrationTest {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @Autowired
    private GazetteCursorRepository cursors;

    @Test
    void leads_whenAskedByAUser_isForbidden() throws IOException, InterruptedException {
        // When
        HttpResponse<String> response = send(get("/tax-planning/admin/gazette-leads", "USER"));

        // Then
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void seedCursor_whenTheOwnerSetsIt_isStoredWithTheCaller() throws IOException, InterruptedException {
        // When
        HttpResponse<String> response = send(put("/tax-planning/admin/gazette-cursor",
                "{\"year\":2026,\"lastNumber\":271439}"));

        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"lastNumber\":271439");
        assertThat(cursors.findById(GazetteCursor.ID)).hasValueSatisfying(cursor -> {
            assertThat(cursor.getYear()).isEqualTo(2026);
            assertThat(cursor.getUpdatedBy()).isEqualTo("admin@example.com");
        });
        assertThat(send(get("/tax-planning/admin/gazette-leads", "SUPER_USER")).statusCode()).isEqualTo(200);
    }

    @Test
    void seedCursor_whenTheYearPrecedesTheEGazette_isRefused() throws IOException, InterruptedException {
        // When
        HttpResponse<String> response = send(put("/tax-planning/admin/gazette-cursor", "{\"year\":2008,\"lastNumber\":1}"));

        // Then
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(cursors.findAll()).isEmpty();
    }

    private HttpRequest get(String path, String role) {
        return request(path, role).GET().build();
    }

    private HttpRequest put(String path, String json) {
        return request(path, "SUPER_USER").header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json)).build();
    }

    private HttpRequest.Builder request(String path, String role) {
        return HttpRequest.newBuilder(URI.create(RestAssured.baseURI + ":" + RestAssured.port + path))
                .header("Authorization", "Bearer " + generateToken("admin@example.com", role));
    }

    private static HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
