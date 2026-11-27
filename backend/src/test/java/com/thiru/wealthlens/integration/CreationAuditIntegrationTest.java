package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxplanning.compute.DraftComputation;
import com.thiru.wealthlens.taxplanning.profile.BankAccount;
import com.thiru.wealthlens.taxplanning.profile.Employer;
import com.thiru.wealthlens.taxplanning.profile.FamilyMember;
import com.thiru.wealthlens.taxplanning.profile.Landlord;
import com.thiru.wealthlens.taxplanning.profile.LandlordRepository;
import com.thiru.wealthlens.taxplanning.profile.Lender;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfileRepository;
import com.thiru.wealthlens.taxplanning.workspace.DraftRevision;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraft;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraftRepository;
import com.thiru.wealthlens.taxplanning.workspace.RegimeElectionHistory;
import com.thiru.wealthlens.taxplanning.workspace.TaxYearWorkspace;
import io.restassured.RestAssured;
import jakarta.persistence.Table;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * Every tax-planning entity given its id before its first save records when and by whom it was created (W-329b, D-725):
 * Spring Data audits it as new on its creation path, and as an update ever after. A record stored before W-329b keeps
 * no creation fields — its creation was never recorded, and none is invented.
 */
class CreationAuditIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER = "auditor@example.com";

    private static final List<Class<?>> CREATED_WITH_ID = List.of(TaxpayerProfile.class, FamilyMember.class,
            Employer.class, Landlord.class, Lender.class, BankAccount.class, TaxYearWorkspace.class, FilingDraft.class,
            DraftRevision.class, DraftComputation.class, RegimeElectionHistory.class);

    private String profileId;

    @Autowired
    private TaxpayerProfileRepository taxpayerProfileRepository;

    @Autowired
    private FilingDraftRepository filingDraftRepository;

    @Autowired
    private LandlordRepository landlordRepository;

    private ResponseEntity<String> call(HttpMethod method, String path, String body) {
        RestTemplate client = new RestTemplate(new JdkClientHttpRequestFactory());
        client.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(generateToken(OWNER));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return client.exchange(URI.create("http://localhost:" + RestAssured.port + "/api/v1/me/tax" + path), method,
                new HttpEntity<>(body, headers), String.class);
    }

    private void created(HttpMethod method, String path, String body) {
        ResponseEntity<String> response = call(method, path, body);
        assertThat(response.getStatusCode()).as(path + " " + response.getBody()).isEqualTo(HttpStatus.OK);
    }

    private Map<String, Object> audit(Class<?> entity) {
        String table = entity.getAnnotation(Table.class).name();
        return new JdbcTemplate(postgresDataSource)
                .queryForMap("SELECT created_at, created_by, updated_at, updated_by FROM " + table + " LIMIT 1");
    }

    @BeforeEach
    void everyKind() {
        created(HttpMethod.POST, "/profiles", """
                {"name": "Self", "pan": "ABCDE1234F", "kind": "INDIVIDUAL", "dateOfBirth": "1985-06-01",
                 "residentialStatus": "RESIDENT"}""");
        profileId = taxpayerProfileRepository.findAll().getFirst().getId();
        String references = "/profiles/" + profileId;
        created(HttpMethod.POST, references + "/family-members", """
                {"name": "Asha", "relationship": "SPOUSE", "dateOfBirth": "1986-02-01", "dependant": true}""");
        created(HttpMethod.POST, references + "/employers", """
                {"name": "Acme", "tan": "BLRA12345B", "type": "PRIVATE"}""");
        created(HttpMethod.POST, references + "/landlords", """
                {"name": "Ravi", "address": "Bengaluru", "pan": "PQRST5678K"}""");
        created(HttpMethod.POST, references + "/lenders", """
                {"name": "HDFC", "kind": "BANK"}""");
        created(HttpMethod.POST, references + "/bank-accounts", """
                {"bankName": "SBI", "ifsc": "SBIN0000001", "maskedNumber": "XXXX1234", "kind": "SAVINGS"}""");
        created(HttpMethod.POST, references + "/workspaces/2026/first-figure", """
                {"label": "First figure", "grossPay": {"amountInRupees": 1200000}, "dueDate": "2027-07-31"}""");
        String draftId = filingDraftRepository.findAll().getFirst().getId();
        created(HttpMethod.POST, "/drafts/" + draftId + "/status", "{\"status\": \"READY\"}");
        created(HttpMethod.POST, "/drafts/" + draftId + "/status",
                "{\"status\": \"ACTED_ON\", \"regimeCode\": \"FIRST_SCHEDULE_RATES\"}");
    }

    @Test
    void create_whenEachKindIsMade_recordsWhenAndByWhomItWasCreated() {
        // When / Then — each of the 11 kinds, as its first save stored it
        for (Class<?> entity : CREATED_WITH_ID) {
            Map<String, Object> audit = audit(entity);
            assertThat(audit.get("created_at")).as(entity.getSimpleName() + " createdAt").isNotNull();
            assertThat(audit.get("created_by")).as(entity.getSimpleName() + " createdBy").isNotNull();
        }
    }

    @Test
    void update_whenARecordIsSavedAgain_keepsItsCreationAndMovesItsUpdate() {
        // Given
        Map<String, Object> before = audit(Landlord.class);
        String landlordId = landlordRepository.findAll().getFirst().getId();

        // When
        created(HttpMethod.PUT, "/profiles/" + profileId + "/landlords/" + landlordId, """
                {"name": "Ravi Kumar", "address": "Bengaluru", "pan": "PQRST5678K"}""");

        // Then
        Map<String, Object> after = audit(Landlord.class);
        assertThat(landlordRepository.findAll()).hasSize(1);
        assertThat(after.get("created_at")).isEqualTo(before.get("created_at"));
        assertThat(after.get("created_by")).isEqualTo(before.get("created_by"));
        assertThat((Date) after.get("updated_at")).isAfterOrEqualTo((Date) before.get("updated_at"));
    }

    @Test
    void update_whenARecordWasStoredBeforeItsCreationWasRecorded_staysWithoutIt() {
        // Given — as every landlord stored before W-329b is: no creation fields
        String landlordId = landlordRepository.findAll().getFirst().getId();
        new JdbcTemplate(postgresDataSource)
                .update("UPDATE tax_landlords SET created_at = NULL, created_by = NULL WHERE id = ?", landlordId);

        // When
        created(HttpMethod.PUT, "/profiles/" + profileId + "/landlords/" + landlordId, """
                {"name": "Ravi Kumar", "address": "Bengaluru", "pan": "PQRST5678K"}""");

        // Then — updated, not inserted again, and no creation is invented
        Map<String, Object> audit = audit(Landlord.class);
        assertThat(landlordRepository.findAll()).hasSize(1);
        assertThat(audit.get("created_at")).isNull();
        assertThat(audit.get("created_by")).isNull();
        assertThat(audit.get("updated_at")).isNotNull();
    }
}
