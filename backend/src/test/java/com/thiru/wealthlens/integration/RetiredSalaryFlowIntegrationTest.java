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
 * The legacy salary flow under {@code /tax-planning} is gone (W-159, D-726): a person uses {@code /tax/**}. Its user paths
 * answer 404 and its public allowance catalogue is no longer open, so neither can come back unnoticed. Called with the
 * JDK's client for the reason {@link GazetteWatchEndpointIntegrationTest} gives.
 */
class RetiredSalaryFlowIntegrationTest extends AbstractIntegrationTest {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final String EMAIL = "salaried@example.com";

    @Test
    void profiles_whenTheLegacyPathIsAskedWithAToken_isRefused() throws IOException, InterruptedException {
        // Given
        HttpRequest request = request("/tax-planning/user/" + EMAIL + "/profiles")
                .header("Authorization", "Bearer " + generateToken(EMAIL)).GET().build();

        // When
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        // Then — outside every zone, so refused before any route is looked up (D-855); before W-358c1 it was a 404
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void allowances_whenAskedWithoutAToken_isUnauthorised() throws IOException, InterruptedException {
        // Given
        HttpRequest request = request("/tax-planning/public/allowances").GET().build();

        // When
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        // Then
        assertThat(response.statusCode()).isEqualTo(401);
    }

    private static HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(RestAssured.baseURI + ":" + RestAssured.port + path));
    }
}
