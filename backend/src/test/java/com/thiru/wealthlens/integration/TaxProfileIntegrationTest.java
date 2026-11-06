package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.thiru.wealthlens.taxplanning.profile.Employer;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.taxplanning.protection.AccessRecord;
import com.thiru.wealthlens.taxplanning.protection.ReadSubject;
import io.restassured.RestAssured;
import java.net.URI;
import java.util.stream.Stream;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * A taxpayer's profile and the people and accounts it refers to, over HTTP and into Mongo (W-096 – W-098, D-440): only the owner
 * reaches them, and a PAN appears in no response, message or log in full ({@code DR 14} O1.1, O2).
 */
@ExtendWith(OutputCaptureExtension.class)
class TaxProfileIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER = "owner@example.com";
    private static final String OTHER = "other@example.com";
    private static final String PAN = "ABCDE1234F";

    private static String profile(String pan) {
        return """
                {"name": "Self", "pan": "%s", "kind": "INDIVIDUAL", "dateOfBirth": "1985-06-01",
                 "residentialStatus": "RESIDENT"}""".formatted(pan);
    }

    private ResponseEntity<String> call(HttpMethod method, String caller, String path, String body) {
        RestTemplate client = new RestTemplate();
        client.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(generateToken(caller));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return client.exchange(URI.create("http://localhost:" + RestAssured.port + "/tax/user/" + path), method,
                new HttpEntity<>(body, headers), String.class);
    }

    private String createdProfileId() {
        assertThat(call(HttpMethod.POST, OWNER, OWNER + "/profiles", profile(PAN)).getStatusCode()).isEqualTo(HttpStatus.OK);
        return mongoTemplate.findAll(TaxpayerProfile.class).getFirst().getId();
    }

    @Test
    void create_whenWellFormed_storesThePanAndShowsItMasked() {
        // When
        ResponseEntity<String> created = call(HttpMethod.POST, OWNER, OWNER + "/profiles", profile(PAN));

        // Then
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(created.getBody()).contains("ABCXXXXX4F").doesNotContain(PAN);
        TaxpayerProfile stored = mongoTemplate.findAll(TaxpayerProfile.class).getFirst();
        assertThat(stored.getPan()).isEqualTo(PAN);
        assertThat(stored.getEmail()).isEqualTo(OWNER);
    }

    @Test
    void create_whenAnAccountHoldsSeveralProfiles_keepsThemAll() {
        // Given — D-046 B2: self, a HUF, family
        call(HttpMethod.POST, OWNER, OWNER + "/profiles", profile(PAN));

        // When
        call(HttpMethod.POST, OWNER, OWNER + "/profiles", profile("PQRST5678K"));

        // Then
        assertThat(call(HttpMethod.GET, OWNER, OWNER + "/profiles", null).getBody()).contains("ABCXXXXX4F", "PQRXXXXX8K");
    }

    @Test
    void create_whenThePanIsTaken_expected400WithItMaskedInTheResponseAndTheLog(CapturedOutput output) {
        // Given
        call(HttpMethod.POST, OWNER, OWNER + "/profiles", profile(PAN));

        // When
        ResponseEntity<String> again = call(HttpMethod.POST, OWNER, OWNER + "/profiles", profile(PAN));

        // Then
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(again.getBody()).contains("ABCXXXXX4F").doesNotContain(PAN);
        assertThat(output.getAll()).doesNotContain(PAN);
    }

    @Test
    void create_whenThePanIsMalformed_expected400AndNothingStored() {
        // When
        ResponseEntity<String> refused = call(HttpMethod.POST, OWNER, OWNER + "/profiles", profile("abcde1234f"));

        // Then
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mongoTemplate.findAll(TaxpayerProfile.class)).isEmpty();
    }

    @Test
    void anyRoute_whenNoTokenIsSent_isRefused() {
        // Given — /tax/** is named in AuthConfig; the fallback there would otherwise make it public
        String id = createdProfileId();
        RestTemplate client = new RestTemplate();
        client.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });

        // When
        ResponseEntity<String> response = client.getForEntity(URI.create("http://localhost:" + RestAssured.port + "/tax/user/"
                + OWNER + "/profiles/" + id), String.class);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(String.valueOf(response.getBody())).doesNotContain("Self", "ABCXXXXX4F");
    }

    @Test
    void anyRoute_whenAnotherAccountCalls_isRefused() {
        // Given
        String id = createdProfileId();

        // When / Then — their own token, the owner's path
        assertThat(call(HttpMethod.GET, OTHER, OWNER + "/profiles", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(HttpMethod.GET, OTHER, OWNER + "/profiles/" + id, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(HttpMethod.GET, OTHER, OWNER + "/profiles/" + id + "/employers", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anyRoute_whenAnotherAccountNamesTheProfileUnderItsOwnPath_expected400() {
        // Given
        String id = createdProfileId();

        // When / Then — the path is theirs, the profile is not
        assertThat(call(HttpMethod.GET, OTHER, OTHER + "/profiles/" + id, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.POST, OTHER, OTHER + "/profiles/" + id + "/lenders",
                "{\"name\": \"HDFC\", \"kind\": \"BANK\"}").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    static Stream<Arguments> references() {
        return Stream.of(
                Arguments.of("family-members", """
                        {"name": "Asha", "relationship": "SPOUSE", "dateOfBirth": "1986-02-01", "dependant": true}""", "Asha"),
                Arguments.of("employers", """
                        {"name": "Acme", "tan": "BLRA12345B", "type": "PRIVATE"}""", "Acme"),
                Arguments.of("landlords", """
                        {"name": "Ravi", "address": "Bengaluru", "pan": "PQRST5678K"}""", "Ravi"),
                Arguments.of("lenders", """
                        {"name": "HDFC", "kind": "BANK"}""", "HDFC"),
                Arguments.of("bank-accounts", """
                        {"bankName": "SBI", "ifsc": "SBIN0000001", "maskedNumber": "XXXX1234", "kind": "SAVINGS"}""", "SBI"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("references")
    void reference_whenCreatedAndReplaced_isReadBack(String kind, String body, String name) {
        // Given
        String path = OWNER + "/profiles/" + createdProfileId() + "/" + kind;

        // When
        ResponseEntity<String> created = call(HttpMethod.POST, OWNER, path, body);
        String id = created.getBody().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        ResponseEntity<String> replaced = call(HttpMethod.PUT, OWNER, path + "/" + id, body.replace(name, "Renamed"));

        // Then
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replaced.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, OWNER, path, null).getBody()).contains("Renamed").doesNotContain(name);
    }

    @Test
    void landlord_whenRead_showsItsPanMasked() {
        // Given
        String path = OWNER + "/profiles/" + createdProfileId() + "/landlords";
        call(HttpMethod.POST, OWNER, path, "{\"name\": \"Ravi\", \"pan\": \"PQRST5678K\"}");

        // When / Then
        assertThat(call(HttpMethod.GET, OWNER, path, null).getBody()).contains("PQRXXXXX8K").doesNotContain("PQRST5678K");
    }

    @Test
    void employer_whenTheTanIsTakenInTheProfile_expected400ButAnotherProfileMayHoldIt() {
        // Given
        String path = OWNER + "/profiles/" + createdProfileId() + "/employers";
        String body = "{\"name\": \"Acme\", \"tan\": \"BLRA12345B\", \"type\": \"PRIVATE\"}";
        call(HttpMethod.POST, OWNER, path, body);

        // When
        ResponseEntity<String> again = call(HttpMethod.POST, OWNER, path, body);
        call(HttpMethod.POST, OWNER, OWNER + "/profiles", profile("PQRST5678K"));
        String secondId = mongoTemplate.findAll(TaxpayerProfile.class).get(1).getId();
        ResponseEntity<String> elsewhere = call(HttpMethod.POST, OWNER, OWNER + "/profiles/" + secondId + "/employers", body);

        // Then — D-046 B5: per account's profile, not shared
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(again.getBody()).doesNotContain("BLRA12345B");
        assertThat(elsewhere.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mongoTemplate.findAll(Employer.class)).hasSize(2);
    }

    private String stored(String collection) {
        return mongoTemplate.getCollection(collection).find().first().toJson();
    }

    @Test
    void save_whenAProfileAndItsReferencesAreStored_holdsNoPersonalDataInTheClear() {
        // Given
        String profileId = createdProfileId();
        String path = OWNER + "/profiles/" + profileId + "/";

        // When
        call(HttpMethod.POST, OWNER, path + "family-members", """
                {"name": "Asha", "relationship": "SPOUSE", "dateOfBirth": "1986-02-01", "disability": "DISABILITY",
                 "pan": "PQRST5678K"}""");
        call(HttpMethod.POST, OWNER, path + "employers", "{\"name\": \"Acme\", \"tan\": \"BLRA12345B\", \"type\": \"PRIVATE\"}");
        call(HttpMethod.POST, OWNER, path + "landlords", "{\"name\": \"Ravi\", \"address\": \"Bengaluru\", \"pan\": \"LMNOP4321Q\"}");
        call(HttpMethod.POST, OWNER, path + "lenders", "{\"name\": \"Meera\", \"pan\": \"FGHIJ8765K\", \"kind\": \"OTHER\"}");

        // Then — DR 14 O1.1: what Mongo holds names none of them; what the app reads back is unchanged
        assertThat(stored("tax_profiles")).doesNotContain(PAN, "Self", "1985-06-01");
        assertThat(stored("tax_family_members")).doesNotContain("PQRST5678K", "Asha", "1986-02-01", "\"DISABILITY\"");
        assertThat(stored("tax_employers")).doesNotContain("BLRA12345B");
        assertThat(stored("tax_landlords")).doesNotContain("LMNOP4321Q", "Ravi", "Bengaluru");
        assertThat(stored("tax_lenders")).doesNotContain("FGHIJ8765K", "Meera");
        Document profile = mongoTemplate.getCollection("tax_profiles").find().first();
        assertThat(profile.getString("pan")).startsWith("k1:");
        // HMAC-SHA256 of ABCDE1234F under the test digest key (bytes 0x1F … 0x3E), computed with Python's hmac, not the code
        assertThat(profile.getString("pan_digest")).isEqualTo("fc7b5d1c7f778ae0d11ff7f39543daf491a5b2f2e1ca37b3f68a03e2066bca62");
        TaxpayerProfile read = mongoTemplate.findAll(TaxpayerProfile.class).getFirst();
        assertThat(read.getPan()).isEqualTo(PAN);
        assertThat(read.getName()).isEqualTo("Self");
        assertThat(read.getDateOfBirth()).hasToString("1985-06-01");
        assertThat(call(HttpMethod.GET, OWNER, path + "family-members", null).getBody()).contains("Asha", "DISABILITY", "1986-02-01");
    }

    @Test
    void read_whenAProfileAndItsFamilyAreShown_recordsEachReadWithoutItsValues() {
        // Given
        String profileId = createdProfileId();

        // When
        call(HttpMethod.GET, OWNER, OWNER + "/profiles/" + profileId, null);
        call(HttpMethod.GET, OWNER, OWNER + "/profiles/" + profileId + "/family-members", null);
        call(HttpMethod.GET, OWNER, OWNER + "/profiles/" + profileId + "/employers", null);

        // Then — DR 14 O1.1, O4: the two reads that carry special-category data, and only those
        assertThat(mongoTemplate.findAll(AccessRecord.class))
                .extracting(AccessRecord::getSubject, AccessRecord::getSubjectId, AccessRecord::getPrincipal)
                .containsExactlyInAnyOrder(
                        tuple(ReadSubject.PROFILE, profileId, OWNER),
                        tuple(ReadSubject.FAMILY_MEMBERS, profileId, OWNER));
        assertThat(mongoTemplate.getCollection("tax_access_log").find().first().toJson()).doesNotContain(PAN, "Self");
    }
}
