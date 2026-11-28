package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The instrument registry's aliases over a real database (W-440b, D-1055): each value a broker's file writes for an instrument names
 * one stock code, never two; a second identical post writes nothing; a value is kept exactly as the file writes it; the registry is a
 * {@code SUPER_USER}'s alone. ISINs and codes here are made up in the shapes the owner's sample files show.
 */
class InstrumentAliasIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN = "aliases-admin@example.com";
    private static final String ROUTE = "/api/v1/admin/portfolio/instrument-aliases";
    private static final String ISIN = "INE000A01011";

    @Test
    void register_whenNew_writesEachAliasAndListsIt() {
        // Given
        List<Map<String, Object>> aliases = List.of(alias("ISIN", null, ISIN, "ACME"), alias("BROKER_SYMBOL", "FYERS", "ACME LTD", "ACME"));

        // When
        post(aliases, "SUPER_USER").statusCode(200)
                .body("created", contains("ISIN " + ISIN + " -> ACME", "BROKER_SYMBOL FYERS ACME LTD -> ACME")).body("skipped", empty());

        // Then
        JsonPath listed = list();
        assertThat(listed.getInt("page.totalElements")).isEqualTo(2);
        assertThat(listed.getList("content.value", String.class)).containsExactly("ACME LTD", ISIN);
    }

    @Test
    void register_whenSentAgain_writesNothingAndSaysSo() {
        // Given
        List<Map<String, Object>> aliases = List.of(alias("ISIN", null, ISIN, "ACME"));
        post(aliases, "SUPER_USER").statusCode(200);

        // When
        post(aliases, "SUPER_USER").statusCode(200).body("created", empty()).body("skipped", contains("ISIN " + ISIN + " -> ACME"));

        // Then
        assertThat(list().getInt("page.totalElements")).isEqualTo(1);
    }

    @Test
    void register_whenTheValueAlreadyNamesAnotherCode_refusesTheWholeListNamingBoth() {
        // Given
        post(List.of(alias("ISIN", null, ISIN, "ACME")), "SUPER_USER").statusCode(200);

        // When — a new alias first, so a refusal that wrote part of the list would show
        post(List.of(alias("SCRIP_CODE", "UPSTOX", "500000", "ACME"), alias("ISIN", null, ISIN, "ACMENEW")), "SUPER_USER")
                .statusCode(400).body("detail", containsString(ISIN)).body("detail", containsString("ACME"))
                .body("detail", containsString("ACMENEW"));

        // Then
        assertThat(list().getInt("page.totalElements")).isEqualTo(1);
    }

    @Test
    void register_whenOneListNamesTwoCodesForAValue_refusesIt() {
        // When / Then
        post(List.of(alias("ISIN", null, ISIN, "ACME"), alias("ISIN", null, ISIN, "OTHER")), "SUPER_USER")
                .statusCode(400).body("detail", containsString("OTHER"));
        assertThat(list().getInt("page.totalElements")).isEqualTo(0);
    }

    @Test
    void register_whenTheSameValueIsAnotherBrokersSymbol_keepsBoth() {
        // When — a symbol is the broker's own, so two brokers may write one name for different instruments
        post(List.of(alias("BROKER_SYMBOL", "FYERS", "ACME", "ACME"), alias("BROKER_SYMBOL", "UPSTOX", "ACME", "ACMEINFRA")), "SUPER_USER")
                .statusCode(200);

        // Then
        assertThat(list().getInt("page.totalElements")).isEqualTo(2);
    }

    @Test
    void register_whenAnIsinNamesABroker_refusesIt() {
        // When / Then — an ISIN is the instrument's everywhere
        post(List.of(alias("ISIN", "ZERODHA", ISIN, "ACME")), "SUPER_USER").statusCode(400).body("detail", containsString("broker"));
    }

    @Test
    void register_whenABrokerValueNamesNoBroker_refusesIt() {
        // When / Then
        post(List.of(alias("BROKER_SYMBOL", null, "ACME LTD", "ACME")), "SUPER_USER").statusCode(400).body("detail", containsString("broker"));
        post(List.of(alias("SCRIP_CODE", null, "500000", "ACME")), "SUPER_USER").statusCode(400).body("detail", containsString("broker"));
    }

    @Test
    void register_whenAnIsinIsNotTwelveCharactersOfItsShape_refusesIt() {
        // When / Then — lower case, eleven characters, a letter last
        for (String isin : List.of("ine000a01011", "INE000A0101", "INE000A0101X")) {
            post(List.of(alias("ISIN", null, isin, "ACME")), "SUPER_USER").statusCode(400).body("detail", containsString(isin));
        }
    }

    @Test
    void register_whenAValueOrCodeHasSpaceAroundIt_refusesIt() {
        // When / Then — kept exactly as the file writes it, so a space a file never writes is a mistake, not a match
        post(List.of(alias("BROKER_SYMBOL", "FYERS", " ACME", "ACME")), "SUPER_USER").statusCode(400).body("detail", containsString("space"));
        post(List.of(alias("BROKER_SYMBOL", "FYERS", "ACME", "ACME ")), "SUPER_USER").statusCode(400).body("detail", containsString("space"));
    }

    @Test
    void register_whenAFieldIsMissing_refusesItNamingTheField() {
        // When / Then
        post(List.of(alias("ISIN", null, ISIN, null)), "SUPER_USER").statusCode(400).body("detail", containsString("stockCode"));
        post(List.of(), "SUPER_USER").statusCode(400).body("detail", containsString("aliases"));
    }

    @Test
    void register_whenTheCallerIsAUser_refusesIt() {
        // When / Then
        post(List.of(alias("ISIN", null, ISIN, "ACME")), "USER").statusCode(403);
        assertThat(get("USER").statusCode()).isEqualTo(403);
        assertThat(list().getInt("page.totalElements")).isEqualTo(0);
    }

    private ValidatableResponse post(List<Map<String, Object>> aliases, String role) {
        return given().header("Authorization", "Bearer " + generateToken(ADMIN, role)).contentType(ContentType.JSON)
                .body(Map.of("aliases", aliases)).post(ROUTE).then();
    }

    /** Read through {@link HttpClient}: a RestAssured GET throws inside its Groovy dispatch in this suite. */
    private HttpResponse<String> get(String role) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(RestAssured.baseURI + ":" + RestAssured.port + ROUTE))
                .header("Authorization", "Bearer " + generateToken(ADMIN, role)).GET().build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private JsonPath list() {
        HttpResponse<String> answer = get("SUPER_USER");
        assertThat(answer.statusCode()).as(answer.body()).isEqualTo(200);
        return JsonPath.from(answer.body());
    }

    private static Map<String, Object> alias(String kind, String broker, String value, String stockCode) {
        Map<String, Object> alias = new HashMap<>();
        alias.put("kind", kind);
        alias.put("brokerName", broker);
        alias.put("value", value);
        alias.put("stockCode", stockCode);
        return alias;
    }
}
