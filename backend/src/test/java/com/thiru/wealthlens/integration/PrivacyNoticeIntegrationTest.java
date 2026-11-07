package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.shared.privacy.GrievanceReceipt;
import com.thiru.wealthlens.shared.privacy.PrivacyNotice;
import com.thiru.wealthlens.shared.privacy.PrivacyRight;
import com.thiru.wealthlens.taxplanning.erasure.ErasureReceipt;
import io.restassured.RestAssured;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * The privacy notice is public and says what the yaml says (DPDP Rules r.14(1), r.9; W-237a); and every self-service
 * request it lists answers with a type naming the contact (r.9's "every response"; W-237a C3a) — a request added to the
 * notice without one fails here. The value in the erasure receipt is {@code TaxErasureIntegrationTest}'s.
 */
class PrivacyNoticeIntegrationTest extends AbstractIntegrationTest {

    /** Each rights request the app serves, and the response it answers with. */
    private static final Map<String, Class<?>> RESPONSES = Map.of(
            "POST /tax/user/{email}/profiles/{profileId}/erasure", ErasureReceipt.class,
            "POST /privacy/grievances/user/{email}", GrievanceReceipt.class);

    @Test
    void rights_whenReadWithoutSigningIn_publishesTheRightsTheIdentifierAndTheContact()
            throws IOException, InterruptedException {
        // When
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create(RestAssured.baseURI + ":" + RestAssured.port + "/privacy/rights")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        // Then — as application-integration-test.yaml states it
        assertThat(response.statusCode()).isEqualTo(200);
        PrivacyNotice notice = JsonMapper.builder().build().readValue(response.body(), PrivacyNotice.class);
        assertThat(notice.contact()).isEqualTo("privacy@example.com");
        assertThat(notice.identifier()).isEqualTo("the email address your account is registered with");
        assertThat(notice.rights()).extracting(PrivacyRight::section).containsExactly(
                "Digital Personal Data Protection Act, 2023 s.11(1)",
                "Digital Personal Data Protection Act, 2023 s.12(1), (2)",
                "Digital Personal Data Protection Act, 2023 s.12(1), (3)",
                "Digital Personal Data Protection Act, 2023 s.13",
                "Digital Personal Data Protection Act, 2023 s.14");
        assertThat(notice.rights()).flatExtracting(PrivacyRight::endpoints)
                .containsExactly("POST /tax/user/{email}/profiles/{profileId}/erasure", "POST /privacy/grievances/user/{email}");
        assertThat(notice.grievanceResponseDays()).isEqualTo(30);
    }

    @Test
    void rights_whenTheNoticeListsASelfServiceRequest_itsResponseNamesTheContact()
            throws IOException, InterruptedException {
        // Given
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create(RestAssured.baseURI + ":" + RestAssured.port + "/privacy/rights")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        PrivacyNotice notice = JsonMapper.builder().build().readValue(response.body(), PrivacyNotice.class);

        // When / Then — every listed request is known here, and its response type has a contact
        notice.rights().stream().flatMap(right -> right.endpoints().stream()).forEach(endpoint -> {
            assertThat(RESPONSES).as("the response type of %s", endpoint).containsKey(endpoint);
            assertThat(Arrays.stream(RESPONSES.get(endpoint).getRecordComponents()).map(c -> c.getName()))
                    .as("%s names the contact (r.9)", endpoint).contains("contact");
        });
    }
}
