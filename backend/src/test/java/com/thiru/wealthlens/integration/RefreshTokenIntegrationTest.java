package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.auth.dto.LoginRequest;
import com.thiru.wealthlens.auth.entity.UserDetail;
import com.thiru.wealthlens.auth.service.LoginErasure;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Refresh tokens (W-358b, D-853 C3a): a 15-minute access token and a refresh token that is rotated on each use, stored only as a
 * hash, refused when used twice (and then its whole family is), and ended by logout, a password change, a hold and erasure.
 */
class RefreshTokenIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "refresher@example.com";
    private static final String PASSWORD = "password123";
    private static final String REFRESH_TOKENS = "refresh_tokens";

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginErasure loginErasure;

    private void registered(String roles) {
        UserDetail user = new UserDetail();
        user.setEmail(EMAIL);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRoles(roles);
        mongoTemplate.save(user);
    }

    private ExtractableResponse<Response> login() {
        LoginRequest request = new LoginRequest();
        request.setEmail(EMAIL);
        request.setPassword(PASSWORD);
        return given().contentType(ContentType.JSON).body(request).when().post("/auth/login").then().extract();
    }

    private static String body(String refreshToken) {
        return "{\"refresh_token\":\"" + refreshToken + "\"}";
    }

    private ExtractableResponse<Response> refresh(String refreshToken) {
        return given().contentType(ContentType.JSON).body(body(refreshToken)).when().post("/auth/token/refresh").then().extract();
    }

    /** Over {@code java.net.http}: RestAssured's GET throws an NPE in this suite's Groovy dispatch (session 59). */
    private static int transactions(String accessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + io.restassured.RestAssured.port
                + "/transactions/user/" + EMAIL)).header("Authorization", "Bearer " + accessToken).GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static String part(String jwt, int index) {
        return new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[index]), StandardCharsets.UTF_8);
    }

    private List<Document> storedRefreshTokens() {
        return mongoTemplate.getCollection(REFRESH_TOKENS).find().into(new java.util.ArrayList<>());
    }

    @Test
    void login_whenSignedIn_returnsAFifteenMinuteAccessTokenWithAKeyIdAndARefreshToken() {
        // Given
        registered("USER");

        // When
        ExtractableResponse<Response> signedIn = login();

        // Then — 15 minutes = 900 seconds (C3a)
        assertThat(signedIn.statusCode()).isEqualTo(200);
        assertThat(signedIn.<Integer>path("expires_in")).isEqualTo(900);
        assertThat(signedIn.<String>path("refresh_token")).isNotBlank();
        assertThat(part(signedIn.path("access_token"), 0)).contains("\"kid\"").contains("\"HS256\"");
    }

    @Test
    void refresh_whenTheTokenIsCurrent_returnsANewPairWhoseAccessTokenWorks() throws Exception {
        // Given
        registered("USER");
        String first = login().path("refresh_token");

        // When
        ExtractableResponse<Response> refreshed = refresh(first);

        // Then
        assertThat(refreshed.statusCode()).isEqualTo(200);
        assertThat(refreshed.<String>path("refresh_token")).isNotBlank().isNotEqualTo(first);
        assertThat(refreshed.<Integer>path("expires_in")).isEqualTo(900);
        assertThat(transactions(refreshed.path("access_token"))).isEqualTo(200);
    }

    @Test
    void refresh_whenAnExpiredAccessTokenIsSentAlong_stillRefreshes() {
        // Given — a client sends its old access token with every call, the refresh included
        registered("USER");
        String first = login().path("refresh_token");

        // When
        int status = given().header("Authorization", "Bearer " + generateExpiredToken(EMAIL, "USER")).contentType(ContentType.JSON)
                .body(body(first)).when().post("/auth/token/refresh").then().extract().statusCode();

        // Then
        assertThat(status).isEqualTo(200);
    }

    @Test
    void refresh_whenATokenIsUsedTwice_refusesItAndEveryTokenOfItsFamily() {
        // Given
        registered("USER");
        String first = login().path("refresh_token");
        ExtractableResponse<Response> rotated = refresh(first);
        assertThat(rotated.statusCode()).isEqualTo(200);
        String second = rotated.path("refresh_token");

        // When — the first is presented again, as a thief holding a copy would
        int reused = refresh(first).statusCode();

        // Then — the rightful holder's newer token is ended with it
        assertThat(reused).isEqualTo(401);
        assertThat(refresh(second).statusCode()).isEqualTo(401);
    }

    @Test
    void refresh_whenRotated_keepsTheExpiryOfTheSignIn() {
        // Given
        registered("USER");
        String first = login().path("refresh_token");

        // When
        refresh(first);

        // Then — 14 days from the sign-in, the same on both; a refresh never extends it (a password sign-in at least every 14 days)
        List<Document> stored = storedRefreshTokens();
        assertThat(stored).hasSize(2);
        Date issued = stored.get(0).getDate("issued_at");
        assertThat(stored).allSatisfy(token -> assertThat(token.getDate("expires_at"))
                .isEqualTo(new Date(issued.getTime() + Duration.ofDays(14).toMillis())));
    }

    @Test
    void refresh_whenPastItsExpiry_refuses() {
        // Given
        registered("USER");
        String first = login().path("refresh_token");
        assertThat(storedRefreshTokens()).hasSize(1);
        mongoTemplate.getCollection(REFRESH_TOKENS).updateMany(new Document(),
                new Document("$set", new Document("expires_at", new Date(System.currentTimeMillis() - 1000))));

        // When / Then
        assertThat(refresh(first).statusCode()).isEqualTo(401);
    }

    @Test
    void refresh_whenTheRoleChanged_carriesTheCurrentRole() {
        // Given
        registered("USER");
        String first = login().path("refresh_token");
        UserDetail login = storedLogin(EMAIL);
        login.setRoles("SUPER_USER");
        mongoTemplate.save(login);

        // When
        String access = refresh(first).path("access_token");

        // Then
        assertThat(part(access, 1)).contains("\"ROLE_SUPER_USER\"").doesNotContain("\"ROLE_USER\"");
    }

    @Test
    void refresh_whenStored_keepsOnlyAHashAndTheEncryptedEmail() {
        // Given
        registered("USER");

        // When
        String first = login().path("refresh_token");

        // Then
        List<Document> stored = storedRefreshTokens();
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).toJson()).doesNotContain(first).doesNotContain(EMAIL);
        assertThat(stored.get(0).getString("email")).isEqualTo(storedIdentifier(EMAIL));
    }

    @Test
    void logout_whenGivenARefreshToken_endsItsFamily() {
        // Given
        registered("USER");
        String first = login().path("refresh_token");

        // When
        int status = given().contentType(ContentType.JSON).body(body(first)).when().post("/auth/logout").then().extract().statusCode();

        // Then
        assertThat(status).isEqualTo(204);
        assertThat(refresh(first).statusCode()).isEqualTo(401);
    }

    @Test
    void changePassword_whenDone_endsEveryRefreshToken() throws IOException, InterruptedException {
        // Given
        registered("USER");
        ExtractableResponse<Response> signedIn = login();
        String body = "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\",\"newPassword\":\"another-password\"}";

        // When
        HttpRequest change = HttpRequest.newBuilder(URI.create("http://localhost:" + io.restassured.RestAssured.port + "/auth/user/" + EMAIL
                        + "/change/password")).header("Authorization", "Bearer " + signedIn.<String>path("access_token"))
                .header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(body)).build();
        int status = HttpClient.newHttpClient().send(change, HttpResponse.BodyHandlers.discarding()).statusCode();

        // Then
        assertThat(status).isEqualTo(200);
        assertThat(refresh(signedIn.path("refresh_token")).statusCode()).isEqualTo(401);
    }

    @Test
    void refresh_whenTheAccountIsHeldForErasure_refuses() {
        // Given
        registered("USER");
        ExtractableResponse<Response> signedIn = login();
        assertThat(signedIn.<String>path("refresh_token")).isNotBlank();
        given().header("Authorization", "Bearer " + signedIn.<String>path("access_token")).when()
                .post("/privacy/erasure/user/" + EMAIL).then().statusCode(202);

        // When / Then
        assertThat(refresh(signedIn.path("refresh_token")).statusCode()).isEqualTo(401);
    }

    @Test
    void erase_whenTheLoginIsErased_removesItsRefreshTokens() {
        // Given
        registered("USER");
        login();
        assertThat(storedRefreshTokens()).hasSize(1);

        // When
        loginErasure.erase(EMAIL);

        // Then
        assertThat(storedRefreshTokens()).isEmpty();
    }
}
