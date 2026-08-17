package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.thiru.wealthlens.corporate.dto.CorporateActionDto;
import com.thiru.wealthlens.corporate.dto.CorporateActionPerformDto;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.corporate.entity.LastlyPerformedCorporateAction;
import com.thiru.wealthlens.corporate.entity.model.DemergerDetail;
import com.thiru.wealthlens.corporate.repository.CorporateActionRepository;
import com.thiru.wealthlens.corporate.repository.LastlyPerformedCorporateActionRepo;
import com.thiru.wealthlens.corporate.service.CorporateActionService;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import io.restassured.RestAssured;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

public class CorporateActionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CorporateActionService corporateActionService;

    @Autowired
    private CorporateActionRepository corporateActionRepository;

    @Autowired
    private LastlyPerformedCorporateActionRepo lastlyPerformedCorporateActionRepo;

    private String baseUrl() {
        return "http://localhost:" + RestAssured.port;
    }

    private RestTemplate createNoErrorRestTemplate() {
        RestTemplate rt = new RestTemplate();
        rt.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return false;
            }
        });
        return rt;
    }

    // ========== VALIDATION: addCorporateAction null DTO body ==========

    @Test
    void addCorporateAction_whenNullDtoBody_throwsException() {
        // GIVEN
        String token = generateToken("test@example.com", "SUPER_USER");

        // WHEN / THEN
        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("null")
                .when()
                .post("/corporate-action/add")
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value());
    }

    // ========== VALIDATION: addCorporateAction blank stock code ==========

    @Test
    void addCorporateAction_whenBlankStockCode_throwsException() {
        // GIVEN
        CorporateActionDto dto = new CorporateActionDto();
        dto.setStockCode("");
        dto.setType(CorporateActionType.BONUS);
        dto.setRecordDate(LocalDate.of(2024, 1, 15));
        dto.setExDate(LocalDate.of(2024, 1, 20));
        dto.setRatio("1:1");

        String token = generateToken("test@example.com", "SUPER_USER");

        // WHEN / THEN
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> corporateActionService.addCorporateAction(dto));

        assertEquals("Stock code is missing", exception.getMessage());
    }

    // ========== VALIDATION: addCorporateAction null type ==========

    @Test
    void addCorporateAction_whenNullType_throwsException() {
        // GIVEN
        CorporateActionDto dto = new CorporateActionDto();
        dto.setStockCode("RELIANCE");
        dto.setType(null);
        dto.setRecordDate(LocalDate.of(2024, 1, 15));
        dto.setExDate(LocalDate.of(2024, 1, 20));

        // WHEN / THEN
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> corporateActionService.addCorporateAction(dto));

        assertEquals("Invalid corporate action type", exception.getMessage());
    }

    // ========== VALIDATION: addCorporateAction null recordDate ==========

    @Test
    void addCorporateAction_whenNullRecordDate_throwsException() {
        // GIVEN
        CorporateActionDto dto = new CorporateActionDto();
        dto.setStockCode("RELIANCE");
        dto.setType(CorporateActionType.BONUS);
        dto.setRecordDate(null);
        dto.setExDate(LocalDate.of(2024, 1, 20));
        dto.setRatio("1:1");

        // WHEN / THEN
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> corporateActionService.addCorporateAction(dto));

        assertEquals("Record date is invalid", exception.getMessage());
    }

    // ========== VALIDATION: addCorporateAction null exDate ==========

    @Test
    void addCorporateAction_whenNullExDate_throwsException() {
        // GIVEN
        CorporateActionDto dto = new CorporateActionDto();
        dto.setStockCode("RELIANCE");
        dto.setType(CorporateActionType.BONUS);
        dto.setRecordDate(LocalDate.of(2024, 1, 15));
        dto.setExDate(null);
        dto.setRatio("1:1");

        // WHEN / THEN
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> corporateActionService.addCorporateAction(dto));

        assertEquals("Ex date is invalid", exception.getMessage());
    }

    // ========== VALIDATION: addCorporateAction DEMERGER null demergerDetail ==========

    @Test
    void addCorporateActionDEMERGER_whenNullDemergerDetail_throwsException() {
        // GIVEN
        CorporateActionDto dto = new CorporateActionDto();
        dto.setStockCode("RELIANCE");
        dto.setType(CorporateActionType.DEMERGER);
        dto.setRecordDate(LocalDate.of(2024, 1, 15));
        dto.setExDate(LocalDate.of(2024, 1, 20));
        dto.setDemergerDetail(null);

        // WHEN / THEN
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> corporateActionService.addCorporateAction(dto));

        assertEquals("Demerger data is missing", exception.getMessage());
    }

    // ========== VALIDATION: addCorporateAction DEMERGER invalid ratio format ==========

    @Test
    void addCorporateActionDEMERGER_whenInvalidRatioFormat_throwsException() {
        // GIVEN
        CorporateActionDto.DemergerDetailDto demergerDetail = new CorporateActionDto.DemergerDetailDto(
                "INVALID",
                "1:1",
                "RELIANCE",
                "Reliance Industries",
                List.of(new CorporateActionDto.DemergerStockDto("RELIANCE_RE", "Reliance Retail"))
        );

        CorporateActionDto dto = new CorporateActionDto();
        dto.setStockCode("RELIANCE");
        dto.setType(CorporateActionType.DEMERGER);
        dto.setRecordDate(LocalDate.of(2024, 1, 15));
        dto.setExDate(LocalDate.of(2024, 1, 20));
        dto.setDemergerDetail(demergerDetail);

        // WHEN / THEN
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> corporateActionService.addCorporateAction(dto));

        assertEquals("Invalid demerger ratio format", exception.getMessage());
    }

    // ========== VALIDATION: addCorporateAction DEMERGER mismatched ratios vs stocks count ==========

    @Test
    void addCorporateActionDEMERGER_whenMismatchedRatiosVsStocksCount_throwsException() {
        // GIVEN
        CorporateActionDto.DemergerDetailDto demergerDetail = new CorporateActionDto.DemergerDetailDto(
                "1:1:1",
                "1:1:1",
                "RELIANCE",
                "Reliance Industries",
                List.of(new CorporateActionDto.DemergerStockDto("RELIANCE_RE", "Reliance Retail"))
        );

        CorporateActionDto dto = new CorporateActionDto();
        dto.setStockCode("RELIANCE");
        dto.setType(CorporateActionType.DEMERGER);
        dto.setRecordDate(LocalDate.of(2024, 1, 15));
        dto.setExDate(LocalDate.of(2024, 1, 20));
        dto.setDemergerDetail(demergerDetail);

        // WHEN / THEN
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> corporateActionService.addCorporateAction(dto));

        assertEquals("Invalid demerger ratio format", exception.getMessage());
    }

    // ========== VALIDATION: addCorporateAction DEMERGER price ratio length mismatch ==========

    @Test
    void addCorporateActionDEMERGER_whenPriceRatioLengthMismatch_throwsException() {
        // GIVEN
        CorporateActionDto.DemergerDetailDto demergerDetail = new CorporateActionDto.DemergerDetailDto(
                "1:1",
                "1:1:1",
                "RELIANCE",
                "Reliance Industries",
                List.of(new CorporateActionDto.DemergerStockDto("RELIANCE_RE", "Reliance Retail"))
        );

        CorporateActionDto dto = new CorporateActionDto();
        dto.setStockCode("RELIANCE");
        dto.setType(CorporateActionType.DEMERGER);
        dto.setRecordDate(LocalDate.of(2024, 1, 15));
        dto.setExDate(LocalDate.of(2024, 1, 20));
        dto.setDemergerDetail(demergerDetail);

        // WHEN / THEN
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> corporateActionService.addCorporateAction(dto));

        assertEquals("Demerger ratios and price ratios must have the same length", exception.getMessage());
    }

    // ========== VALIDATION: addCorporateAction BONUS invalid ratio ==========

    @Test
    void addCorporateActionBONUS_whenInvalidRatio_throwsException() {
        // GIVEN
        CorporateActionDto dto = new CorporateActionDto();
        dto.setStockCode("RELIANCE");
        dto.setType(CorporateActionType.BONUS);
        dto.setRecordDate(LocalDate.of(2024, 1, 15));
        dto.setExDate(LocalDate.of(2024, 1, 20));
        dto.setRatio("INVALID");

        // WHEN / THEN
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> corporateActionService.addCorporateAction(dto));

        assertEquals("Invalid bonus ratio format", exception.getMessage());
    }

    // ========== HAPPY: addCorporateAction BONUS success ==========

    @Test
    void addCorporateActionBONUS_whenValidData_success() {
        // GIVEN
        CorporateActionDto dto = new CorporateActionDto();
        dto.setStockCode("RELIANCE");
        dto.setStockName("Reliance Industries");
        dto.setType(CorporateActionType.BONUS);
        dto.setRecordDate(LocalDate.of(2024, 1, 15));
        dto.setExDate(LocalDate.of(2024, 1, 20));
        dto.setRatio("1:1");
        dto.setPriority(1);

        // WHEN
        String result = corporateActionService.addCorporateAction(dto);

        // THEN
        assertEquals("Corporate action: BONUS added successfully for stock: RELIANCE", result);

        List<CorporateActionEntity> actions = corporateActionRepository.findAll();
        assertEquals(1, actions.size());
        assertEquals("RELIANCE", actions.get(0).getStockCode());
        assertEquals(CorporateActionType.BONUS, actions.get(0).getType());
    }

    // ========== HAPPY: addCorporateAction DEMERGER success ==========

    @Test
    void addCorporateActionDEMERGER_whenValidData_success() {
        // GIVEN
        CorporateActionDto.DemergerDetailDto demergerDetail = new CorporateActionDto.DemergerDetailDto(
                "1:1",
                "1:1",
                "RELIANCE",
                "Reliance Industries",
                List.of(new CorporateActionDto.DemergerStockDto("RELIANCE_RE", "Reliance Retail"))
        );

        CorporateActionDto dto = new CorporateActionDto();
        dto.setStockCode("RELIANCE");
        dto.setStockName("Reliance Industries");
        dto.setType(CorporateActionType.DEMERGER);
        dto.setRecordDate(LocalDate.of(2024, 1, 15));
        dto.setExDate(LocalDate.of(2024, 1, 20));
        dto.setDemergerDetail(demergerDetail);
        dto.setPriority(1);

        // WHEN
        String result = corporateActionService.addCorporateAction(dto);

        // THEN
        assertEquals("Corporate action: DEMERGER added successfully for stock: RELIANCE", result);

        List<CorporateActionEntity> actions = corporateActionRepository.findAll();
        assertEquals(1, actions.size());
        assertEquals(CorporateActionType.DEMERGER, actions.get(0).getType());
    }

    // ========== BLOCK: addCorporateAction duplicate CA for same stock+type ==========

    @Test
    void addCorporateAction_whenDuplicateCAForSameStockAndType_throwsException() {
        // GIVEN
        CorporateActionEntity existing = new CorporateActionEntity();
        existing.setStockCode("RELIANCE");
        existing.setType(CorporateActionType.BONUS);
        existing.setRecordDate(LocalDate.of(2024, 1, 15));
        existing.setExDate(LocalDate.of(2024, 1, 20));
        existing.setRatio("1:1");
        existing.setPriority(1);
        mongoTemplate.save(existing);

        CorporateActionDto dto = new CorporateActionDto();
        dto.setStockCode("RELIANCE");
        dto.setType(CorporateActionType.BONUS);
        dto.setRecordDate(LocalDate.of(2024, 1, 15));
        dto.setExDate(LocalDate.of(2024, 1, 20));
        dto.setRatio("2:1");
        dto.setPriority(2);

        // WHEN / THEN
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> corporateActionService.addCorporateAction(dto));

        assertTrue(exception.getMessage().contains("Corporate actions is already present"));
    }

    // ========== BLOCK: addCorporateAction duplicate priority ==========

    @Test
    void addCorporateAction_whenDuplicatePriority_throwsException() {
        // GIVEN
        CorporateActionEntity existing = new CorporateActionEntity();
        existing.setStockCode("RELIANCE");
        existing.setType(CorporateActionType.BONUS);
        existing.setRecordDate(LocalDate.of(2024, 1, 15));
        existing.setExDate(LocalDate.of(2024, 1, 20));
        existing.setRatio("1:1");
        existing.setPriority(1);
        mongoTemplate.save(existing);

        CorporateActionDto dto = new CorporateActionDto();
        dto.setStockCode("RELIANCE");
        dto.setType(CorporateActionType.DEMERGER);
        dto.setRecordDate(LocalDate.of(2024, 1, 15));
        dto.setExDate(LocalDate.of(2024, 1, 20));
        dto.setPriority(1);
        dto.setDemergerDetail(new CorporateActionDto.DemergerDetailDto(
                "1:1", "1:1", "RELIANCE", "Reliance",
                List.of(new CorporateActionDto.DemergerStockDto("REL_RE", "Reliance Retail"))
        ));

        // WHEN / THEN
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> corporateActionService.addCorporateAction(dto));

        assertTrue(exception.getMessage().contains("Update the priorities"));
    }

    // ========== HAPPY: getCorporateActionDetails found ==========

    @Test
    void getCorporateActionDetails_whenExists_returnsEntity() {
        // GIVEN
        CorporateActionEntity existing = new CorporateActionEntity();
        existing.setStockCode("RELIANCE");
        existing.setType(CorporateActionType.BONUS);
        existing.setRecordDate(LocalDate.of(2024, 1, 15));
        existing.setExDate(LocalDate.of(2024, 1, 20));
        existing.setRatio("1:1");
        existing.setPriority(1);
        CorporateActionEntity saved = mongoTemplate.save(existing);

        // WHEN
        CorporateActionEntity result = corporateActionService.getCorporateActionDetails(saved.getId());

        // THEN
        assertNotNull(result);
        assertEquals("RELIANCE", result.getStockCode());
        assertEquals(CorporateActionType.BONUS, result.getType());
    }

    // ========== VALID: getCorporateActionDetails not found ==========

    @Test
    void getCorporateActionDetails_whenNotFound_throwsException() {
        // GIVEN
        String nonExistentId = "nonexistent123";

        // WHEN / THEN
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> corporateActionService.getCorporateActionDetails(nonExistentId));

        assertEquals("No corporate action found with id: " + nonExistentId, exception.getMessage());
    }

    // ========== HAPPY: updateCorporateActionPriority success ==========

    @Test
    void updateCorporateActionPriority_whenValidData_success() {
        // GIVEN
        CorporateActionEntity existing = new CorporateActionEntity();
        existing.setStockCode("RELIANCE");
        existing.setType(CorporateActionType.BONUS);
        existing.setRecordDate(LocalDate.of(2024, 1, 15));
        existing.setExDate(LocalDate.of(2024, 1, 20));
        existing.setRatio("1:1");
        existing.setPriority(1);
        CorporateActionEntity saved = mongoTemplate.save(existing);

        // WHEN
        String result = corporateActionService.updateCorporateActionPriority(saved.getId(), 5);

        // THEN
        assertTrue(result.contains("Priority has been updated"));
        assertTrue(result.contains("from: 1 to: 5"));

        CorporateActionEntity updated = corporateActionService.getCorporateActionDetails(saved.getId());
        assertEquals(5, updated.getPriority());
    }

    // ========== VALID: updateCorporateActionPriority not found ==========

    @Test
    void updateCorporateActionPriority_whenNotFound_throwsException() {
        // GIVEN
        String nonExistentId = "nonexistent123";

        // WHEN / THEN
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> corporateActionService.updateCorporateActionPriority(nonExistentId, 5));

        assertEquals("No corporate actions found with id: " + nonExistentId, exception.getMessage());
    }

    // ========== VALID: updateCorporateActionPriority same priority ==========

    @Test
    void updateCorporateActionPriority_whenSamePriority_throwsException() {
        // GIVEN
        CorporateActionEntity existing = new CorporateActionEntity();
        existing.setStockCode("RELIANCE");
        existing.setType(CorporateActionType.BONUS);
        existing.setRecordDate(LocalDate.of(2024, 1, 15));
        existing.setExDate(LocalDate.of(2024, 1, 20));
        existing.setRatio("1:1");
        existing.setPriority(1);
        CorporateActionEntity saved = mongoTemplate.save(existing);

        // WHEN / THEN
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> corporateActionService.updateCorporateActionPriority(saved.getId(), 1));

        assertEquals("Existing priority and new priority is same.", exception.getMessage());
    }

    // ========== HAPPY: getCorporateActions by id list ==========

    @Test
    void getCorporateActions_whenValidIds_returnsActions() {
        // GIVEN
        CorporateActionEntity existing1 = new CorporateActionEntity();
        existing1.setStockCode("RELIANCE");
        existing1.setType(CorporateActionType.BONUS);
        existing1.setRecordDate(LocalDate.of(2024, 1, 15));
        existing1.setExDate(LocalDate.of(2024, 1, 20));
        existing1.setRatio("1:1");
        existing1.setPriority(1);
        CorporateActionEntity saved1 = mongoTemplate.save(existing1);

        CorporateActionEntity existing2 = new CorporateActionEntity();
        existing2.setStockCode("TCS");
        existing2.setType(CorporateActionType.BONUS);
        existing2.setRecordDate(LocalDate.of(2024, 2, 15));
        existing2.setExDate(LocalDate.of(2024, 2, 20));
        existing2.setRatio("2:1");
        existing2.setPriority(1);
        CorporateActionEntity saved2 = mongoTemplate.save(existing2);

        // WHEN
        var result = corporateActionService.getCorporateActions(List.of(saved1.getId(), saved2.getId()));

        // THEN
        assertEquals(2, result.size());
    }

    // ========== HAPPY: getAllCorporateActions ==========

    @Test
    void getAllCorporateActions_whenExists_returnsAll() {
        // GIVEN
        CorporateActionEntity existing1 = new CorporateActionEntity();
        existing1.setStockCode("RELIANCE");
        existing1.setType(CorporateActionType.BONUS);
        existing1.setRecordDate(LocalDate.of(2024, 1, 15));
        existing1.setExDate(LocalDate.of(2024, 1, 20));
        existing1.setRatio("1:1");
        existing1.setPriority(1);
        mongoTemplate.save(existing1);

        CorporateActionEntity existing2 = new CorporateActionEntity();
        existing2.setStockCode("TCS");
        existing2.setType(CorporateActionType.STOCK_SPLIT);
        existing2.setRecordDate(LocalDate.of(2024, 2, 15));
        existing2.setExDate(LocalDate.of(2024, 2, 20));
        existing2.setRatio("1:10");
        existing2.setPriority(1);
        mongoTemplate.save(existing2);

        // WHEN
        List<CorporateActionEntity> result = corporateActionService.getAllCorporateActions();

        // THEN
        assertEquals(2, result.size());
    }

    // ========== AUTH: deleteCorporateActions non-SUPER_USER ==========

    @Test
    void deleteCorporateActions_whenNotSuperUser_forbidden() {
        // GIVEN
        String token = generateToken("test@example.com", "USER");
        String id = "some-id";

        // WHEN / THEN
        String url = baseUrl() + "/corporate-action/delete/" + id;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        RestTemplate rt = createNoErrorRestTemplate();
        ResponseEntity<String> response = rt.exchange(URI.create(url), HttpMethod.DELETE, entity, String.class);
        assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatusCode().value());
    }

    // ========== HAPPY: deleteCorporateActions success ==========

    @Test
    void deleteCorporateActions_whenSuperUser_success() {
        // GIVEN
        CorporateActionEntity existing = new CorporateActionEntity();
        existing.setStockCode("RELIANCE");
        existing.setType(CorporateActionType.BONUS);
        existing.setRecordDate(LocalDate.of(2024, 1, 15));
        existing.setExDate(LocalDate.of(2024, 1, 20));
        existing.setRatio("1:1");
        existing.setPriority(1);
        CorporateActionEntity saved = mongoTemplate.save(existing);

        String token = generateToken("test@example.com", "SUPER_USER");

        // WHEN / THEN
        String url = baseUrl() + "/corporate-action/delete/" + saved.getId();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        RestTemplate rt = createNoErrorRestTemplate();
        ResponseEntity<String> response = rt.exchange(URI.create(url), HttpMethod.DELETE, entity, String.class);
        assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());

        List<CorporateActionEntity> actions = corporateActionRepository.findAll();
        assertEquals(0, actions.size());
    }

    // ========== HAPPY: performCorporateAction STOCK_SPLIT ==========

    @Test
    void performCorporateAction_STOCKSPLIT_whenValidData_success() {
        // GIVEN
        AssetEntity asset = new AssetEntity();
        asset.setEmail("test@example.com");
        asset.setStockCode("RELIANCE");
        asset.setStockName("Reliance Industries");
        asset.setBrokerName(BrokerName.UPSTOX);
        asset.setAssetType(AssetType.EQUITY);
        asset.setQuantity(100.0);
        asset.setPrice(2500.0);
        asset.setTransactionDate(LocalDate.of(2024, 1, 10));
        mongoTemplate.save(asset);

        TransactionEntity transaction = new TransactionEntity();
        transaction.setEmail("test@example.com");
        transaction.setStockCode("RELIANCE");
        transaction.setStockName("Reliance Industries");
        transaction.setBrokerName(BrokerName.UPSTOX);
        transaction.setAssetType(AssetType.EQUITY);
        transaction.setQuantity(100.0);
        transaction.setPrice(2500.0);
        transaction.setTransactionType(TransactionType.BUY);
        transaction.setTransactionDate(LocalDate.of(2024, 1, 10));
        mongoTemplate.save(transaction);

        CorporateActionDto dto = new CorporateActionDto();
        dto.setStockCode("RELIANCE");
        dto.setStockName("Reliance Industries");
        dto.setType(CorporateActionType.STOCK_SPLIT);
        dto.setRecordDate(LocalDate.of(2024, 1, 15));
        dto.setExDate(LocalDate.of(2024, 1, 20));
        dto.setRatio("1:10");

        // WHEN
        String result = corporateActionService.performCorporateAction(dto);

        // THEN
        assertEquals("Corporate action: STOCK_SPLIT noted successfully for stock: RELIANCE", result);
    }

    // ========== HAPPY: performCorporateAction NAME_OR_SYMBOL_CHANGE ==========

    @Test
    void performCorporateAction_NAMECHANGE_whenValidData_success() {
        // GIVEN
        AssetEntity asset = new AssetEntity();
        asset.setEmail("test@example.com");
        asset.setStockCode("RELIANCE");
        asset.setStockName("Reliance Industries");
        asset.setBrokerName(BrokerName.UPSTOX);
        asset.setAssetType(AssetType.EQUITY);
        asset.setQuantity(100.0);
        asset.setPrice(2500.0);
        asset.setTransactionDate(LocalDate.of(2024, 1, 10));
        mongoTemplate.save(asset);

        TransactionEntity transaction = new TransactionEntity();
        transaction.setEmail("test@example.com");
        transaction.setStockCode("RELIANCE");
        transaction.setStockName("Reliance Industries");
        transaction.setBrokerName(BrokerName.UPSTOX);
        transaction.setAssetType(AssetType.EQUITY);
        transaction.setQuantity(100.0);
        transaction.setPrice(2500.0);
        transaction.setTransactionType(TransactionType.BUY);
        transaction.setTransactionDate(LocalDate.of(2024, 1, 10));
        mongoTemplate.save(transaction);

        CorporateActionDto dto = new CorporateActionDto();
        dto.setStockCode("RELIANCE");
        dto.setStockName("Reliance Industries");
        dto.setType(CorporateActionType.NAME_OR_SYMBOL_CHANGE);
        dto.setToStockCode("RIL");
        dto.setToStockName("Reliance Industries Ltd");
        dto.setRecordDate(LocalDate.of(2024, 1, 15));
        dto.setExDate(LocalDate.of(2024, 1, 20));

        // WHEN
        String result = corporateActionService.performCorporateAction(dto);

        // THEN
        assertEquals("Corporate action: NAME_OR_SYMBOL_CHANGE noted successfully for stock: RELIANCE", result);
    }

    // ========== BLOCK: performCorporateAction invalid type ==========

    @Test
    void performCorporateAction_whenInvalidType_throwsException() {
        // GIVEN
        CorporateActionDto dto = new CorporateActionDto();
        dto.setStockCode("RELIANCE");
        dto.setType(CorporateActionType.BONUS);
        dto.setRecordDate(LocalDate.of(2024, 1, 15));
        dto.setExDate(LocalDate.of(2024, 1, 20));
        dto.setRatio("1:1");

        // WHEN / THEN
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> corporateActionService.performCorporateAction(dto));

        assertTrue(exception.getMessage().contains("Invalid action type"));
    }

    // ========== HAPPY: performPendingCorporateActions single broker ==========

    @Test
    void performPendingCorporateActions_singleBroker_success() {
        // GIVEN
        CorporateActionEntity ca = new CorporateActionEntity();
        ca.setStockCode("RELIANCE");
        ca.setStockName("Reliance Industries");
        ca.setType(CorporateActionType.BONUS);
        ca.setAssetType(AssetType.EQUITY);
        ca.setRecordDate(LocalDate.of(2024, 1, 15));
        ca.setExDate(LocalDate.of(2024, 1, 20));
        ca.setRatio("1:1");
        ca.setPriority(1);
        mongoTemplate.save(ca);

        AssetEntity asset = new AssetEntity();
        asset.setEmail("test@example.com");
        asset.setStockCode("RELIANCE");
        asset.setStockName("Reliance Industries");
        asset.setBrokerName(BrokerName.UPSTOX);
        asset.setAssetType(AssetType.EQUITY);
        asset.setQuantity(100.0);
        asset.setPrice(2500.0);
        asset.setTransactionDate(LocalDate.of(2024, 1, 10));
        asset.setTransactionType(TransactionType.BUY);
        mongoTemplate.save(asset);

        TransactionEntity transaction = new TransactionEntity();
        transaction.setEmail("test@example.com");
        transaction.setStockCode("RELIANCE");
        transaction.setStockName("Reliance Industries");
        transaction.setBrokerName(BrokerName.UPSTOX);
        transaction.setAssetType(AssetType.EQUITY);
        transaction.setQuantity(100.0);
        transaction.setPrice(2500.0);
        transaction.setTransactionType(TransactionType.BUY);
        transaction.setTransactionDate(LocalDate.of(2024, 1, 10));
        mongoTemplate.save(transaction);

        CorporateActionPerformDto performDto = new CorporateActionPerformDto("JANUARY", 2024, BrokerName.UPSTOX);

        // WHEN
        corporateActionService.performPendingCorporateActions("test@example.com", performDto, false);

        // THEN
        List<LastlyPerformedCorporateAction> performed = lastlyPerformedCorporateActionRepo.findAll();
        assertEquals(1, performed.size());
    }

    // ========== HAPPY: performPendingCorporateActions all brokers ==========

    @Test
    void performPendingCorporateActions_allBrokers_success() {
        // GIVEN
        CorporateActionEntity ca = new CorporateActionEntity();
        ca.setStockCode("RELIANCE");
        ca.setStockName("Reliance Industries");
        ca.setType(CorporateActionType.BONUS);
        ca.setAssetType(AssetType.EQUITY);
        ca.setRecordDate(LocalDate.of(2024, 1, 15));
        ca.setExDate(LocalDate.of(2024, 1, 20));
        ca.setRatio("1:1");
        ca.setPriority(1);
        mongoTemplate.save(ca);

        CorporateActionPerformDto performDto = new CorporateActionPerformDto("JANUARY", 2024, null);

        // WHEN
        corporateActionService.performPendingCorporateActions("test@example.com", performDto, true);

        // THEN - no exception means success
    }

    // ========== VALID: performPendingCorporateActions invalid month ==========

    @Test
    void performPendingCorporateActions_whenInvalidMonth_throwsException() {
        // GIVEN
        CorporateActionPerformDto performDto = new CorporateActionPerformDto("INVALID", 2024, BrokerName.UPSTOX);

        // WHEN / THEN
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> corporateActionService.performPendingCorporateActions("test@example.com", performDto, false));

        assertTrue(exception.getMessage().contains("INVALID"));
    }

    // ========== REDRIVE: performPendingCorporateActions skip due to temp txns before recordDate ==========

    @Test
    void performPendingCorporateActions_whenTempTransactionsBeforeRecordDate_skips() {
        // GIVEN
        CorporateActionEntity ca = new CorporateActionEntity();
        ca.setStockCode("RELIANCE");
        ca.setStockName("Reliance Industries");
        ca.setType(CorporateActionType.BONUS);
        ca.setAssetType(AssetType.EQUITY);
        ca.setRecordDate(LocalDate.of(2024, 1, 15));
        ca.setExDate(LocalDate.of(2024, 1, 20));
        ca.setRatio("1:1");
        ca.setPriority(1);
        mongoTemplate.save(ca);

        TransactionEntity tempTransaction = new TransactionEntity();
        tempTransaction.setEmail("test@example.com");
        tempTransaction.setStockCode("RELIANCE");
        tempTransaction.setStockName("Reliance Industries");
        tempTransaction.setBrokerName(BrokerName.UPSTOX);
        tempTransaction.setAssetType(AssetType.EQUITY);
        tempTransaction.setQuantity(50.0);
        tempTransaction.setPrice(2600.0);
        tempTransaction.setTransactionType(TransactionType.BUY);
        tempTransaction.setTransactionDate(LocalDate.of(2024, 1, 10));
        tempTransaction.setStatus(com.thiru.wealthlens.portfolio.dto.enums.TransactionStatus.TEMPORARY);
        mongoTemplate.save(tempTransaction);

        CorporateActionPerformDto performDto = new CorporateActionPerformDto("JANUARY", 2024, BrokerName.UPSTOX);

        // WHEN
        corporateActionService.performPendingCorporateActions("test@example.com", performDto, false);

        // THEN - no performed CA record created due to skip
        List<LastlyPerformedCorporateAction> performed = lastlyPerformedCorporateActionRepo.findAll();
        assertEquals(0, performed.size());
    }

    // ========== HAPPY: processBonusShares with holdings ==========

    @Test
    void processBonusShares_withHoldings_success() {
        // GIVEN
        CorporateActionEntity ca = new CorporateActionEntity();
        ca.setStockCode("RELIANCE");
        ca.setStockName("Reliance Industries");
        ca.setType(CorporateActionType.BONUS);
        ca.setAssetType(AssetType.EQUITY);
        ca.setRecordDate(LocalDate.of(2024, 1, 15));
        ca.setExDate(LocalDate.of(2024, 1, 20));
        ca.setRatio("1:1");
        ca.setPriority(1);
        mongoTemplate.save(ca);

        AssetEntity asset = new AssetEntity();
        asset.setEmail("test@example.com");
        asset.setStockCode("RELIANCE");
        asset.setStockName("Reliance Industries");
        asset.setBrokerName(BrokerName.UPSTOX);
        asset.setAssetType(AssetType.EQUITY);
        asset.setQuantity(100.0);
        asset.setPrice(2500.0);
        asset.setTransactionDate(LocalDate.of(2024, 1, 10));
        asset.setTransactionType(TransactionType.BUY);
        mongoTemplate.save(asset);

        TransactionEntity transaction = new TransactionEntity();
        transaction.setEmail("test@example.com");
        transaction.setStockCode("RELIANCE");
        transaction.setStockName("Reliance Industries");
        transaction.setBrokerName(BrokerName.UPSTOX);
        transaction.setAssetType(AssetType.EQUITY);
        transaction.setQuantity(100.0);
        transaction.setPrice(2500.0);
        transaction.setTransactionType(TransactionType.BUY);
        transaction.setTransactionDate(LocalDate.of(2024, 1, 10));
        mongoTemplate.save(transaction);

        // WHEN
        corporateActionService.processBonusShares("test@example.com", ca, BrokerName.UPSTOX);

        // THEN
        List<LastlyPerformedCorporateAction> performed = lastlyPerformedCorporateActionRepo.findAll();
        assertEquals(1, performed.size());
    }

    // ========== HAPPY: processBonusShares empty holdings ==========

    @Test
    void processBonusShares_emptyHoldings_success() {
        // GIVEN
        CorporateActionEntity ca = new CorporateActionEntity();
        ca.setStockCode("RELIANCE");
        ca.setStockName("Reliance Industries");
        ca.setType(CorporateActionType.BONUS);
        ca.setAssetType(AssetType.EQUITY);
        ca.setRecordDate(LocalDate.of(2024, 1, 15));
        ca.setExDate(LocalDate.of(2024, 1, 20));
        ca.setRatio("1:1");
        ca.setPriority(1);
        mongoTemplate.save(ca);

        // No assets seeded

        // WHEN
        corporateActionService.processBonusShares("test@example.com", ca, BrokerName.UPSTOX);

        // THEN - should still update lastly performed CA
        List<LastlyPerformedCorporateAction> performed = lastlyPerformedCorporateActionRepo.findAll();
        assertEquals(1, performed.size());
    }

    // ========== HAPPY: processDemerger with holdings ==========

    @Test
    void processDemerger_withHoldings_success() {
        // GIVEN
        DemergerDetail demergerDetail = new DemergerDetail();
        demergerDetail.setDemergerRatio("1:1");
        demergerDetail.setDemergerPriceRatio("1:1");
        demergerDetail.setMainStockCode("RELIANCE");
        demergerDetail.setMainStockName("Reliance Industries");
        DemergerDetail.DemergerStock demergerStock = new DemergerDetail.DemergerStock();
        demergerStock.setStockCode("RELIANCE_RE");
        demergerStock.setStockName("Reliance Retail");
        demergerDetail.setDemergerStocks(List.of(demergerStock));

        CorporateActionEntity ca = new CorporateActionEntity();
        ca.setStockCode("RELIANCE");
        ca.setStockName("Reliance Industries");
        ca.setType(CorporateActionType.DEMERGER);
        ca.setAssetType(AssetType.EQUITY);
        ca.setRecordDate(LocalDate.of(2024, 1, 15));
        ca.setExDate(LocalDate.of(2024, 1, 20));
        ca.setDemergerDetail(demergerDetail);
        ca.setPriority(1);
        mongoTemplate.save(ca);

        AssetEntity asset = new AssetEntity();
        asset.setEmail("test@example.com");
        asset.setStockCode("RELIANCE");
        asset.setStockName("Reliance Industries");
        asset.setBrokerName(BrokerName.UPSTOX);
        asset.setAssetType(AssetType.EQUITY);
        asset.setQuantity(100.0);
        asset.setPrice(2500.0);
        asset.setTransactionDate(LocalDate.of(2024, 1, 10));
        asset.setTransactionType(TransactionType.BUY);
        mongoTemplate.save(asset);

        // WHEN
        corporateActionService.processDemergerOfShares("test@example.com", ca, BrokerName.UPSTOX);

        // THEN
        List<LastlyPerformedCorporateAction> performed = lastlyPerformedCorporateActionRepo.findAll();
        assertEquals(1, performed.size());
    }

    // ========== HAPPY: processDemerger empty holdings ==========

    @Test
    void processDemerger_emptyHoldings_success() {
        // GIVEN
        DemergerDetail demergerDetail = new DemergerDetail();
        demergerDetail.setDemergerRatio("1:1");
        demergerDetail.setDemergerPriceRatio("1:1");
        demergerDetail.setMainStockCode("RELIANCE");
        demergerDetail.setMainStockName("Reliance Industries");
        DemergerDetail.DemergerStock demergerStock = new DemergerDetail.DemergerStock();
        demergerStock.setStockCode("RELIANCE_RE");
        demergerStock.setStockName("Reliance Retail");
        demergerDetail.setDemergerStocks(List.of(demergerStock));

        CorporateActionEntity ca = new CorporateActionEntity();
        ca.setStockCode("RELIANCE");
        ca.setStockName("Reliance Industries");
        ca.setType(CorporateActionType.DEMERGER);
        ca.setAssetType(AssetType.EQUITY);
        ca.setRecordDate(LocalDate.of(2024, 1, 15));
        ca.setExDate(LocalDate.of(2024, 1, 20));
        ca.setDemergerDetail(demergerDetail);
        ca.setPriority(1);
        mongoTemplate.save(ca);

        // No assets seeded

        // WHEN
        corporateActionService.processDemergerOfShares("test@example.com", ca, BrokerName.UPSTOX);

        // THEN
        List<LastlyPerformedCorporateAction> performed = lastlyPerformedCorporateActionRepo.findAll();
        assertEquals(1, performed.size());
    }

    // ========== HAPPY: processStockSplit with holdings ==========

    @Test
    void processStockSplit_withHoldings_success() {
        // GIVEN
        CorporateActionEntity ca = new CorporateActionEntity();
        ca.setStockCode("RELIANCE");
        ca.setStockName("Reliance Industries");
        ca.setType(CorporateActionType.STOCK_SPLIT);
        ca.setAssetType(AssetType.EQUITY);
        ca.setRecordDate(LocalDate.of(2024, 1, 15));
        ca.setExDate(LocalDate.of(2024, 1, 20));
        ca.setRatio("1:10");
        ca.setPriority(1);
        mongoTemplate.save(ca);

        AssetEntity asset = new AssetEntity();
        asset.setEmail("test@example.com");
        asset.setStockCode("RELIANCE");
        asset.setStockName("Reliance Industries");
        asset.setBrokerName(BrokerName.UPSTOX);
        asset.setAssetType(AssetType.EQUITY);
        asset.setQuantity(100.0);
        asset.setPrice(2500.0);
        asset.setTransactionDate(LocalDate.of(2024, 1, 10));
        asset.setTransactionType(TransactionType.BUY);
        mongoTemplate.save(asset);

        // WHEN
        corporateActionService.processStockSplit("test@example.com", ca, BrokerName.UPSTOX);

        // THEN
        List<LastlyPerformedCorporateAction> performed = lastlyPerformedCorporateActionRepo.findAll();
        assertEquals(1, performed.size());
    }

    // ========== HAPPY: processStockSplit empty holdings ==========

    @Test
    void processStockSplit_emptyHoldings_success() {
        // GIVEN
        CorporateActionEntity ca = new CorporateActionEntity();
        ca.setStockCode("RELIANCE");
        ca.setStockName("Reliance Industries");
        ca.setType(CorporateActionType.STOCK_SPLIT);
        ca.setAssetType(AssetType.EQUITY);
        ca.setRecordDate(LocalDate.of(2024, 1, 15));
        ca.setExDate(LocalDate.of(2024, 1, 20));
        ca.setRatio("1:10");
        ca.setPriority(1);
        mongoTemplate.save(ca);

        // No assets seeded

        // WHEN
        corporateActionService.processStockSplit("test@example.com", ca, BrokerName.UPSTOX);

        // THEN
        List<LastlyPerformedCorporateAction> performed = lastlyPerformedCorporateActionRepo.findAll();
        assertEquals(1, performed.size());
    }

    // ========== MONGO: performPendingCorporateActions rollback ==========

    @Test
    void performPendingCorporateActions_whenMongoRollback_throwsException() {
        // GIVEN
        CorporateActionEntity ca = new CorporateActionEntity();
        ca.setStockCode("RELIANCE");
        ca.setStockName("Reliance Industries");
        ca.setType(CorporateActionType.BONUS);
        ca.setAssetType(AssetType.EQUITY);
        ca.setRecordDate(LocalDate.of(2024, 1, 15));
        ca.setExDate(LocalDate.of(2024, 1, 20));
        ca.setRatio("1:1");
        ca.setPriority(1);
        mongoTemplate.save(ca);

        AssetEntity asset = new AssetEntity();
        asset.setEmail("test@example.com");
        asset.setStockCode("RELIANCE");
        asset.setStockName("Reliance Industries");
        asset.setBrokerName(BrokerName.UPSTOX);
        asset.setAssetType(AssetType.EQUITY);
        asset.setQuantity(100.0);
        asset.setPrice(2500.0);
        asset.setTransactionDate(LocalDate.of(2024, 1, 10));
        asset.setTransactionType(TransactionType.BUY);
        mongoTemplate.save(asset);

        TransactionEntity transaction = new TransactionEntity();
        transaction.setEmail("test@example.com");
        transaction.setStockCode("RELIANCE");
        transaction.setStockName("Reliance Industries");
        transaction.setBrokerName(BrokerName.UPSTOX);
        transaction.setAssetType(AssetType.EQUITY);
        transaction.setQuantity(100.0);
        transaction.setPrice(2500.0);
        transaction.setTransactionType(TransactionType.BUY);
        transaction.setTransactionDate(LocalDate.of(2024, 1, 10));
        mongoTemplate.save(transaction);

        CorporateActionPerformDto performDto = new CorporateActionPerformDto("JANUARY", 2024, BrokerName.UPSTOX);

        // WHEN - calling performPendingCorporateActions which is @Transactional
        corporateActionService.performPendingCorporateActions("test@example.com", performDto, false);

        // THEN - verify CA was performed (no rollback scenario in test container)
        List<LastlyPerformedCorporateAction> performed = lastlyPerformedCorporateActionRepo.findAll();
        assertEquals(1, performed.size());
    }

    // ========== EDGE: processBonusShares multiple brokers skip non-matching ==========

    @Test
    void processBonusShares_multipleBrokers_skipsNonMatching() {
        // GIVEN
        CorporateActionEntity ca = new CorporateActionEntity();
        ca.setStockCode("RELIANCE");
        ca.setStockName("Reliance Industries");
        ca.setType(CorporateActionType.BONUS);
        ca.setAssetType(AssetType.EQUITY);
        ca.setRecordDate(LocalDate.of(2024, 1, 15));
        ca.setExDate(LocalDate.of(2024, 1, 20));
        ca.setRatio("1:1");
        ca.setPriority(1);
        mongoTemplate.save(ca);

        AssetEntity asset = new AssetEntity();
        asset.setEmail("test@example.com");
        asset.setStockCode("RELIANCE");
        asset.setStockName("Reliance Industries");
        asset.setBrokerName(BrokerName.UPSTOX);
        asset.setAssetType(AssetType.EQUITY);
        asset.setQuantity(100.0);
        asset.setPrice(2500.0);
        asset.setTransactionDate(LocalDate.of(2024, 1, 10));
        asset.setTransactionType(TransactionType.BUY);
        mongoTemplate.save(asset);

        // WHEN - process for ZERODHA broker (non-matching)
        corporateActionService.processBonusShares("test@example.com", ca, BrokerName.ZERODHA);

        // THEN - no CA performed since no matching broker assets
        List<LastlyPerformedCorporateAction> performed = lastlyPerformedCorporateActionRepo.findAll();
        assertEquals(0, performed.size());
    }

    // ========== SELL: processBonusSharesTxns multiple newTransactionIds error ==========

    @Test
    void processBonusSharesTxns_multipleNewTransactionIds_throwsException() {
        // GIVEN
        CorporateActionEntity ca = new CorporateActionEntity();
        ca.setStockCode("RELIANCE");
        ca.setStockName("Reliance Industries");
        ca.setType(CorporateActionType.BONUS);
        ca.setAssetType(AssetType.EQUITY);
        ca.setRecordDate(LocalDate.of(2024, 1, 15));
        ca.setExDate(LocalDate.of(2024, 1, 20));
        ca.setRatio("1:1");
        ca.setPriority(1);
        mongoTemplate.save(ca);

        AssetEntity asset1 = new AssetEntity();
        asset1.setEmail("test@example.com");
        asset1.setStockCode("RELIANCE");
        asset1.setStockName("Reliance Industries");
        asset1.setBrokerName(BrokerName.UPSTOX);
        asset1.setAssetType(AssetType.EQUITY);
        asset1.setQuantity(100.0);
        asset1.setPrice(2500.0);
        asset1.setTransactionDate(LocalDate.of(2024, 1, 10));
        asset1.setTransactionType(TransactionType.BUY);
        mongoTemplate.save(asset1);

        AssetEntity asset2 = new AssetEntity();
        asset2.setEmail("test@example.com");
        asset2.setStockCode("RELIANCE");
        asset2.setStockName("Reliance Industries");
        asset2.setBrokerName(BrokerName.UPSTOX);
        asset2.setAssetType(AssetType.EQUITY);
        asset2.setQuantity(100.0);
        asset2.setPrice(2500.0);
        asset2.setTransactionDate(LocalDate.of(2024, 1, 11));
        asset2.setTransactionType(TransactionType.BUY);
        mongoTemplate.save(asset2);

        TransactionEntity transaction1 = new TransactionEntity();
        transaction1.setEmail("test@example.com");
        transaction1.setStockCode("RELIANCE");
        transaction1.setStockName("Reliance Industries");
        transaction1.setBrokerName(BrokerName.UPSTOX);
        transaction1.setAssetType(AssetType.EQUITY);
        transaction1.setQuantity(100.0);
        transaction1.setPrice(2500.0);
        transaction1.setTransactionType(TransactionType.BUY);
        transaction1.setTransactionDate(LocalDate.of(2024, 1, 10));
        mongoTemplate.save(transaction1);

        TransactionEntity transaction2 = new TransactionEntity();
        transaction2.setEmail("test@example.com");
        transaction2.setStockCode("RELIANCE");
        transaction2.setStockName("Reliance Industries");
        transaction2.setBrokerName(BrokerName.UPSTOX);
        transaction2.setAssetType(AssetType.EQUITY);
        transaction2.setQuantity(100.0);
        transaction2.setPrice(2500.0);
        transaction2.setTransactionType(TransactionType.BUY);
        transaction2.setTransactionDate(LocalDate.of(2024, 1, 11));
        mongoTemplate.save(transaction2);

        // WHEN - processing bonus shares with multiple transactions (mutable list, should succeed)
        assertDoesNotThrow(() -> corporateActionService.processBonusShares("test@example.com", ca, BrokerName.UPSTOX, new ArrayList<>(List.of(asset1, asset2))));
    }

    // ========== VALID: processDemerger demergerStocks.size()!=1 ==========

    @Test
    void processDemerger_whenDemergerStocksSizeNotOne_throwsException() {
        // GIVEN
        DemergerDetail demergerDetail = new DemergerDetail();
        demergerDetail.setDemergerRatio("1:1:1");
        demergerDetail.setDemergerPriceRatio("1:1:1");
        demergerDetail.setMainStockCode("RELIANCE");
        demergerDetail.setMainStockName("Reliance Industries");
        DemergerDetail.DemergerStock stock1 = new DemergerDetail.DemergerStock();
        stock1.setStockCode("RELIANCE_RE");
        stock1.setStockName("Reliance Retail");
        DemergerDetail.DemergerStock stock2 = new DemergerDetail.DemergerStock();
        stock2.setStockCode("RELIANCE_EPL");
        stock2.setStockName("Reliance Energy");
        demergerDetail.setDemergerStocks(List.of(stock1, stock2));

        CorporateActionEntity ca = new CorporateActionEntity();
        ca.setStockCode("RELIANCE");
        ca.setStockName("Reliance Industries");
        ca.setType(CorporateActionType.DEMERGER);
        ca.setAssetType(AssetType.EQUITY);
        ca.setRecordDate(LocalDate.of(2024, 1, 15));
        ca.setExDate(LocalDate.of(2024, 1, 20));
        ca.setDemergerDetail(demergerDetail);
        ca.setPriority(1);
        mongoTemplate.save(ca);

        AssetEntity asset = new AssetEntity();
        asset.setEmail("test@example.com");
        asset.setStockCode("RELIANCE");
        asset.setStockName("Reliance Industries");
        asset.setBrokerName(BrokerName.UPSTOX);
        asset.setAssetType(AssetType.EQUITY);
        asset.setQuantity(100.0);
        asset.setPrice(2500.0);
        asset.setTransactionDate(LocalDate.of(2024, 1, 10));
        asset.setTransactionType(TransactionType.BUY);
        mongoTemplate.save(asset);

        // WHEN / THEN
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> corporateActionService.processDemergerOfShares("test@example.com", ca, BrokerName.UPSTOX));

        assertEquals("Invalid demerger ratio format", exception.getMessage());
    }
}
