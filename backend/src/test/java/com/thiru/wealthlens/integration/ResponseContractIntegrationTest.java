package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import io.restassured.RestAssured;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * What a caller reads off the routes that used to answer with stored documents (W-358c2, D-857): the owner's email never reaches a
 * user through reference data, and no response carries what is stored only for the app.
 */
class ResponseContractIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER = "the-owner@example.com";
    private static final String USER = "a-user@example.com";

    private static HttpResponse<String> call(String method, String path, String token, String body) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(RestAssured.baseURI + ":" + RestAssured.port + path))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void referenceData_whenTheOwnerSeededAndAddedIt_answersAUserWithoutTheOwnersEmail() throws IOException, InterruptedException {
        // Given — the owner seeds the rate cards and adds a corporate action, so auditing records the owner as the author
        String owner = generateToken(OWNER, "SUPER_USER");
        assertThat(call("POST", "/api/v1/admin/charges/seed", owner, null).statusCode()).isEqualTo(200);
        assertThat(call("POST", "/api/v1/admin/corporate-actions/add", owner, """
                {"stockCode": "INFY", "stockName": "INFY", "type": "BONUS", "assetType": "EQUITY", "ratio": "1:1", "priority": 1,
                 "recordDate": "2026-01-20", "exDate": "2026-01-20", "allotmentDate": "2026-01-22"}""").statusCode()).isEqualTo(200);
        String user = generateToken(USER);

        // When
        HttpResponse<String> cards = call("GET", "/api/v1/me/charges/charge-schedules?broker=ZERODHA", user, null);
        HttpResponse<String> catalogue = call("GET", "/api/v1/me/charges/charge-catalogue", user, null);
        HttpResponse<String> actions = call("GET", "/api/v1/me/corporate-actions/all", user, null);

        // Then
        for (HttpResponse<String> read : java.util.List.of(cards, catalogue, actions)) {
            assertThat(read.statusCode()).as(read.uri().toString()).isEqualTo(200);
            assertThat(read.body()).as(read.uri().toString()).isNotEqualTo("[]").doesNotContain(OWNER).doesNotContain("auditMetadata");
        }
    }

    @Test
    void ownRows_whenRead_carryNothingStoredOnlyForTheApp() throws IOException, InterruptedException {
        // Given — a trade and a temporary one with the fields only the app reads, and a holding
        mongoTemplate.save(transaction("T-1", "PROCESSED"));
        mongoTemplate.save(transaction("T-2", "TEMPORARY"));
        AssetEntity holding = new AssetEntity();
        holding.setEmail(USER);
        holding.setStockCode("INFY");
        holding.setStockName("INFY");
        holding.setBrokerName(BrokerName.ZERODHA);
        holding.setAssetType(AssetType.EQUITY);
        holding.setQuantity(10.0);
        holding.setTransactionDate(LocalDate.of(2026, 1, 5));
        mongoTemplate.save(holding);
        String user = generateToken(USER);

        // When
        HttpResponse<String> all = call("GET", "/api/v1/me/portfolio/all/transactions", user, null);
        HttpResponse<String> temporary = call("GET", "/api/v1/me/portfolio/temporary-transactions/all", user, null);
        HttpResponse<String> stocks = call("GET", "/api/v1/me/portfolio/stocks/all", user, null);

        // Then
        for (HttpResponse<String> read : java.util.List.of(all, temporary, stocks)) {
            assertThat(read.statusCode()).as(read.uri().toString()).isEqualTo(200);
            assertThat(read.body()).as(read.uri().toString()).isNotEqualTo("[]").contains("INFY").doesNotContain("auditMetadata")
                    .doesNotContain("idempotencyKey").doesNotContain("tradeFingerprint").doesNotContain("submittedAt")
                    .doesNotContain("assetRequest").doesNotContain("tradeFlow");
        }
    }

    private static TransactionEntity transaction(String id, String status) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(id);
        transaction.setEmail(USER);
        transaction.setStockCode("INFY");
        transaction.setStockName("INFY");
        transaction.setBrokerName(BrokerName.ZERODHA);
        transaction.setAssetType(AssetType.EQUITY);
        transaction.setQuantity(10.0);
        transaction.setTransactionType(TransactionType.BUY);
        transaction.setTransactionDate(LocalDate.of(2026, 1, 5));
        transaction.setIdempotencyKey("key-" + id);
        transaction.setTradeFingerprint("fingerprint-" + id);
        transaction.setStatus(com.thiru.wealthlens.portfolio.dto.enums.TransactionStatus.valueOf(status));
        return transaction;
    }
}
