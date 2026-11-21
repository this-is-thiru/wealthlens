package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxplanning.jobs.JobLease;
import io.restassured.RestAssured;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Running a tax job on request (W-336): {@code SUPER_USER} only, through the job's lease, and a name no job has refused naming
 * the ones there are. Called with the JDK's client, as {@code GazetteWatchEndpointIntegrationTest} explains.
 */
class TaxJobEndpointIntegrationTest extends AbstractIntegrationTest {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @Test
    void run_whenTheOwnerAsksForAKnownJob_runsItAndReleasesTheLease() throws IOException, InterruptedException {
        // When
        HttpResponse<String> response = send(post("/api/v1/admin/tax/jobs/log-retention/run", "SUPER_USER"));

        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"job\":\"log-retention\"").contains("\"ran\":true");
        assertThat(mongoTemplate.count(new Query(), JobLease.class)).isZero();
    }

    @Test
    void run_whenAskedByAUser_isForbidden() throws IOException, InterruptedException {
        // When
        HttpResponse<String> response = send(post("/api/v1/admin/tax/jobs/log-retention/run", "USER"));

        // Then
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void run_whenNoJobHasTheName_isRefusedNamingTheJobs() throws IOException, InterruptedException {
        // When
        HttpResponse<String> response = send(post("/api/v1/admin/tax/jobs/nightly-cleanup/run", "SUPER_USER"));

        // Then
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("nightly-cleanup").contains("account-erasure").contains("staleness-sweep");
    }

    private HttpRequest post(String path, String role) {
        return HttpRequest.newBuilder(URI.create(RestAssured.baseURI + ":" + RestAssured.port + path))
                .header("Authorization", "Bearer " + generateToken("admin@example.com", role))
                .POST(HttpRequest.BodyPublishers.noBody()).build();
    }

    private static HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
