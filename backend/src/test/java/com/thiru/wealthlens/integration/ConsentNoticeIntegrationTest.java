package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.thiru.wealthlens.auth.dto.AuthHelper;
import com.thiru.wealthlens.auth.dto.LoginRequest;
import com.thiru.wealthlens.auth.dto.RegistrationRequest;
import com.thiru.wealthlens.auth.entity.UserDetail;
import com.thiru.wealthlens.shared.privacy.PersonalDataHolder;
import com.thiru.wealthlens.shared.privacy.PersonalDataItem;
import com.thiru.wealthlens.shared.privacy.PrivacyConsent;
import com.thiru.wealthlens.shared.privacy.PrivacyNotice;
import com.thiru.wealthlens.shared.privacy.PrivacyRecordErasure;
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
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

/**
 * The notice before consent (DPDP Act, 2023 s.5, s.6(10); Rules r.3; W-250): registration is refused unless it sends back the
 * version of the notice now published, and records the consent against it; an account registered before W-250 is given the
 * notice at its next sign-in, recorded once; and every store of personal data is described in the notice.
 */
class ConsentNoticeIntegrationTest extends AbstractIntegrationTest {

    private static final String ANU = "anu@example.com";

    private static final String PASSWORD = "secret-1";

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private List<PersonalDataHolder> holders;

    @Autowired
    private PrivacyRecordErasure privacyRecordErasure;

    @Test
    void register_whenThePublishedVersionIsSentBack_recordsTheConsentAgainstIt() {
        // Given
        String version = publishedVersion();

        // When
        register(version).statusCode(200);

        // Then — given at the registration, the moment the account's sign-in count also starts (W-249)
        PrivacyConsent consent = onlyConsentOf(ANU);
        assertThat(consent.getNoticeVersion()).isEqualTo(version);
        assertThat(consent.getGivenAt()).isEqualTo(mongoTemplate.findById(ANU, UserDetail.class).getLastSignedInAt());
        assertThat(consent.getNoticedAt()).isNull();
    }

    @Test
    void register_whenNoVersionIsSent_isRefusedAndStoresNothing() {
        // When / Then
        register(null).statusCode(400);
        assertThat(mongoTemplate.findById(ANU, UserDetail.class)).isNull();
        assertThat(mongoTemplate.find(query(where("email").is(ANU)), PrivacyConsent.class)).isEmpty();
    }

    @Test
    void register_whenAnotherVersionIsSent_isRefusedAndStoresNothing() {
        // When / Then — a notice the person was not shown
        register("0").statusCode(400);
        assertThat(mongoTemplate.findById(ANU, UserDetail.class)).isNull();
        assertThat(mongoTemplate.find(query(where("email").is(ANU)), PrivacyConsent.class)).isEmpty();
    }

    @Test
    void login_whenTheAccountPredatesTheNotice_givesItAndRecordsItOnce() {
        // Given an account saved as registration did before W-250
        UserDetail user = new UserDetail();
        user.setEmail(ANU);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRoles("USER");
        user.setLastSignedInAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        mongoTemplate.save(user);
        String version = publishedVersion();

        // When
        JsonPath first = signIn();
        PrivacyConsent recorded = onlyConsentOf(ANU);
        signIn();

        // Then — s.5(2)(a): the link and version in the response; one record, its time unchanged by the second sign-in
        assertThat(first.getString("privacy_notice")).isEqualTo("/privacy");
        assertThat(first.getString("privacy_notice_version")).isEqualTo(version);
        assertThat(recorded.getNoticeVersion()).isEqualTo(version);
        assertThat(recorded.getNoticedAt()).isNotNull();
        assertThat(recorded.getGivenAt()).isNull();
        assertThat(onlyConsentOf(ANU).getNoticedAt()).isEqualTo(recorded.getNoticedAt());
    }

    @Test
    void login_whenTheAccountConsentedAtRegistration_addsNoNoticeRecord() {
        // Given
        register(publishedVersion()).statusCode(200);

        // When
        signIn();

        // Then
        PrivacyConsent consent = onlyConsentOf(ANU);
        assertThat(consent.getGivenAt()).isNotNull();
        assertThat(consent.getNoticedAt()).isNull();
    }

    @Test
    void erase_whenTheAccountIsErased_itsConsentGoesWithIt() {
        // Given
        register(publishedVersion()).statusCode(200);

        // When
        privacyRecordErasure.erase(ANU);

        // Then
        assertThat(mongoTemplate.find(query(where("email").is(ANU)), PrivacyConsent.class)).isEmpty();
    }

    @Test
    void notice_whenAStoreOfPersonalDataExists_describesItAndNothingElse() {
        // Given
        PrivacyNotice notice = publishedNotice();

        // When / Then — r.3(b)(i): a holder added without an entry, or an entry left after its holder, fails here
        assertThat(notice.consent().personalData()).extracting(PersonalDataItem::part)
                .containsExactlyInAnyOrderElementsOf(holders.stream().map(PersonalDataHolder::noticePart).toList());
    }

    @Test
    void notice_whenOpenedAsAPage_showsWhatTheNoticeBeforeConsentNeeds() {
        // Given
        PrivacyNotice notice = publishedNotice();

        // When
        String page = get("/privacy");

        // Then — r.3(b), (c); each value as application-integration-test.yaml states it
        assertThat(page).contains(notice.consent().personalData().stream().map(PersonalDataItem::description).toList());
        assertThat(page).contains(
                "plan your income tax, for you, while you keep your account",
                "Withdraw your consent at any time by asking for your whole account to be erased",
                "Data Protection Board of India, which works as a digital office (DPDP Rules r.20)",
                "(s.13(3))",
                notice.version());
    }

    private PrivacyNotice publishedNotice() {
        return JsonMapper.builder().build().readValue(get("/privacy/rights"), PrivacyNotice.class);
    }

    private String publishedVersion() {
        return publishedNotice().version();
    }

    /* RestAssured's GET fails inside its Groovy dispatch in this suite; PrivacyNoticeIntegrationTest reads the same way. */
    private static String get(String path) {
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                    URI.create(RestAssured.baseURI + ":" + RestAssured.port + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as("GET %s", path).isEqualTo(200);
            return response.body();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private ValidatableResponse register(String version) {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail(ANU);
        request.setPassword(PASSWORD);
        request.setRole(AuthHelper.Role.USER);
        request.setPrivacyNoticeVersion(version);
        return given().contentType(ContentType.JSON).body(request).when().post("/auth/register").then();
    }

    private JsonPath signIn() {
        LoginRequest request = new LoginRequest();
        request.setEmail(ANU);
        request.setPassword(PASSWORD);
        return given().contentType(ContentType.JSON).body(request).when().post("/auth/login").then().statusCode(200)
                .extract().jsonPath();
    }

    private PrivacyConsent onlyConsentOf(String email) {
        List<PrivacyConsent> consents = mongoTemplate.find(query(where("email").is(email)), PrivacyConsent.class);
        assertThat(consents).hasSize(1);
        return consents.getFirst();
    }
}
