package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.thiru.wealthlens.auth.dto.LoginRequest;
import com.thiru.wealthlens.brokercharges.repository.ChargeAccountRepository;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.shared.privacy.BreachNotice;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Identifiers stored plain before W-253 encrypted by the one-time endpoint (D-485 C3a), logins re-keyed (C2a), and a row
 * written since the deploy left as a conflict. The counts are by hand from the rows each test inserts.
 */
class StoredIdentifierEncryptionIntegrationTest extends AbstractIntegrationTest {

    private static final String USER = "holder@example.com";
    private static final String HOLDER = "Lakshmi Devi";
    private static final String DEMAT = "1208160000000001";
    private static final String PASSWORD = "Secret#123";
    private static final String OWNER = "owner@example.com";

    @Autowired
    private ChargeAccountRepository chargeAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private ValidatableResponse run(boolean dryRun) {
        return given().header("Authorization", "Bearer " + generateToken(OWNER, "SUPER_USER")).contentType(ContentType.JSON)
                .when().post("/privacy/stored-fields/encrypt?dryRun=" + dryRun).then().statusCode(200);
    }

    private static String counted(String collection, String count) {
        return "collections.find { it.collection == '" + collection + "' }." + count;
    }

    private Document raw(String collection, String id) {
        return mongoTemplate.getCollection(collection).find(new Document("_id", id)).first();
    }

    @Test
    void encrypt_whenIdentifiersAreStoredPlain_encryptsThemOnceAndTheQueriesFindThem() {
        // Given — a temporary transaction with its embedded request, and a demat account
        mongoTemplate.getCollection("transactions").insertOne(new Document("_id", "t1").append("email", USER)
                .append("account_holder", HOLDER).append("stock_code", "INFY")
                .append("asset_request", new Document("email", USER).append("accountHolder", HOLDER).append("stockCode", "INFY")));
        mongoTemplate.getCollection("charge_accounts").insertOne(new Document("_id", "c1").append("email", USER)
                .append("account_holder", HOLDER).append("broker_name", "ZERODHA").append("demat_account_id", DEMAT));
        mongoTemplate.getCollection("privacy_breach_notices").insertOne(new Document("_id", "n1").append("affected", List.of(USER, OWNER)));

        // When
        run(false).body(counted("transactions", "encryptedRows"), equalTo(1))
                .body(counted("charge_accounts", "encryptedRows"), equalTo(1))
                .body(counted("privacy_breach_notices", "encryptedRows"), equalTo(1));

        // Then
        Document transaction = raw("transactions", "t1");
        assertThat(List.of(transaction.getString("email"), transaction.getString("account_holder"),
                transaction.get("asset_request", Document.class).getString("email"),
                transaction.get("asset_request", Document.class).getString("accountHolder"))).allMatch(value -> value.startsWith("k1:"));
        assertThat(transaction.toJson()).doesNotContain("holder@").doesNotContain("Lakshmi").contains("INFY");
        assertThat(raw("charge_accounts", "c1").getString("demat_account_id")).startsWith("k1:");
        assertThat(raw("privacy_breach_notices", "n1").getList("affected", String.class))
                .containsExactly(storedIdentifier(USER), storedIdentifier(OWNER));
        assertThat(mongoTemplate.findById("n1", BreachNotice.class).getAffected()).containsExactly(USER, OWNER);
        assertThat(mongoTemplate.count(query(where("affected").is(OWNER)), BreachNotice.class)).isEqualTo(1);
        assertThat(chargeAccountRepository.findByEmailAndBrokerNameAndDematAccountId(USER, BrokerName.ZERODHA, DEMAT)).isPresent();
        List<TransactionEntity> found = mongoTemplate.find(query(where("email").is(USER).and("accountHolder").is(HOLDER)),
                TransactionEntity.class);
        assertThat(found).singleElement().satisfies(row -> assertThat(row.getAssetRequest().getEmail()).isEqualTo(USER));
        run(false).body("collections.plainRows", everyItem(equalTo(0)));
    }

    @Test
    void encrypt_whenALoginIsKeyedByItsEmail_rekeysItOnceAndTheUserSignsIn() {
        // Given
        mongoTemplate.getCollection("user_details").insertOne(new Document("_id", USER)
                .append("password", passwordEncoder.encode(PASSWORD)).append("roles", "USER"));

        // When — a dry run counts it and changes nothing; a real run re-keys it
        run(true).body(counted("user_details", "plainRows"), equalTo(1)).body(counted("user_details", "encryptedRows"), equalTo(0));
        assertThat(raw("user_details", USER)).isNotNull();
        run(false).body(counted("user_details", "encryptedRows"), equalTo(1));

        // Then
        assertThat(raw("user_details", USER)).isNull();
        Document rekeyed = mongoTemplate.getCollection("user_details").find().first();
        assertThat(rekeyed.getString("email")).isEqualTo(storedIdentifier(USER));
        LoginRequest login = new LoginRequest();
        login.setEmail(USER);
        login.setPassword(PASSWORD);
        given().contentType(ContentType.JSON).body(login).when().post("/auth/login").then().statusCode(200);
        run(false).body(counted("user_details", "plainRows"), equalTo(0));
        assertThat(mongoTemplate.getCollection("user_details").countDocuments()).isEqualTo(1);
    }

    @Test
    void encrypt_whenARowWasWrittenEncryptedSinceTheDeploy_countsTheConflictAndLeavesThePlainRow() {
        // Given — the same user and year twice, under the unique index {email, financial_year}
        mongoTemplate.getCollection("profit_and_loss").insertMany(List.of(
                new Document("_id", "p-plain").append("email", USER).append("financial_year", "2026-27"),
                new Document("_id", "p-new").append("email", storedIdentifier(USER)).append("financial_year", "2026-27")));

        // When / Then
        run(false).body(counted("profit_and_loss", "plainRows"), equalTo(1))
                .body(counted("profit_and_loss", "encryptedRows"), equalTo(0))
                .body(counted("profit_and_loss", "conflicts"), equalTo(1));
        assertThat(raw("profit_and_loss", "p-plain").getString("email")).isEqualTo(USER);
    }
}
