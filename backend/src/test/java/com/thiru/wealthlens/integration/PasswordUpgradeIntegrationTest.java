package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.auth.entity.UserDetail;
import com.thiru.wealthlens.auth.repository.UserDetailsRepository;
import com.thiru.wealthlens.auth.service.RegisteredUserDirectory;
import io.restassured.RestAssured;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stored passwords move to Argon2id the next time their owner signs in (W-358d3, D-885): the password is known only then, so the
 * sign-in is when its stored hash is rewritten, and nobody is asked to reset. Every hash stored before still signs in.
 */
class PasswordUpgradeIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "the-password-1";

    @Autowired
    private UserDetailsRepository logins;

    @Autowired
    private RegisteredUserDirectory directory;

    @Test
    void signIn_whenTheStoredHashIsTodaysBcrypt_rewritesItAsArgon2AndStillSignsIn() throws IOException, InterruptedException {
        // Given — a login stored as every login is today: BCrypt cost 10, no label
        stored("upgrade@example.com", new BCryptPasswordEncoder().encode(PASSWORD));

        // When
        int first = signIn("upgrade@example.com", PASSWORD);

        // Then
        assertThat(first).isEqualTo(200);
        assertThat(hashOf("upgrade@example.com")).startsWith("{argon2}$argon2id$");
        assertThat(signIn("upgrade@example.com", PASSWORD)).isEqualTo(200);
    }

    @Test
    void signIn_whenThePasswordIsWrong_rewritesNothing() throws IOException, InterruptedException {
        // Given
        String today = new BCryptPasswordEncoder().encode(PASSWORD);
        stored("wrong@example.com", today);

        // When
        int answer = signIn("wrong@example.com", "not-the-password");

        // Then
        assertThat(answer).isEqualTo(401);
        assertThat(hashOf("wrong@example.com")).isEqualTo(today);
    }

    @Test
    void signIn_whenTheStoredHashCameFromHtpasswd_signsIn() throws IOException, InterruptedException {
        // Given — the owner's resets during W-334 were written by htpasswd -B, as $2y$
        stored("reset@example.com", new BCryptPasswordEncoder().encode(PASSWORD).replace("$2a$", "$2y$"));

        // When / Then
        assertThat(signIn("reset@example.com", PASSWORD)).isEqualTo(200);
        assertThat(hashOf("reset@example.com")).startsWith("{argon2}");
    }

    @Test
    void register_whenAccepted_storesArgon2() throws IOException, InterruptedException {
        // When
        int answer = post("/api/v1/public/auth/register", "{\"email\":\"new@example.com\",\"password\":\"" + PASSWORD
                + "\",\"privacyNoticeVersion\":\"" + noticeVersion() + "\"}", null).statusCode();

        // Then
        assertThat(answer).isEqualTo(200);
        assertThat(hashOf("new@example.com")).startsWith("{argon2}");
    }

    @Test
    void changePassword_whenAccepted_storesArgon2() throws IOException, InterruptedException {
        // Given
        stored("change@example.com", new BCryptPasswordEncoder().encode(PASSWORD));
        String body = "{\"email\":\"change@example.com\",\"password\":\"" + PASSWORD + "\",\"newPassword\":\"the-new-password-2\"}";

        // When
        HttpResponse<String> answer = send(HttpRequest.newBuilder(uri("/api/v1/me/auth/password")).header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + generateToken("change@example.com")).PUT(HttpRequest.BodyPublishers.ofString(body)));

        // Then
        assertThat(answer.statusCode()).isEqualTo(200);
        assertThat(hashOf("change@example.com")).startsWith("{argon2}");
        assertThat(signIn("change@example.com", "the-new-password-2")).isEqualTo(200);
    }

    @Test
    void signIn_whenTheLoginWasReKeyedWithAnObjectId_upgradesItInPlace() throws IOException, InterruptedException {
        // Given — W-369: every login re-keyed by §1.r holds an ObjectId _id (staging 14 of 14), where the app writes a string
        storedAsReKeyed("rekeyed@example.com", new BCryptPasswordEncoder().encode(PASSWORD));

        // When
        int first = signIn("rekeyed@example.com", PASSWORD);

        // Then — signed in and rewritten, still one login (a save had inserted a second and hit the unique email index: a 500)
        assertThat(first).isEqualTo(200);
        assertThat(hashOf("rekeyed@example.com")).startsWith("{argon2}$argon2id$");
        assertThat(mongoTemplate.getCollection("user_details").countDocuments()).isEqualTo(1);
        assertThat(signIn("rekeyed@example.com", PASSWORD)).isEqualTo(200);
    }

    @Test
    void changePassword_whenTheLoginWasReKeyedWithAnObjectId_changesItInPlace() throws IOException, InterruptedException {
        // Given — W-369: the same save failed here since m1.4 for every re-keyed login
        storedAsReKeyed("rekeyed-change@example.com", new BCryptPasswordEncoder().encode(PASSWORD));
        String body = "{\"email\":\"rekeyed-change@example.com\",\"password\":\"" + PASSWORD + "\",\"newPassword\":\"the-new-password-2\"}";

        // When
        HttpResponse<String> answer = send(HttpRequest.newBuilder(uri("/api/v1/me/auth/password")).header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + generateToken("rekeyed-change@example.com")).PUT(HttpRequest.BodyPublishers.ofString(body)));

        // Then
        assertThat(answer.statusCode()).as(answer.body()).isEqualTo(200);
        assertThat(mongoTemplate.getCollection("user_details").countDocuments()).isEqualTo(1);
        assertThat(signIn("rekeyed-change@example.com", "the-new-password-2")).isEqualTo(200);
    }

    @Test
    void storePassword_whenNoLoginHoldsTheEmail_refusesRatherThanStoringNothing() {
        // When / Then — W-369: a targeted update that matched no row would otherwise report nothing
        assertThatThrownBy(() -> directory.storePassword("nobody@example.com", "{argon2}x")).isInstanceOf(IllegalStateException.class)
                .hasMessage("No login holds this email to store its password");
        assertThat(mongoTemplate.getCollection("user_details").countDocuments()).isZero();
    }

    /** A login as runbook §1.r's re-key left it: the row the app would write, its _id replaced by an ObjectId. */
    private void storedAsReKeyed(String email, String hash) {
        stored(email, hash);
        Document row = mongoTemplate.getCollection("user_details").find(new Document("password", hash)).first();
        mongoTemplate.getCollection("user_details").deleteOne(new Document("_id", row.get("_id")));
        row.put("_id", new ObjectId());
        mongoTemplate.getCollection("user_details").insertOne(row);
    }

    private void stored(String email, String hash) {
        UserDetail login = new UserDetail();
        login.setEmail(email);
        login.setPassword(hash);
        login.setRoles("USER");
        mongoTemplate.save(login);
    }

    private String hashOf(String email) {
        return logins.findByEmail(email).orElseThrow().getPassword();
    }

    private static int signIn(String email, String password) throws IOException, InterruptedException {
        return post("/api/v1/public/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null).statusCode();
    }

    private static HttpResponse<String> post(String path, String body, String token) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private static String noticeVersion() throws IOException, InterruptedException {
        String rights = send(HttpRequest.newBuilder(uri("/api/v1/public/privacy/rights")).GET()).body();
        return JsonMapper.builder().build().readTree(rights).get("version").asString();
    }

    private static URI uri(String path) {
        return URI.create(RestAssured.baseURI + ":" + RestAssured.port + path);
    }

    private static HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
