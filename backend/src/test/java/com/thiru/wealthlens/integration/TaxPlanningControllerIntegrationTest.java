package com.thiru.wealthlens.integration;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.thiru.wealthlens.taxplanning.enums.CityTier;
import com.thiru.wealthlens.taxplanning.enums.EmployerType;
import com.thiru.wealthlens.taxplanning.enums.ProfileType;
import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import com.thiru.wealthlens.taxplanning.policy.entity.TaxSlabPolicyEntity;
import com.thiru.wealthlens.taxplanning.salary.dto.SalaryComponentDto;
import com.thiru.wealthlens.taxplanning.salary.dto.SalaryProfileRequest;
import com.thiru.wealthlens.taxplanning.salary.dto.SalaryProfileResponse;
import io.restassured.RestAssured;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

public class TaxPlanningControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_A = "usera@example.com";
    private static final String USER_B = "userb@example.com";

    private String baseUrl() {
        return "http://localhost:" + RestAssured.port;
    }

    private RestTemplate createRestTemplate() {
        RestTemplate rt = new RestTemplate();
        rt.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return false;
            }
        });
        return rt;
    }

    // ============================================================
    // Helper builders
    // ============================================================

    private SalaryProfileRequest buildSalaryProfileRequest(String email, String regime) {
        SalaryProfileRequest req = new SalaryProfileRequest();
        req.setEmail(email);
        req.setProfileName("Test Profile");
        req.setProfileType(ProfileType.CURRENT);
        req.setEmployerName("TestCorp");
        req.setTaxYear("2025-26");
        req.setCityTier(CityTier.METRO_8);
        req.setCityName("Bengaluru");
        req.setRegimeType(RegimeType.valueOf(regime));
        req.setEmployerType(EmployerType.PRIVATE);
        req.setComponents(List.of(
                component("BASIC", 600000),
                component("HRA", 240000),
                component("SPECIAL_ALLOWANCE", 360000),
                component("NPS_EMPLOYER", 70000),
                component("EMPLOYEE_PF", 72000),
                component("EMPLOR_PF", 72000)
        ));
        req.setCarProvided(false);
        req.setInvestment80c(100000L);
        req.setInvestment80d(25000L);
        req.setMonthlyRentPaid(15000L);
        req.setIsPayingRent(true);
        req.setNumberOfChildren(1);
        req.setIsPhysicallyDisabled(false);
        return req;
    }

    private SalaryComponentDto component(String code, long amount) {
        SalaryComponentDto c = new SalaryComponentDto();
        c.setAllowanceCode(code);
        c.setAnnualAmount(amount);
        c.setIsCurrent(true);
        return c;
    }

    // ============================================================
    // 1. createProfile_success
    // ============================================================

    @Test
    void createProfile_success() {
        String token = generateToken(USER_A);
        SalaryProfileRequest req = buildSalaryProfileRequest(USER_A, "NEW_REGIME");

        String url = baseUrl() + "/tax-planning/user/" + USER_A + "/profile";
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new org.springframework.http.HttpEntity<>(req, headers);

        RestTemplate rt = createRestTemplate();
        var response = rt.exchange(URI.create(url), HttpMethod.POST, entity, SalaryProfileResponse.class);

        assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getId());
        assertEquals(USER_A, response.getBody().getEmail());
    }

    // ============================================================
    // 2. getProfiles_success
    // ============================================================

    @Test
    void getProfiles_success() {
        String token = generateToken(USER_A);
        SalaryProfileRequest req = buildSalaryProfileRequest(USER_A, "NEW_REGIME");

        // Create profile first
        String createUrl = baseUrl() + "/tax-planning/user/" + USER_A + "/profile";
        var createHeaders = new org.springframework.http.HttpHeaders();
        createHeaders.setBearerAuth(token);
        createHeaders.setContentType(MediaType.APPLICATION_JSON);
        var createEntity = new org.springframework.http.HttpEntity<>(req, createHeaders);
        RestTemplate rt = createRestTemplate();
        var createResp = rt.exchange(URI.create(createUrl), HttpMethod.POST, createEntity, SalaryProfileResponse.class);
        assertEquals(HttpStatus.OK.value(), createResp.getStatusCode().value());
        String profileId = createResp.getBody().getId();

        // Get all profiles
        String getUrl = baseUrl() + "/tax-planning/user/" + USER_A + "/profiles";
        var getHeaders = new org.springframework.http.HttpHeaders();
        getHeaders.setBearerAuth(token);
        var getEntity = new org.springframework.http.HttpEntity<>(getHeaders);
        var getResp = rt.exchange(URI.create(getUrl), HttpMethod.GET, getEntity, String.class);

        assertEquals(HttpStatus.OK.value(), getResp.getStatusCode().value());
        assertNotNull(getResp.getBody());
        assertTrue(getResp.getBody().contains(profileId));
    }

    // ============================================================
    // 3. getProfile_success
    // ============================================================

    @Test
    void getProfile_success() {
        String token = generateToken(USER_A);
        SalaryProfileRequest req = buildSalaryProfileRequest(USER_A, "NEW_REGIME");

        // Create
        String createUrl = baseUrl() + "/tax-planning/user/" + USER_A + "/profile";
        var createHeaders = new org.springframework.http.HttpHeaders();
        createHeaders.setBearerAuth(token);
        createHeaders.setContentType(MediaType.APPLICATION_JSON);
        var createEntity = new org.springframework.http.HttpEntity<>(req, createHeaders);
        RestTemplate rt = createRestTemplate();
        var createResp = rt.exchange(URI.create(createUrl), HttpMethod.POST, createEntity, SalaryProfileResponse.class);
        String profileId = createResp.getBody().getId();

        // Get single profile
        String getUrl = baseUrl() + "/tax-planning/user/" + USER_A + "/profile/" + profileId;
        var getHeaders = new org.springframework.http.HttpHeaders();
        getHeaders.setBearerAuth(token);
        var getEntity = new org.springframework.http.HttpEntity<>(getHeaders);
        var getResp = rt.exchange(URI.create(getUrl), HttpMethod.GET, getEntity, SalaryProfileResponse.class);

        assertEquals(HttpStatus.OK.value(), getResp.getStatusCode().value());
        assertNotNull(getResp.getBody());
        assertEquals(profileId, getResp.getBody().getId());
        assertEquals(USER_A, getResp.getBody().getEmail());
    }

    // ============================================================
    // 4. updateProfile_success
    // ============================================================

    @Test
    void updateProfile_success() {
        String token = generateToken(USER_A);
        SalaryProfileRequest req = buildSalaryProfileRequest(USER_A, "NEW_REGIME");

        // Create
        String createUrl = baseUrl() + "/tax-planning/user/" + USER_A + "/profile";
        var createHeaders = new org.springframework.http.HttpHeaders();
        createHeaders.setBearerAuth(token);
        createHeaders.setContentType(MediaType.APPLICATION_JSON);
        var createEntity = new org.springframework.http.HttpEntity<>(req, createHeaders);
        RestTemplate rt = createRestTemplate();
        var createResp = rt.exchange(URI.create(createUrl), HttpMethod.POST, createEntity, SalaryProfileResponse.class);
        String profileId = createResp.getBody().getId();

        // Update to OLD_REGIME
        SalaryProfileRequest updateReq = buildSalaryProfileRequest(USER_A, "OLD_REGIME");
        String updateUrl = baseUrl() + "/tax-planning/user/" + USER_A + "/profile/" + profileId;
        var updateHeaders = new org.springframework.http.HttpHeaders();
        updateHeaders.setBearerAuth(token);
        updateHeaders.setContentType(MediaType.APPLICATION_JSON);
        var updateEntity = new org.springframework.http.HttpEntity<>(updateReq, updateHeaders);
        var updateResp = rt.exchange(URI.create(updateUrl), HttpMethod.PUT, updateEntity, SalaryProfileResponse.class);

        assertEquals(HttpStatus.OK.value(), updateResp.getStatusCode().value());
        assertNotNull(updateResp.getBody());
        assertEquals(RegimeType.OLD_REGIME, updateResp.getBody().getRegimeType());
    }

    // ============================================================
    // 5. deleteProfile_success
    // ============================================================

    @Test
    void deleteProfile_success() {
        String token = generateToken(USER_A);
        SalaryProfileRequest req = buildSalaryProfileRequest(USER_A, "NEW_REGIME");

        // Create
        String createUrl = baseUrl() + "/tax-planning/user/" + USER_A + "/profile";
        var createHeaders = new org.springframework.http.HttpHeaders();
        createHeaders.setBearerAuth(token);
        createHeaders.setContentType(MediaType.APPLICATION_JSON);
        var createEntity = new org.springframework.http.HttpEntity<>(req, createHeaders);
        RestTemplate rt = createRestTemplate();
        var createResp = rt.exchange(URI.create(createUrl), HttpMethod.POST, createEntity, SalaryProfileResponse.class);
        String profileId = createResp.getBody().getId();

        // Delete
        String deleteUrl = baseUrl() + "/tax-planning/user/" + USER_A + "/profile/" + profileId;
        var deleteHeaders = new org.springframework.http.HttpHeaders();
        deleteHeaders.setBearerAuth(token);
        var deleteEntity = new org.springframework.http.HttpEntity<>(deleteHeaders);
        var deleteResp = rt.exchange(URI.create(deleteUrl), HttpMethod.DELETE, deleteEntity, String.class);

        assertEquals(HttpStatus.NO_CONTENT.value(), deleteResp.getStatusCode().value());

        // Verify GET returns 400 or empty
        var getHeaders2 = new org.springframework.http.HttpHeaders();
        getHeaders2.setBearerAuth(token);
        var getEntity2 = new org.springframework.http.HttpEntity<>(getHeaders2);
        var getUrl = baseUrl() + "/tax-planning/user/" + USER_A + "/profile/" + profileId;
        var getResp = rt.exchange(URI.create(getUrl), HttpMethod.GET, getEntity2, String.class);
        assertTrue(getResp.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()
                || getResp.getStatusCode().value() == HttpStatus.NOT_FOUND.value());
    }

    // ============================================================
    // 6. computeTax_success
    // ============================================================

    @Test
    void computeTax_success() {
        String token = generateToken(USER_A);
        SalaryProfileRequest req = buildSalaryProfileRequest(USER_A, "NEW_REGIME");

        // Create profile
        String createUrl = baseUrl() + "/tax-planning/user/" + USER_A + "/profile";
        var createHeaders = new org.springframework.http.HttpHeaders();
        createHeaders.setBearerAuth(token);
        createHeaders.setContentType(MediaType.APPLICATION_JSON);
        var createEntity = new org.springframework.http.HttpEntity<>(req, createHeaders);
        RestTemplate rt = createRestTemplate();
        var createResp = rt.exchange(URI.create(createUrl), HttpMethod.POST, createEntity, SalaryProfileResponse.class);
        String profileId = createResp.getBody().getId();

        // Compute tax
        String computeUrl = baseUrl() + "/tax-planning/user/" + USER_A + "/profile/" + profileId + "/compute";
        var computeHeaders = new org.springframework.http.HttpHeaders();
        computeHeaders.setBearerAuth(token);
        var computeEntity = new org.springframework.http.HttpEntity<>(computeHeaders);
        var computeResp = rt.exchange(URI.create(computeUrl), HttpMethod.POST, computeEntity, Map.class);

        assertEquals(HttpStatus.OK.value(), computeResp.getStatusCode().value());
        assertNotNull(computeResp.getBody());
        assertNotNull(computeResp.getBody().get("newRegimeResult"));
        assertNotNull(computeResp.getBody().get("oldRegimeResult"));
        assertNotNull(computeResp.getBody().get("recommendedRegime"));
        String recommendedRegime = (String) computeResp.getBody().get("recommendedRegime");
        assertTrue(recommendedRegime.equals("NEW_REGIME") || recommendedRegime.equals("OLD_REGIME"),
                "recommendedRegime should be NEW_REGIME or OLD_REGIME but was: " + recommendedRegime);
    }

    // ============================================================
    // 7. restructure_success
    // ============================================================

    @Test
    void restructure_success() {
        String token = generateToken(USER_A);

        // Build request with special allowance > 0
        SalaryProfileRequest req = buildSalaryProfileRequest(USER_A, "NEW_REGIME");
        req.setComponents(List.of(
                component("BASIC", 600000),
                component("HRA", 240000),
                component("SPECIAL_ALLOWANCE", 360000),
                component("NPS_EMPLOYER", 70000),
                component("EMPLOYEE_PF", 72000)
        ));

        // Create profile
        String createUrl = baseUrl() + "/tax-planning/user/" + USER_A + "/profile";
        var createHeaders = new org.springframework.http.HttpHeaders();
        createHeaders.setBearerAuth(token);
        createHeaders.setContentType(MediaType.APPLICATION_JSON);
        var createEntity = new org.springframework.http.HttpEntity<>(req, createHeaders);
        RestTemplate rt = createRestTemplate();
        var createResp = rt.exchange(URI.create(createUrl), HttpMethod.POST, createEntity, SalaryProfileResponse.class);
        String profileId = createResp.getBody().getId();

        // Restructure
        String restructureUrl = baseUrl() + "/tax-planning/user/" + USER_A + "/profile/" + profileId + "/restructure";
        var restructureHeaders = new org.springframework.http.HttpHeaders();
        restructureHeaders.setBearerAuth(token);
        var restructureEntity = new org.springframework.http.HttpEntity<>(restructureHeaders);
        var restructureResp = rt.exchange(URI.create(restructureUrl), HttpMethod.POST, restructureEntity, Map.class);

        assertEquals(HttpStatus.OK.value(), restructureResp.getStatusCode().value());
        assertNotNull(restructureResp.getBody());
        assertNotNull(restructureResp.getBody().get("recommendations"));
        assertTrue(restructureResp.getBody().get("recommendations") instanceof List);
        List<?> recommendations = (List<?>) restructureResp.getBody().get("recommendations");
        assertNotNull(restructureResp.getBody().get("totalOptimizedSaving"));
        assertTrue(restructureResp.getBody().get("totalOptimizedSaving") instanceof Number);
        assertNotNull(restructureResp.getBody().get("restructuredProfile"));
    }

    // ============================================================
    // 8. generateTaxReport_success
    // ============================================================

    @Test
    void generateTaxReport_success() {
        String token = generateToken(USER_A);
        SalaryProfileRequest req = buildSalaryProfileRequest(USER_A, "NEW_REGIME");

        // Create profile
        String createUrl = baseUrl() + "/tax-planning/user/" + USER_A + "/profile";
        var createHeaders = new org.springframework.http.HttpHeaders();
        createHeaders.setBearerAuth(token);
        createHeaders.setContentType(MediaType.APPLICATION_JSON);
        var createEntity = new org.springframework.http.HttpEntity<>(req, createHeaders);
        RestTemplate rt = createRestTemplate();
        var createResp = rt.exchange(URI.create(createUrl), HttpMethod.POST, createEntity, SalaryProfileResponse.class);
        String profileId = createResp.getBody().getId();

        // Generate tax report
        String reportUrl = baseUrl() + "/tax-planning/user/" + USER_A + "/profile/" + profileId + "/document/tax-report";
        var reportHeaders = new org.springframework.http.HttpHeaders();
        reportHeaders.setBearerAuth(token);
        var reportEntity = new org.springframework.http.HttpEntity<>(reportHeaders);
        var reportResp = rt.exchange(URI.create(reportUrl), HttpMethod.GET, reportEntity, byte[].class);

        assertEquals(HttpStatus.OK.value(), reportResp.getStatusCode().value());
        assertNotNull(reportResp.getBody());
        assertTrue(reportResp.getBody().length > 0);
        assertEquals("application/pdf", reportResp.getHeaders().getContentType().toString());
    }

    // ============================================================
    // 9. getAllowances_public_noAuth
    // ============================================================

    @Test
    void getAllowances_public_noAuth() {
        RestTemplate rt = createRestTemplate();
        String allowancesUrl = baseUrl() + "/tax-planning/public/allowances?taxYear=2025-26&regime=NEW_REGIME";
        var resp = rt.getForEntity(URI.create(allowancesUrl), String.class);

        assertEquals(HttpStatus.OK.value(), resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        // Should contain NPS_EMPLOYER and MEAL_VOUCHER (if seeded)
        assertTrue(resp.getBody().contains("NPS_EMPLOYER") || resp.getBody().contains("HRA"),
                "Expected allowances in response: " + resp.getBody());
    }

    // ============================================================
    // 10. getAllowanceByCode_success
    // ============================================================

    @Test
    void getAllowanceByCode_success() {
        RestTemplate rt = createRestTemplate();
        String allowanceUrl = baseUrl() + "/tax-planning/public/allowances/HRA?taxYear=2025-26";
        var resp = rt.getForEntity(URI.create(allowanceUrl), String.class);

        assertEquals(HttpStatus.OK.value(), resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().contains("HRA"));
    }

    // ============================================================
    // 11. security_userCannotAccessOtherUserProfile
    // ============================================================

    @Test
    void security_userCannotAccessOtherUserProfile() {
        String tokenUserA = generateToken(USER_A);
        SalaryProfileRequest req = buildSalaryProfileRequest(USER_A, "NEW_REGIME");

        // Create profile for USER_A
        String createUrl = baseUrl() + "/tax-planning/user/" + USER_A + "/profile";
        var createHeaders = new org.springframework.http.HttpHeaders();
        createHeaders.setBearerAuth(tokenUserA);
        createHeaders.setContentType(MediaType.APPLICATION_JSON);
        var createEntity = new org.springframework.http.HttpEntity<>(req, createHeaders);
        RestTemplate rt = createRestTemplate();
        var createResp = rt.exchange(URI.create(createUrl), HttpMethod.POST, createEntity, SalaryProfileResponse.class);
        String profileId = createResp.getBody().getId();

        // Try to access USER_A's profile using USER_B's token
        String tokenUserB = generateToken(USER_B);
        String accessUrl = baseUrl() + "/tax-planning/user/" + USER_A + "/profile/" + profileId;
        var accessHeaders = new org.springframework.http.HttpHeaders();
        accessHeaders.setBearerAuth(tokenUserB);
        var accessEntity = new org.springframework.http.HttpEntity<>(accessHeaders);
        var accessResp = rt.exchange(URI.create(accessUrl), HttpMethod.GET, accessEntity, String.class);

        // Should be denied
        assertTrue(accessResp.getStatusCode().value() == HttpStatus.FORBIDDEN.value()
                || accessResp.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value(),
                "Expected 403/401 but got: " + accessResp.getStatusCode().value());
    }

    // ============================================================
    // 12. security_superUserCanAccessAnyProfile
    // ============================================================

    @Test
    void security_superUserCanAccessAnyProfile() {
        String tokenUserA = generateToken(USER_A);
        SalaryProfileRequest req = buildSalaryProfileRequest(USER_A, "NEW_REGIME");

        // Create profile for USER_A
        String createUrl = baseUrl() + "/tax-planning/user/" + USER_A + "/profile";
        var createHeaders = new org.springframework.http.HttpHeaders();
        createHeaders.setBearerAuth(tokenUserA);
        createHeaders.setContentType(MediaType.APPLICATION_JSON);
        var createEntity = new org.springframework.http.HttpEntity<>(req, createHeaders);
        RestTemplate rt = createRestTemplate();
        var createResp = rt.exchange(URI.create(createUrl), HttpMethod.POST, createEntity, SalaryProfileResponse.class);
        String profileId = createResp.getBody().getId();

        // SUPER_USER accesses USER_A's profile
        String superToken = generateToken(USER_A, "SUPER_USER");
        String accessUrl = baseUrl() + "/tax-planning/user/" + USER_A + "/profile/" + profileId;
        var accessHeaders = new org.springframework.http.HttpHeaders();
        accessHeaders.setBearerAuth(superToken);
        var accessEntity = new org.springframework.http.HttpEntity<>(accessHeaders);
        var accessResp = rt.exchange(URI.create(accessUrl), HttpMethod.GET, accessEntity, SalaryProfileResponse.class);

        assertEquals(HttpStatus.OK.value(), accessResp.getStatusCode().value());
        assertNotNull(accessResp.getBody());
        assertEquals(profileId, accessResp.getBody().getId());
    }

    // ============================================================
    // 13. admin_createSlabPolicy_superUserOnly
    // ============================================================

    @Test
    void admin_createSlabPolicy_superUserOnly() {
        String superToken = generateToken(USER_A, "SUPER_USER");

        TaxSlabPolicyEntity policy = new TaxSlabPolicyEntity();
        policy.setTaxYear("2025-26");
        policy.setRegimeType(RegimeType.NEW_REGIME);
        policy.setStandardDeduction(75000L);
        policy.setRebate87aLimit(700000L);
        policy.setRebate87aAmount(25000L);
        policy.setBasicExemptionLimit(300000L);
        policy.setCessPercentage(4.0);

        String url = baseUrl() + "/tax-planning/admin/policies/slab";
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(superToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new org.springframework.http.HttpEntity<>(policy, headers);

        RestTemplate rt = createRestTemplate();
        var response = rt.exchange(URI.create(url), HttpMethod.POST, entity, TaxSlabPolicyEntity.class);

        assertTrue(response.getStatusCode().value() == HttpStatus.OK.value()
                || response.getStatusCode().value() == HttpStatus.CREATED.value(),
                "Expected 200 or 201 but got: " + response.getStatusCode().value());
    }

    // ============================================================
    // 14. admin_createSlabPolicy_regularUserForbidden
    // ============================================================

    @Test
    void admin_createSlabPolicy_regularUserForbidden() {
        String userToken = generateToken(USER_A);

        TaxSlabPolicyEntity policy = new TaxSlabPolicyEntity();
        policy.setTaxYear("2025-26");
        policy.setRegimeType(RegimeType.NEW_REGIME);
        policy.setStandardDeduction(75000L);
        policy.setCessPercentage(4.0);

        String url = baseUrl() + "/tax-planning/admin/policies/slab";
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(userToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new org.springframework.http.HttpEntity<>(policy, headers);

        RestTemplate rt = createRestTemplate();
        var response = rt.exchange(URI.create(url), HttpMethod.POST, entity, String.class);

        assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatusCode().value());
    }
}
