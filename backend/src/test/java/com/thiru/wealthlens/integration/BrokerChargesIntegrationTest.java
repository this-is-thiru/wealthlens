package com.thiru.wealthlens.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.thiru.wealthlens.brokercharges.dto.enums.AmcChargeFrequency;
import com.thiru.wealthlens.brokercharges.dto.enums.BrokerageAggregatorType;
import com.thiru.wealthlens.brokercharges.dto.helper.BrokerageChargesDto;
import com.thiru.wealthlens.brokercharges.dto.request.AssetManagementDetailsRequest;
import com.thiru.wealthlens.brokercharges.dto.request.BrokerChargesRequest;
import com.thiru.wealthlens.brokercharges.entity.BrokerCharges;
import com.thiru.wealthlens.brokercharges.entity.UserBrokerCharges;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import io.restassured.RestAssured;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

public class BrokerChargesIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_EMAIL = "testuser@example.com";

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

    @Test
    void addBrokerCharge_success() {
        String token = generateToken(TEST_EMAIL);
        BrokerChargesRequest request = buildBrokerChargesRequest();

        String url = "http://localhost:" + RestAssured.port + "/broker-charges/add";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new org.springframework.http.HttpEntity<>(request, headers);

        RestTemplate rt = createRestTemplate();
        ResponseEntity<String> response = rt.exchange(URI.create(url), HttpMethod.POST, entity, String.class);

        assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().length() > 0);

        List<BrokerCharges> saved = mongoTemplate.find(
                Query.query(Criteria.where("brokerName").is(BrokerName.ZERODHA)),
                BrokerCharges.class, "broker_charges");
        assertFalse(saved.isEmpty());
        assertEquals(BrokerName.ZERODHA, saved.get(0).getBrokerName());
    }

    @Test
    void getBrokerCharge_success() {
        String token = generateToken(TEST_EMAIL);

        BrokerChargesRequest request = buildBrokerChargesRequest();
        String addUrl = "http://localhost:" + RestAssured.port + "/broker-charges/add";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new org.springframework.http.HttpEntity<>(request, headers);
        RestTemplate rt = createRestTemplate();
        ResponseEntity<String> addResponse = rt.exchange(URI.create(addUrl), HttpMethod.POST, entity, String.class);
        assertEquals(HttpStatus.OK.value(), addResponse.getStatusCode().value());

        BrokerCharges saved = mongoTemplate.findOne(
                Query.query(Criteria.where("brokerName").is(BrokerName.ZERODHA)),
                BrokerCharges.class, "broker_charges");
        assertNotNull(saved);
        assertNotNull(saved.getId());

        String getUrl = "http://localhost:" + RestAssured.port + "/broker-charges/" + saved.getId();
        HttpHeaders getHeaders = new HttpHeaders();
        getHeaders.setBearerAuth(token);
        var getEntity = new org.springframework.http.HttpEntity<>(getHeaders);
        ResponseEntity<BrokerCharges> getResponse = rt.exchange(
                URI.create(getUrl), HttpMethod.GET, getEntity, BrokerCharges.class);

        assertEquals(HttpStatus.OK.value(), getResponse.getStatusCode().value());
        assertNotNull(getResponse.getBody());
        assertEquals(BrokerName.ZERODHA, getResponse.getBody().getBrokerName());
    }

    @Test
    void addAssetManagementDetail_success() {
        String token = generateToken(TEST_EMAIL);
        AssetManagementDetailsRequest request = new AssetManagementDetailsRequest();
        request.setEmail(TEST_EMAIL);
        request.setBrokerName(BrokerName.ZERODHA);
        request.setDematAccountId("DEMAT-" + UUID.randomUUID());
        request.setAccountOpeningCharges(300);
        request.setTaxOnAccountOpeningCharges(54);
        request.setLastAmcChargesDeductedOn(LocalDate.now().minusDays(100));
        request.setAmcChargesFrequency(AmcChargeFrequency.QUARTERLY);

        String url = "http://localhost:" + RestAssured.port + "/broker-charges/user/" + TEST_EMAIL + "/add/asset-management-detail";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new org.springframework.http.HttpEntity<>(request, headers);

        RestTemplate rt = createRestTemplate();
        ResponseEntity<String> response = rt.exchange(URI.create(url), HttpMethod.POST, entity, String.class);

        assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());

        String getUrl = "http://localhost:" + RestAssured.port + "/broker-charges/user/" + TEST_EMAIL + "/asset-management-details";
        HttpHeaders getHeaders = new HttpHeaders();
        getHeaders.setBearerAuth(token);
        var getEntity = new org.springframework.http.HttpEntity<>(getHeaders);
        ResponseEntity<String> getResponse = rt.exchange(URI.create(getUrl), HttpMethod.GET, getEntity, String.class);

        assertEquals(HttpStatus.OK.value(), getResponse.getStatusCode().value());
        assertNotNull(getResponse.getBody());
        assertTrue(getResponse.getBody().contains("ZERODHA"));
    }

    @Test
    void getAssetManagementDetails_success() {
        String token = generateToken(TEST_EMAIL);

        AssetManagementDetailsRequest request = new AssetManagementDetailsRequest();
        request.setEmail(TEST_EMAIL);
        request.setBrokerName(BrokerName.ZERODHA);
        request.setDematAccountId("DEMAT-12345");
        request.setAccountOpeningCharges(500);
        request.setTaxOnAccountOpeningCharges(90);
        request.setLastAmcChargesDeductedOn(LocalDate.now().minusMonths(3));
        request.setAmcChargesFrequency(AmcChargeFrequency.QUARTERLY);

        String addUrl = "http://localhost:" + RestAssured.port + "/broker-charges/user/" + TEST_EMAIL + "/add/asset-management-detail";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new org.springframework.http.HttpEntity<>(request, headers);
        RestTemplate rt = createRestTemplate();
        ResponseEntity<String> addResponse = rt.exchange(URI.create(addUrl), HttpMethod.POST, entity, String.class);
        assertEquals(HttpStatus.OK.value(), addResponse.getStatusCode().value());

        String getUrl = "http://localhost:" + RestAssured.port + "/broker-charges/user/" + TEST_EMAIL + "/asset-management-details";
        HttpHeaders getHeaders = new HttpHeaders();
        getHeaders.setBearerAuth(token);
        var getEntity = new org.springframework.http.HttpEntity<>(getHeaders);
        ResponseEntity<String> getResponse = rt.exchange(URI.create(getUrl), HttpMethod.GET, getEntity, String.class);

        assertEquals(HttpStatus.OK.value(), getResponse.getStatusCode().value());
        assertNotNull(getResponse.getBody());
        assertTrue(getResponse.getBody().startsWith("["));
        assertTrue(getResponse.getBody().contains("DEMAT-12345"));
    }

    @Test
    void imposeAmcCharges_success() {
        String token = generateToken(TEST_EMAIL);

        AssetManagementDetailsRequest request = new AssetManagementDetailsRequest();
        request.setEmail(TEST_EMAIL);
        request.setBrokerName(BrokerName.ZERODHA);
        request.setDematAccountId("DEMAT-AMD-TEST-" + UUID.randomUUID());
        request.setAccountOpeningCharges(300);
        request.setTaxOnAccountOpeningCharges(54);
        request.setLastAmcChargesDeductedOn(LocalDate.now().minusDays(100));
        request.setAmcChargesFrequency(AmcChargeFrequency.QUARTERLY);

        String addDetailUrl = "http://localhost:" + RestAssured.port + "/broker-charges/user/" + TEST_EMAIL + "/add/asset-management-detail";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new org.springframework.http.HttpEntity<>(request, headers);
        RestTemplate rt = createRestTemplate();
        ResponseEntity<String> addResponse = rt.exchange(URI.create(addDetailUrl), HttpMethod.POST, entity, String.class);
        assertEquals(HttpStatus.OK.value(), addResponse.getStatusCode().value());

        BrokerCharges bc = new BrokerCharges();
        bc.setBrokerName(BrokerName.ZERODHA);
        bc.setStartDate(LocalDate.now().minusYears(1));
        bc.setEndDate(LocalDate.now().plusYears(1));
        bc.setStatus(EntityStatus.ACTIVE);
        bc.setBrokerageCharges(new com.thiru.wealthlens.portfolio.entity.model.BrokerageCharges(0, 20, BrokerageAggregatorType.MIN, 0, 20));
        bc.setStt(0.1);
        bc.setSebiCharges(0.0001);
        bc.setStampDuty(0.015);
        bc.setDpChargesPerScrip(13.5);
        bc.setAmcChargesAnnually(100);
        bc.setAmcChargeFrequency(AmcChargeFrequency.QUARTERLY);
        bc.setGstApplicableDescription("18%-brokerage,18%-dp_charges,18%-stt,18%-amc_charges");
        mongoTemplate.save(bc, "broker_charges");

        String imposeUrl = "http://localhost:" + RestAssured.port + "/broker-charges/amc/impose";
        HttpHeaders imposeHeaders = new HttpHeaders();
        imposeHeaders.setBearerAuth(token);
        imposeHeaders.setContentType(MediaType.APPLICATION_JSON);
        var imposeEntity = new org.springframework.http.HttpEntity<>(imposeHeaders);
        ResponseEntity<String> imposeResponse = rt.exchange(URI.create(imposeUrl), HttpMethod.POST, imposeEntity, String.class);

        assertEquals(HttpStatus.OK.value(), imposeResponse.getStatusCode().value());

        List<UserBrokerCharges> charges = mongoTemplate.find(
                Query.query(Criteria.where("email").is(TEST_EMAIL)),
                UserBrokerCharges.class, "user_broker_charges");
        assertFalse(charges.isEmpty(), "Expected AMC charges to be imposed for user");
    }

    private BrokerChargesRequest buildBrokerChargesRequest() {
        BrokerChargesRequest request = new BrokerChargesRequest();
        request.setBrokerName(BrokerName.ZERODHA);
        request.setStartDate(LocalDate.now().minusDays(1));
        request.setStatus(EntityStatus.ACTIVE);
        request.setAccountOpeningCharges(0);
        request.setAmcChargesAnnually(100);
        request.setAmcChargesFrequency(AmcChargeFrequency.QUARTERLY);

        BrokerageChargesDto brokerage = new BrokerageChargesDto();
        brokerage.setBrokerage(0);
        brokerage.setBrokerageCharges(20);
        brokerage.setBrokerageAggregator(BrokerageAggregatorType.MIN);
        brokerage.setMinimumBrokerage(0);
        brokerage.setMaximumBrokerage(20);
        request.setBrokerageCharges(brokerage);

        request.setDpChargesPerScrip(13.5);
        request.setStt(0.1);
        request.setSebiCharges(0.0001);
        request.setStampDuty(0.015);
        request.setGstApplicableDescription("18%-brokerage,18%-dp_charges,18%-stt,18%-amc_charges");
        return request;
    }
}
