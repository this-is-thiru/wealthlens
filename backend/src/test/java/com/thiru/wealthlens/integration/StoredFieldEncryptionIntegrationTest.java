package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;

import com.thiru.wealthlens.insurance.entity.InsuranceEntity;
import com.thiru.wealthlens.insurance.entity.model.PolicyDetails;
import com.thiru.wealthlens.shared.privacy.Grievance;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;

/**
 * Personal data outside {@code taxplanning} stored encrypted (W-248, D-479 C2a), and rows stored in plain text before
 * it encrypted by the one-time endpoint (C3a). The counts are by hand from the rows each test inserts.
 */
class StoredFieldEncryptionIntegrationTest extends AbstractIntegrationTest {

    private static final String USER = "user@example.com";
    private static final String OWNER = "owner@example.com";
    private static final String GRIEVANCES = "privacy_grievances";
    private static final String INSURANCES = "insurances";

    private ValidatableResponse run(boolean dryRun, String role) {
        return given().header("Authorization", "Bearer " + generateToken(OWNER, role)).contentType(ContentType.JSON)
                .when().post("/privacy/stored-fields/encrypt?dryRun=" + dryRun).then();
    }

    private Document raw(String collection, String id) {
        return mongoTemplate.getCollection(collection).find(new Document("_id", id)).first();
    }

    private void insertPlainRows() {
        mongoTemplate.getCollection(GRIEVANCES).insertMany(List.of(
                new Document("_id", "plain-1").append("email", USER).append("text", "Wrong address shown"),
                new Document("_id", "plain-2").append("email", USER).append("text", "Delete my old PAN")
                        .append("answer", "Done on 3 June")));
        Grievance sealed = new Grievance();
        sealed.setId("sealed");
        sealed.setEmail(USER);
        sealed.setText("Already encrypted");
        mongoTemplate.save(sealed);
        mongoTemplate.getCollection(INSURANCES).insertOne(new Document("_id", "policy-1").append("email", USER)
                .append("agent_name", "Ravi Kumar").append("premium", 12000.0)
                .append("policyDetails", List.of(new Document("policy_id", "LIC-778").append("notes", "Term cover"))));
    }

    @Test
    void raise_whenAGrievanceIsStored_holdsItsTextEncryptedAndReadsItBack() throws IOException, InterruptedException {
        // Given / When
        given().header("Authorization", "Bearer " + generateToken(USER, "USER")).contentType(ContentType.JSON)
                .body("{\"text\": \"My old address is still shown\"}")
                .when().post("/privacy/grievances/user/" + USER).then().statusCode(201);

        // Then
        Document stored = mongoTemplate.getCollection(GRIEVANCES).find().first();
        assertThat(stored).isNotNull();
        assertThat(stored.getString("text")).startsWith("k1:").doesNotContain("address");
        HttpResponse<String> read = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create(RestAssured.baseURI + ":" + RestAssured.port + "/privacy/grievances/user/" + USER))
                .header("Authorization", "Bearer " + generateToken(USER, "USER")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(read.body()).contains("My old address is still shown");
        Document logged = mongoTemplate.getCollection("privacy_processing_log").find().first();
        assertThat(logged).isNotNull();
        assertThat(logged.getString("remote_address")).startsWith("k1:").doesNotContain("127.0.0.1");
    }

    @Test
    void save_whenAnInsuranceHoldsAgentAndPolicyDetails_storesThemEncryptedAndReadsThemBack() {
        // Given
        PolicyDetails details = new PolicyDetails();
        details.setPolicyId("LIC-778");
        details.setAgentEmail("ravi@agency.example");
        InsuranceEntity insurance = new InsuranceEntity();
        insurance.setId("policy-1");
        insurance.setEmail(USER);
        insurance.setAgentName("Ravi Kumar");
        insurance.setPolicyDetails(List.of(details));

        // When
        mongoTemplate.save(insurance);

        // Then
        Document stored = raw(INSURANCES, "policy-1");
        assertThat(stored.getString("agent_name")).startsWith("k1:");
        Document storedDetails = stored.getList("policyDetails", Document.class).getFirst();
        assertThat(storedDetails.getString("policy_id")).startsWith("k1:");
        assertThat(storedDetails.getString("agent_email")).startsWith("k1:");
        assertThat(stored.toJson()).doesNotContain("Ravi").doesNotContain("LIC-778").doesNotContain("agency");
        InsuranceEntity read = mongoTemplate.findById("policy-1", InsuranceEntity.class);
        assertThat(read.getAgentName()).isEqualTo("Ravi Kumar");
        assertThat(read.getPolicyDetails().getFirst().getPolicyId()).isEqualTo("LIC-778");
        assertThat(read.getPolicyDetails().getFirst().getAgentEmail()).isEqualTo("ravi@agency.example");
    }

    @Test
    void encrypt_whenADryRun_countsThePlainRowsAndChangesNothing() {
        // Given
        insertPlainRows();

        // When / Then — plain-1 and plain-2, not "sealed"; policy-1
        run(true, "SUPER_USER").statusCode(200)
                .body("collections.find { it.collection == 'privacy_grievances' }.plainRows", equalTo(2))
                .body("collections.find { it.collection == 'privacy_grievances' }.encryptedRows", equalTo(0))
                .body("collections.find { it.collection == 'insurances' }.plainRows", equalTo(1))
                .body("collections.find { it.collection == 'privacy_processing_log' }.plainRows", equalTo(0));
        assertThat(raw(GRIEVANCES, "plain-1").getString("text")).isEqualTo("Wrong address shown");
        assertThat(raw(INSURANCES, "policy-1").getString("agent_name")).isEqualTo("Ravi Kumar");
    }

    @Test
    void encrypt_whenRunTwice_encryptsThePlainRowsOnceAndTheyReadBack() {
        // Given
        insertPlainRows();

        // When
        run(false, "SUPER_USER").statusCode(200)
                .body("collections.find { it.collection == 'privacy_grievances' }.encryptedRows", equalTo(2))
                .body("collections.find { it.collection == 'insurances' }.encryptedRows", equalTo(1));

        // Then
        Document plainTwo = raw(GRIEVANCES, "plain-2");
        assertThat(plainTwo.getString("text")).startsWith("k1:");
        assertThat(plainTwo.getString("answer")).startsWith("k1:");
        Document policy = raw(INSURANCES, "policy-1");
        assertThat(policy.getString("agent_name")).startsWith("k1:");
        assertThat(policy.getDouble("premium")).isEqualTo(12000.0);
        assertThat(policy.getList("policyDetails", Document.class).getFirst().getString("notes")).startsWith("k1:");
        Grievance two = mongoTemplate.findById("plain-2", Grievance.class);
        assertThat(two.getText()).isEqualTo("Delete my old PAN");
        assertThat(two.getAnswer()).isEqualTo("Done on 3 June");
        assertThat(mongoTemplate.findById("sealed", Grievance.class).getText()).isEqualTo("Already encrypted");
        InsuranceEntity insurance = mongoTemplate.findById("policy-1", InsuranceEntity.class);
        assertThat(insurance.getPolicyDetails().getFirst().getPolicyId()).isEqualTo("LIC-778");
        run(false, "SUPER_USER").statusCode(200)
                .body("collections.plainRows", everyItem(equalTo(0)));
    }

    @Test
    void encrypt_whenTheCallerIsNotTheOwner_refuses() {
        // Given
        insertPlainRows();

        // When / Then
        run(false, "USER").statusCode(403);
        assertThat(raw(GRIEVANCES, "plain-1").getString("text")).isEqualTo("Wrong address shown");
    }
}
