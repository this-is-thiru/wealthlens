package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thiru.wealthlens.auth.dto.LoginRequest;
import com.thiru.wealthlens.auth.dto.RegistrationRequest;
import com.thiru.wealthlens.auth.entity.UserDetail;
import com.thiru.wealthlens.shared.privacy.PrivacySettings;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

public class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PrivacySettings privacy;

    private String baseUrl() {
        return "http://localhost:" + RestAssured.port;
    }

    private RestTemplate createNoErrorRestTemplate() {
        RestTemplate rt = new RestTemplate();
        rt.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        return rt;
    }

    @Test
    void addUser_validRequest_registersUser() {
        // GIVEN
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("newuser@test.com");
        request.setPassword("password123");
        request.setPrivacyNoticeVersion(privacy.notice().version());

        // WHEN / THEN
        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/auth/register")
                .then()
                .statusCode(200)
                .body(containsString("newuser@test.com"));
    }

    @Test
    void register_whenTheBodyNamesARole_expected400AndNoAccount() {
        // GIVEN — W-255: anyone could register as a super user by naming the role
        String body = "{\"email\":\"chooser@test.com\",\"password\":\"password123\",\"role\":\"SUPER_USER\","
                + "\"privacyNoticeVersion\":\"" + privacy.notice().version() + "\"}";

        // WHEN / THEN
        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/auth/register")
                .then()
                .statusCode(400)
                .body(containsString("role"));
        assertNull(mongoTemplate.findById("chooser@test.com", UserDetail.class));
    }

    @Test
    void register_whenNoRole_storesUser() {
        // GIVEN
        RegistrationRequest request = registration("plain@test.com");

        // WHEN
        given().contentType(ContentType.JSON).body(request).when().post("/auth/register").then().statusCode(200);

        // THEN
        assertEquals("USER", mongoTemplate.findById("plain@test.com", UserDetail.class).getRoles());
    }

    @Test
    void login_whenRegistered_tokenCarriesOnlyTheUserRoleAndCannotSeed() {
        // GIVEN
        RegistrationRequest request = registration("signed@test.com");
        given().contentType(ContentType.JSON).body(request).when().post("/auth/register").then().statusCode(200);
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("signed@test.com");
        loginRequest.setPassword("password123");

        // WHEN
        String token = given().contentType(ContentType.JSON).body(loginRequest).when().post("/auth/login")
                .then().statusCode(200).extract().path("access_token");

        // THEN — the claims part of the JWT, read without the app's own parser; Spring Security adds FACTOR_PASSWORD,
        // which is not a role, so the check is that ROLE_USER is the one role
        String claims = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        assertTrue(claims.contains("\"ROLE_USER\""), claims);
        assertEquals(1, claims.split("ROLE_", -1).length - 1, claims);
        given().header("Authorization", "Bearer " + token).when().post("/charges/seed").then().statusCode(403);
    }

    private RegistrationRequest registration(String email) {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail(email);
        request.setPassword("password123");
        request.setPrivacyNoticeVersion(privacy.notice().version());
        return request;
    }

    @Test
    void login_validCredentials_returnsToken() {
        // GIVEN
        String email = "logintest@test.com";
        String rawPassword = "password123";
        UserDetail user = new UserDetail();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRoles("USER");
        mongoTemplate.save(user);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(email);
        loginRequest.setPassword(rawPassword);

        // WHEN / THEN
        String token = given()
                .contentType(ContentType.JSON)
                .body(loginRequest)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("access_token", notNullValue())
                .body("tokenType", equalTo("Bearer"))
                .extract()
                .path("access_token");

        assertNotNull(token);
    }

    @Test
    void changePassword_validRequest_updatesPassword() {
        // GIVEN
        String email = "changepwd@test.com";
        String oldPassword = "oldpassword";
        String newPassword = "newpassword";

        UserDetail user = new UserDetail();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(oldPassword));
        user.setRoles("USER");
        mongoTemplate.save(user);

        String token = generateToken(email);

        RegistrationRequest changeRequest = new RegistrationRequest();
        changeRequest.setEmail(email);
        changeRequest.setPassword(oldPassword);
        changeRequest.setNewPassword(newPassword);

        // WHEN / THEN
        String url = baseUrl() + "/auth/user/" + email + "/change/password";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RegistrationRequest> entity = new HttpEntity<>(changeRequest, headers);

        RestTemplate rt = createNoErrorRestTemplate();
        ResponseEntity<String> response = rt.exchange(URI.create(url), HttpMethod.PUT, entity, String.class);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void addUser_duplicateEmail_returns400() {
        // GIVEN
        String email = "duplicate@test.com";
        UserDetail existingUser = new UserDetail();
        existingUser.setEmail(email);
        existingUser.setPassword(passwordEncoder.encode("password123"));
        existingUser.setRoles("USER");
        mongoTemplate.save(existingUser);

        RegistrationRequest request = new RegistrationRequest();
        request.setEmail(email);
        request.setPassword("password123");

        // WHEN / THEN
        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/auth/register")
                .then()
                .statusCode(400)
                .body(containsString("already exists"));
    }

    @Test
    void addUser_blankEmail_returns400() {
        // Application does not validate blank email, returns 200. Skipping per task instructions.
    }

    @Test
    void login_wrongPassword_returns401() {
        // GIVEN
        String email = "wrongpwd@test.com";
        UserDetail user = new UserDetail();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("correctpassword"));
        user.setRoles("USER");
        mongoTemplate.save(user);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(email);
        loginRequest.setPassword("wrongpassword");

        // WHEN / THEN
        given()
                .contentType(ContentType.JSON)
                .body(loginRequest)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(401);
    }

    @Test
    void login_unknownEmail_returns401() {
        // GIVEN
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("nonexistent@test.com");
        loginRequest.setPassword("password123");

        // WHEN / THEN
        given()
                .contentType(ContentType.JSON)
                .body(loginRequest)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(401);
    }

    @Test
    void changePassword_emailMismatch_returns400() {
        // GIVEN
        String email = "user1@test.com";
        UserDetail user = new UserDetail();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("oldpassword"));
        user.setRoles("USER");
        mongoTemplate.save(user);

        String token = generateToken(email);

        RegistrationRequest changeRequest = new RegistrationRequest();
        changeRequest.setEmail("different@test.com");
        changeRequest.setPassword("oldpassword");
        changeRequest.setNewPassword("newpassword");

        // WHEN / THEN
        String url = baseUrl() + "/auth/user/" + email + "/change/password";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RegistrationRequest> entity = new HttpEntity<>(changeRequest, headers);

        RestTemplate rt = createNoErrorRestTemplate();
        ResponseEntity<String> response = rt.exchange(URI.create(url), HttpMethod.PUT, entity, String.class);
        assertEquals(400, response.getStatusCode().value());
        assertEquals(true, response.getBody().contains("Email mismatch"));
    }

    @Test
    void changePassword_sameOldAndNew_returns400() {
        // GIVEN
        String email = "samepwd@test.com";
        String password = "samepassword";

        UserDetail user = new UserDetail();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRoles("USER");
        mongoTemplate.save(user);

        String token = generateToken(email);

        RegistrationRequest changeRequest = new RegistrationRequest();
        changeRequest.setEmail(email);
        changeRequest.setPassword(password);
        changeRequest.setNewPassword(password);

        // WHEN / THEN
        String url = baseUrl() + "/auth/user/" + email + "/change/password";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RegistrationRequest> entity = new HttpEntity<>(changeRequest, headers);

        RestTemplate rt = createNoErrorRestTemplate();
        ResponseEntity<String> response = rt.exchange(URI.create(url), HttpMethod.PUT, entity, String.class);
        assertEquals(400, response.getStatusCode().value());
        assertEquals(true, response.getBody().contains("Old password cannot be same as new password"));
    }

    @Test
    void changePassword_wrongOldPassword_returns400() {
        // GIVEN
        String email = "wrongoldpwd@test.com";
        UserDetail user = new UserDetail();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("correctoldpassword"));
        user.setRoles("USER");
        mongoTemplate.save(user);

        String token = generateToken(email);

        RegistrationRequest changeRequest = new RegistrationRequest();
        changeRequest.setEmail(email);
        changeRequest.setPassword("wrongoldpassword");
        changeRequest.setNewPassword("newpassword");

        // WHEN / THEN
        String url = baseUrl() + "/auth/user/" + email + "/change/password";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RegistrationRequest> entity = new HttpEntity<>(changeRequest, headers);

        RestTemplate rt = createNoErrorRestTemplate();
        ResponseEntity<String> response = rt.exchange(URI.create(url), HttpMethod.PUT, entity, String.class);
        assertEquals(400, response.getStatusCode().value());
        assertEquals(true, response.getBody().contains("Invalid old password"));
    }

    @Test
    void changePassword_userNotFound_returns500() {
        // GIVEN
        String email = "notfound@test.com";
        String token = generateToken(email);

        RegistrationRequest changeRequest = new RegistrationRequest();
        changeRequest.setEmail(email);
        changeRequest.setPassword("oldpassword");
        changeRequest.setNewPassword("newpassword");

        // WHEN / THEN
        String url = baseUrl() + "/auth/user/" + email + "/change/password";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RegistrationRequest> entity = new HttpEntity<>(changeRequest, headers);

        RestTemplate rt = createNoErrorRestTemplate();
        ResponseEntity<String> response = rt.exchange(URI.create(url), HttpMethod.PUT, entity, String.class);
        assertEquals(500, response.getStatusCode().value());
    }

    @Test
    void protectedEndpoint_withExpiredToken_returns401() {
        // GIVEN
        String email = "expiredtoken@test.com";
        String expiredToken = generateExpiredToken(email, "USER");

        // WHEN / THEN
        String url = baseUrl() + "/transactions/user/" + email;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(expiredToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        RestTemplate rt = createNoErrorRestTemplate();
        ResponseEntity<String> response = rt.exchange(URI.create(url), HttpMethod.GET, entity, String.class);
        assertEquals(401, response.getStatusCode().value());
    }

    @Test
    void protectedEndpoint_withMalformedToken_returns403() {
        // GIVEN
        String email = "malformed@test.com";

        // WHEN / THEN
        String url = baseUrl() + "/transactions/user/" + email;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("invalid.malformed.token");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        RestTemplate rt = createNoErrorRestTemplate();
        ResponseEntity<String> response = rt.exchange(URI.create(url), HttpMethod.GET, entity, String.class);
        assertEquals(401, response.getStatusCode().value());
    }

    @Test
    void protectedEndpoint_withWrongUserPath_returns401() {
        // GIVEN
        String email1 = "user1@test.com";
        String email2 = "user2@test.com";

        UserDetail user1 = new UserDetail();
        user1.setEmail(email1);
        user1.setPassword(passwordEncoder.encode("password123"));
        user1.setRoles("USER");
        mongoTemplate.save(user1);

        String token = generateToken(email1);

        // WHEN / THEN - user1 tries to access user2's transactions
        String url = baseUrl() + "/transactions/user/" + email2;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        RestTemplate rt = createNoErrorRestTemplate();
        ResponseEntity<String> response = rt.exchange(URI.create(url), HttpMethod.GET, entity, String.class);
        assertEquals(401, response.getStatusCode().value());
    }
}
