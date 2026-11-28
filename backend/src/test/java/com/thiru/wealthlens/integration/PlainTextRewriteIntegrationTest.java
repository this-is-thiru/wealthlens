package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.auth.dto.LoginRequest;
import com.thiru.wealthlens.auth.dto.RegistrationRequest;
import com.thiru.wealthlens.shared.privacy.PrivacySettings;
import com.thiru.wealthlens.shared.protection.FieldCipher;
import com.thiru.wealthlens.shared.protection.PlainTextRewrite;
import com.thiru.wealthlens.shared.protection.PlainTextRewrite.RewrittenColumn;
import io.restassured.http.ContentType;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The one-time rewrite of W-428a (D-1036 C1a): rows stored before it, in the {@code k1:} form, are made plain by their form,
 * whatever the column; a second run changes nothing; a sign-in, which looks the email up plain, works only after it.
 */
class PlainTextRewriteIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "rewrite.owner@example.com";
    private static final String PASSWORD = "password123";
    private static final byte[] DOCUMENT = "%PDF-1.7 a stored document".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private PlainTextRewrite rewrite;
    @Autowired
    private FieldCipher cipher;
    @Autowired
    private PrivacySettings privacy;

    @Test
    void rewriteAll_whenRowsHoldTheOldCiphertext_makesThemPlainAndASecondRunChangesNothing() {
        // Given — a login, a transaction's email, request copy and comment, and a document's bytes, as stored before W-428a
        JdbcTemplate jdbc = new JdbcTemplate(postgresDataSource);
        register();
        jdbc.update("UPDATE user_details SET email = ? WHERE email = ?", cipher.encryptSearchable(EMAIL), EMAIL);
        jdbc.update("INSERT INTO transactions (id, email, comment, asset_request_json) VALUES (?, ?, ?, ?)", "t1",
                cipher.encryptSearchable(EMAIL), "the old form starts with k1:",
                "{\"email\":\"" + cipher.encryptSearchable(EMAIL) + "\",\"accountHolder\":\"" + cipher.encryptSearchable("Lakshmi")
                        + "\",\"stockCode\":\"TCS\"}");
        jdbc.update("INSERT INTO tax_document_bytes (document_id, bytes, uploaded_at) VALUES (?, ?, ?)", "d1",
                cipher.encryptBytes(DOCUMENT), LocalDateTime.of(2026, 11, 28, 10, 0));
        login(401);

        // When
        List<RewrittenColumn> first = rewrite.rewriteAll();
        List<RewrittenColumn> second = rewrite.rewriteAll();

        // Then — each sealed value plain; the comment only names the prefix, so it is counted and left
        assertThat(jdbc.queryForObject("SELECT email FROM user_details", String.class)).isEqualTo(EMAIL);
        assertThat(jdbc.queryForMap("SELECT email, comment, asset_request_json FROM transactions WHERE id = 't1'"))
                .containsEntry("email", EMAIL).containsEntry("comment", "the old form starts with k1:")
                .containsEntry("asset_request_json", "{\"email\":\"" + EMAIL + "\",\"accountHolder\":\"Lakshmi\",\"stockCode\":\"TCS\"}");
        assertThat(jdbc.queryForObject("SELECT bytes FROM tax_document_bytes WHERE document_id = 'd1'", byte[].class)).isEqualTo(DOCUMENT);
        assertThat(first).containsExactlyInAnyOrder(new RewrittenColumn("tax_document_bytes", "bytes", 1, 0),
                new RewrittenColumn("transactions", "asset_request_json", 1, 0), new RewrittenColumn("transactions", "comment", 0, 1),
                new RewrittenColumn("transactions", "email", 1, 0), new RewrittenColumn("user_details", "email", 1, 0));
        assertThat(second).containsExactly(new RewrittenColumn("transactions", "comment", 0, 1));
        login(200);
    }

    @Test
    void rewriteAll_whenAValueWillNotDecrypt_failsWithItsColumnUntouched() {
        // Given — two sealed emails in one column, the second altered so it will not open
        JdbcTemplate jdbc = new JdbcTemplate(postgresDataSource);
        jdbc.update("INSERT INTO user_details (id, email) VALUES (?, ?), (?, ?)", "u1", cipher.encryptSearchable(EMAIL), "u2",
                "k1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");

        // When / Then
        assertThatThrownBy(rewrite::rewriteAll).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForList("SELECT email FROM user_details ORDER BY id", String.class))
                .allSatisfy(email -> assertThat(email).startsWith("k1:"));
    }

    private void register() {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail(EMAIL);
        request.setPassword(PASSWORD);
        request.setPrivacyNoticeVersion(privacy.notice().version());
        given().contentType(ContentType.JSON).body(request).when().post("/api/v1/public/auth/register").then().statusCode(200);
    }

    private void login(int status) {
        LoginRequest request = new LoginRequest();
        request.setEmail(EMAIL);
        request.setPassword(PASSWORD);
        given().contentType(ContentType.JSON).body(request).when().post("/api/v1/public/auth/login").then().statusCode(status);
    }
}
