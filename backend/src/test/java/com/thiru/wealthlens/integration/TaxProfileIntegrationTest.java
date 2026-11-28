package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.thiru.wealthlens.taxplanning.profile.EmployerRepository;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfileRepository;
import com.thiru.wealthlens.taxplanning.protection.AccessRecord;
import com.thiru.wealthlens.taxplanning.protection.AccessRecordRepository;
import com.thiru.wealthlens.taxplanning.protection.ReadSubject;
import io.restassured.RestAssured;
import java.net.URI;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A taxpayer's profile and the people and accounts it refers to, over HTTP and into PostgreSQL (W-096 – W-098, D-440): only the
 * owner reaches them, and a PAN appears in no response, message or log in full ({@code DR 14} O1.1, O2).
 */
@ExtendWith(OutputCaptureExtension.class)
class TaxProfileIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER = "owner@example.com";
    private static final String OTHER = "other@example.com";
    private static final String PAN = "ABCDE1234F";

    @Autowired
    private TaxpayerProfileRepository profiles;

    @Autowired
    private EmployerRepository employers;

    @Autowired
    private AccessRecordRepository accessRecords;

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
        return client.exchange(URI.create("http://localhost:" + RestAssured.port + "/api/v1/me/tax" + path), method,
                new HttpEntity<>(body, headers), String.class);
    }

    private String createdProfileId() {
        assertThat(call(HttpMethod.POST, OWNER, "/profiles", profile(PAN)).getStatusCode()).isEqualTo(HttpStatus.OK);
        return profiles.findAll().getFirst().getId();
    }

    @Test
    void create_whenWellFormed_storesThePanAndShowsItMasked() {
        // When
        ResponseEntity<String> created = call(HttpMethod.POST, OWNER, "/profiles", profile(PAN));

        // Then
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(created.getBody()).contains("ABCXXXXX4F").doesNotContain(PAN);
        TaxpayerProfile stored = profiles.findAll().getFirst();
        assertThat(stored.getPan()).isEqualTo(PAN);
        assertThat(stored.getEmail()).isEqualTo(OWNER);
    }

    @Test
    void create_whenAnAccountHoldsSeveralProfiles_keepsThemAll() {
        // Given — D-046 B2: self, a HUF, family
        call(HttpMethod.POST, OWNER, "/profiles", profile(PAN));

        // When
        call(HttpMethod.POST, OWNER, "/profiles", profile("PQRST5678K"));

        // Then
        assertThat(call(HttpMethod.GET, OWNER, "/profiles", null).getBody()).contains("ABCXXXXX4F", "PQRXXXXX8K");
    }

    @Test
    void create_whenThePanIsTaken_expected400WithItMaskedInTheResponseAndTheLog(CapturedOutput output) {
        // Given
        call(HttpMethod.POST, OWNER, "/profiles", profile(PAN));

        // When
        ResponseEntity<String> again = call(HttpMethod.POST, OWNER, "/profiles", profile(PAN));

        // Then
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(again.getBody()).contains("ABCXXXXX4F").doesNotContain(PAN);
        assertThat(output.getAll()).doesNotContain(PAN);
    }

    @Test
    void create_whenThePanIsMalformed_expected400AndNothingStored() {
        // When
        ResponseEntity<String> refused = call(HttpMethod.POST, OWNER, "/profiles", profile("abcde1234f"));

        // Then
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(profiles.findAll()).isEmpty();
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
        ResponseEntity<String> response = client.getForEntity(URI.create("http://localhost:" + RestAssured.port + "/api/v1/me/tax/profiles/" + id), String.class);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(String.valueOf(response.getBody())).doesNotContain("Self", "ABCXXXXX4F");
    }

    @Test
    void anyRoute_whenAnotherAccountCalls_seesNoneOfTheOwnersProfiles() {
        // Given
        String id = createdProfileId();

        // When — their own token; since W-358c1 a /me path names nobody, so the account reads only its own (D-855 C4a)
        ResponseEntity<String> listed = call(HttpMethod.GET, OTHER, "/profiles", null);

        // Then
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listed.getBody()).isEqualTo("[]");
        assertThat(call(HttpMethod.GET, OTHER, "/profiles/" + id + "/employers", null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anyRoute_whenAnotherAccountNamesTheProfileUnderItsOwnPath_expected400() {
        // Given
        String id = createdProfileId();

        // When / Then — the path is theirs, the profile is not
        assertThat(call(HttpMethod.GET, OTHER, "/profiles/" + id, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.POST, OTHER, "/profiles/" + id + "/lenders",
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
        String path = "/profiles/" + createdProfileId() + "/" + kind;

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
        String path = "/profiles/" + createdProfileId() + "/landlords";
        call(HttpMethod.POST, OWNER, path, "{\"name\": \"Ravi\", \"pan\": \"PQRST5678K\"}");

        // When / Then
        assertThat(call(HttpMethod.GET, OWNER, path, null).getBody()).contains("PQRXXXXX8K").doesNotContain("PQRST5678K");
    }

    /** W-119b C2a: r.205(2) asks for the lender's address; one stored before W-119b holds none and reads with none. */
    @Test
    void lender_whenStoredWithAndWithoutAnAddress_readsItBackOrNone() {
        // Given
        String path = "/profiles/" + createdProfileId() + "/lenders";
        call(HttpMethod.POST, OWNER, path, "{\"name\": \"HDFC\", \"kind\": \"BANK\"}");

        // When
        Object withoutAddress = rawFirst("tax_lenders").get("address");
        call(HttpMethod.POST, OWNER, path, "{\"name\": \"SBI\", \"address\": \"Mumbai\", \"kind\": \"BANK\"}");
        JsonNode lenders = JsonMapper.builder().build().readTree(call(HttpMethod.GET, OWNER, path, null).getBody());

        // Then — the first stores no address value at all, as a lender stored before W-119b
        assertThat(withoutAddress).isNull();
        assertThat(lenders.size()).isEqualTo(2);
        for (JsonNode lender : lenders) {
            JsonNode address = lender.path("address");
            if (lender.path("name").asString().equals("SBI")) {
                assertThat(address.asString()).isEqualTo("Mumbai");
            } else {
                assertThat(address.isNull() || address.isMissingNode()).as(lender.toString()).isTrue();
            }
        }
    }

    @Test
    void employer_whenTheTanIsTakenInTheProfile_expected400ButAnotherProfileMayHoldIt() {
        // Given
        String path = "/profiles/" + createdProfileId() + "/employers";
        String body = "{\"name\": \"Acme\", \"tan\": \"BLRA12345B\", \"type\": \"PRIVATE\"}";
        call(HttpMethod.POST, OWNER, path, body);

        // When
        ResponseEntity<String> again = call(HttpMethod.POST, OWNER, path, body);
        call(HttpMethod.POST, OWNER, "/profiles", profile("PQRST5678K"));
        String secondId = profiles.findAll().get(1).getId();
        ResponseEntity<String> elsewhere = call(HttpMethod.POST, OWNER, "/profiles/" + secondId + "/employers", body);

        // Then — D-046 B5: per account's profile, not shared
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(again.getBody()).doesNotContain("BLRA12345B");
        assertThat(elsewhere.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(employers.findAll()).hasSize(2);
    }

    private Map<String, Object> rawFirst(String table) {
        return new JdbcTemplate(postgresDataSource).queryForMap("SELECT * FROM " + table + " LIMIT 1");
    }

    @Test
    void save_whenAProfileAndItsReferencesAreStored_storesThemPlainAndReadsThemBack() {
        // Given
        String profileId = createdProfileId();
        String path = "/profiles/" + profileId + "/";

        // When
        call(HttpMethod.POST, OWNER, path + "family-members", """
                {"name": "Asha", "relationship": "SPOUSE", "dateOfBirth": "1986-02-01", "disability": "DISABILITY",
                 "pan": "PQRST5678K"}""");
        call(HttpMethod.POST, OWNER, path + "employers", "{\"name\": \"Acme\", \"tan\": \"BLRA12345B\", \"type\": \"PRIVATE\"}");
        call(HttpMethod.POST, OWNER, path + "landlords", "{\"name\": \"Ravi\", \"address\": \"Bengaluru\", \"pan\": \"LMNOP4321Q\"}");
        call(HttpMethod.POST, OWNER, path + "lenders",
                "{\"name\": \"Meera\", \"address\": \"Pune\", \"pan\": \"FGHIJ8765K\", \"kind\": \"OTHER\"}");

        // Then — stored plain (W-428a, W-428c); what the app reads back is unchanged
        assertThat(String.valueOf(rawFirst("tax_profiles"))).contains(PAN, "Self", "1985-06-01").doesNotContain("k1:");
        assertThat(String.valueOf(rawFirst("tax_family_members"))).contains("PQRST5678K", "Asha", "1986-02-01").doesNotContain("k1:");
        assertThat(String.valueOf(rawFirst("tax_employers"))).contains("BLRA12345B").doesNotContain("k1:");
        assertThat(String.valueOf(rawFirst("tax_landlords"))).contains("LMNOP4321Q", "Ravi", "Bengaluru").doesNotContain("k1:");
        assertThat(String.valueOf(rawFirst("tax_lenders"))).contains("FGHIJ8765K", "Meera", "Pune").doesNotContain("k1:");
        Map<String, Object> profile = rawFirst("tax_profiles");
        assertThat((String) profile.get("pan")).isEqualTo(PAN);
        // no digest beside it since W-428c (D-1039): the PAN's own index keeps it unique
        assertThat(profile).doesNotContainKey("pan_digest");
        TaxpayerProfile read = profiles.findAll().getFirst();
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
        call(HttpMethod.GET, OWNER, "/profiles/" + profileId, null);
        call(HttpMethod.GET, OWNER, "/profiles/" + profileId + "/family-members", null);
        call(HttpMethod.GET, OWNER, "/profiles/" + profileId + "/employers", null);

        // Then — DR 14 O1.1, O4: the two reads that carry special-category data, and only those
        assertThat(accessRecords.findAll())
                .extracting(AccessRecord::getSubject, AccessRecord::getSubjectId, AccessRecord::getPrincipal)
                .containsExactlyInAnyOrder(
                        tuple(ReadSubject.PROFILE, profileId, OWNER),
                        tuple(ReadSubject.FAMILY_MEMBERS, profileId, OWNER));
        assertThat(String.valueOf(rawFirst("tax_access_log"))).doesNotContain(PAN, "Self");
    }
}
