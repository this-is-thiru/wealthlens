package com.thiru.wealthlens.integration;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertBreakdown;
import static com.thiru.wealthlens.testsupport.MoneyAssert.assertMoney;
import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.context.LotSlice;
import com.thiru.wealthlens.brokercharges.dto.enums.AmcChargeFrequency;
import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeCategory;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeSide;
import com.thiru.wealthlens.brokercharges.dto.enums.DedupeScope;
import com.thiru.wealthlens.brokercharges.dto.enums.FundCategory;
import com.thiru.wealthlens.brokercharges.dto.enums.PlanType;
import com.thiru.wealthlens.brokercharges.dto.enums.RoundingPolicy;
import com.thiru.wealthlens.brokercharges.dto.enums.SlabBandBasis;
import com.thiru.wealthlens.brokercharges.entity.ChargeAccountEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeInstrumentEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeSlab;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.brokercharges.entity.model.ChargeSummaryReport;
import com.thiru.wealthlens.brokercharges.repository.ChargeAccountRepository;
import com.thiru.wealthlens.brokercharges.repository.ChargeInstrumentRepository;
import com.thiru.wealthlens.brokercharges.repository.ChargeScheduleRepository;
import com.thiru.wealthlens.brokercharges.repository.UserChargeRepository;
import com.thiru.wealthlens.brokercharges.service.AmcChargeService;
import com.thiru.wealthlens.brokercharges.service.ChargeSeederService;
import com.thiru.wealthlens.brokercharges.service.UserChargeService;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionStatus;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.repository.TransactionRepository;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import io.restassured.RestAssured;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * The charges engine against a real MongoDB and a real HTTP stack.
 *
 * <p>Two things can only be shown here. Deduplication is one: whether a depository charge is levied
 * once or twice for two sells of the same scrip on the same day is decided by an indexed query, and
 * a unit test that stubs that query proves only that the stub agrees with itself. Authorisation is
 * the other: the security chain ends in {@code anyRequest().permitAll()}, so whether a rate-card
 * endpoint is protected is a property of the filter chain, not of any class.
 *
 * <p>Every test writes the rate card it depends on. Seeding is an explicit operation rather than a
 * startup side effect (ADR-27), and {@code @BeforeEach} runs it for the catalogue's sake and then
 * clears the shipped cards and profiles — so a test leaning on shipped data would pass alone and
 * fail in company.
 */
class ChargesIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "charges-it@example.com";
    private static final String OTHER_EMAIL = "someone-else@example.com";
    private static final String HOLDER = "self";
    private static final String OTHER_HOLDER = "spouse";
    private static final LocalDate TRADE_DATE = LocalDate.of(2025, 6, 2);

    @Autowired
    private UserChargeService userChargeService;

    @Autowired
    private AmcChargeService amcChargeService;

    @Autowired
    private UserChargeRepository userChargeRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ChargeScheduleRepository scheduleRepository;

    @Autowired
    private ChargeAccountRepository chargeAccountRepository;

    @Autowired
    private ChargeInstrumentRepository instrumentRepository;

    @Autowired
    private ChargeSeederService chargeSeederService;

    @BeforeEach
    void seedOneKnownRateCard() {
        // The catalogue has to exist before anything can be published: the validator rejects a rule
        // whose code it does not carry. Nothing seeds at startup any more (ADR-27), so this asks for
        // it explicitly rather than depending on which test happened to run first.
        chargeSeederService.seed("integration-test");

        // Then the shipped cards and profiles go, because this class asserts over the ones it
        // writes and shipped data in the same collections would make those assertions depend on
        // ordering. charge_catalogue survives — it is reference data, and every card needs it.
        mongoTemplate.getCollection("charge_schedules").deleteMany(new Document());
        mongoTemplate.getCollection("charge_instruments").deleteMany(new Document());
        scheduleRepository.save(equityCard("IT_EQ_2025", LocalDate.of(2025, 1, 1), null, 0.0));
    }

    // ------------------------------------------------------------ Tier H — persistence

    @Test
    void computeAndRecord_writesOneRowCarryingEveryLineAndItsBreakdown() {
        // When
        ChargeComputation computation = userChargeService.computeAndRecord(sell("txn-1", "INFY", HOLDER, TRADE_DATE));

        // Then
        assertThat(computation.resolution()).isEqualTo(ChargeResolution.RESOLVED);

        UserChargeEntity stored = userChargeRepository.findByEmailAndTransactionId(EMAIL, "txn-1").orElseThrow();
        assertBreakdown(Map.of("BROKERAGE", 0.00, "STT", 100.00, "DP", 13.50, "GST", 2.43),
                stored.getAmountByCode());
        assertThat(stored.getLines()).extracting(line -> line.getCode())
                .containsExactly("BROKERAGE", "STT", "DP", "GST");
        assertMoney(115.93, stored.getTotalCharges());
    }

    @Test
    void computeAndRecord_recordsWhichRateCardPricedTheTrade() {
        // Given / When — provenance is what lets a corrected card find every row it touched
        userChargeService.computeAndRecord(sell("txn-1", "INFY", HOLDER, TRADE_DATE));

        // Then
        UserChargeEntity stored = userChargeRepository.findByEmailAndTransactionId(EMAIL, "txn-1").orElseThrow();
        assertThat(stored.getScheduleCode()).isEqualTo("IT_EQ_2025");
        assertThat(stored.getScheduleId()).isNotBlank();
        assertThat(userChargeRepository.findByScheduleId(stored.getScheduleId()))
                .extracting(UserChargeEntity::getTransactionId).containsExactly("txn-1");
    }

    @Test
    void depositoryCharge_acrossTwoSellsOfTheSameScripOnTheSameDay_isLeviedOnce() {
        // Given / When — the real dedupe path: two separate transactions, one after the other
        userChargeService.computeAndRecord(sell("txn-1", "INFY", HOLDER, TRADE_DATE));
        ChargeComputation second = userChargeService.computeAndRecord(sell("txn-2", "INFY", HOLDER, TRADE_DATE));

        // Then — AC-4. The second sell pays no depository charge, and no GST on one either
        assertMoney(0.00, second.amountOf("DP"));
        assertMoney(0.00, second.amountOf("GST"));
        assertMoney(100.00, second.total());
    }

    @Test
    void depositoryCharge_forADifferentScripOnTheSameDay_isLeviedAgain() {
        // Given
        userChargeService.computeAndRecord(sell("txn-1", "INFY", HOLDER, TRADE_DATE));

        // When
        ChargeComputation other = userChargeService.computeAndRecord(sell("txn-2", "TCS", HOLDER, TRADE_DATE));

        // Then — the charge is per scrip, so a different scrip is a different debit
        assertMoney(13.50, other.amountOf("DP"));
    }

    @Test
    void depositoryCharge_forTheSameScripOnTheNextDay_isLeviedAgain() {
        // Given
        userChargeService.computeAndRecord(sell("txn-1", "INFY", HOLDER, TRADE_DATE));

        // When
        ChargeComputation nextDay = userChargeService.computeAndRecord(
                sell("txn-2", "INFY", HOLDER, TRADE_DATE.plusDays(1)));

        // Then
        assertMoney(13.50, nextDay.amountOf("DP"));
    }

    @Test
    void depositoryCharge_forTwoAccountHoldersOnTheSameScripAndDay_isLeviedTwice() {
        // Given — D10. A depository charge is levied per demat account, so two holders selling the
        // same scrip on the same day incur two debits. Keying without the holder undercharges.
        userChargeService.computeAndRecord(sell("txn-1", "INFY", HOLDER, TRADE_DATE));

        // When
        ChargeComputation spouse = userChargeService.computeAndRecord(
                sell("txn-2", "INFY", OTHER_HOLDER, TRADE_DATE));

        // Then
        assertMoney(13.50, spouse.amountOf("DP"));
    }

    @Test
    void deleteByEmail_removesThatUsersChargesAndNobodyElses() {
        // Given
        userChargeService.computeAndRecord(sell("txn-1", "INFY", HOLDER, TRADE_DATE));
        userChargeService.computeAndRecord(sellFor(OTHER_EMAIL, "txn-2", "INFY", HOLDER, TRADE_DATE));

        // When
        userChargeService.deleteByEmail(EMAIL);

        // Then
        assertThat(userChargeService.findHistory(EMAIL)).isEmpty();
        assertThat(userChargeService.findHistory(OTHER_EMAIL)).hasSize(1);
    }

    @Test
    void chargeSummary_rollsUpSeveralTradesIntoOneBreakdown() {
        // Given — three trades across two scrips on one day
        userChargeService.computeAndRecord(sell("txn-1", "INFY", HOLDER, TRADE_DATE));
        userChargeService.computeAndRecord(sell("txn-2", "INFY", HOLDER, TRADE_DATE));
        userChargeService.computeAndRecord(sell("txn-3", "TCS", HOLDER, TRADE_DATE));

        // When — the Chunk 7 aggregation shape over the rows the engine actually wrote
        ChargeSummaryReport summary = new ChargeSummaryReport();
        userChargeService.findHistory(EMAIL).forEach(charge -> summary.merge(charge.getAmountByCode()));

        // Then — one depository charge per scrip, three lots of securities transaction tax
        assertBreakdown(Map.of("BROKERAGE", 0.00, "STT", 300.00, "DP", 27.00, "GST", 4.86),
                summary.getAmountByCode());
        assertMoney(331.86, summary.getTotalCharges());
    }

    @Test
    void amcCycle_billsARegisteredAccountAndRecordsIt() {
        // Given — no AMC card ships, so the test writes one
        scheduleRepository.save(amcCard());
        chargeAccountRepository.save(account());

        // When
        List<ChargeAccountEntity> billed = amcChargeService.runCycle(
                AmcChargeFrequency.ANNUALLY, LocalDate.of(2025, 12, 31));

        // Then
        assertThat(billed).hasSize(1);
        UserChargeEntity charge = userChargeService.findHistory(EMAIL).stream()
                .filter(row -> row.getEvent() == ChargeEvent.AMC_CYCLE)
                .findFirst().orElseThrow();
        assertBreakdown(Map.of("AMC", 300.00, "GST", 54.00), charge.getAmountByCode());
    }

    @Test
    void amcCycle_runTwiceForTheSamePeriod_billsOnce() {
        // Given
        scheduleRepository.save(amcCard());
        chargeAccountRepository.save(account());
        LocalDate through = LocalDate.of(2025, 12, 31);

        // When
        amcChargeService.runCycle(AmcChargeFrequency.ANNUALLY, through);
        List<ChargeAccountEntity> secondRun = amcChargeService.runCycle(AmcChargeFrequency.ANNUALLY, through);

        // Then — a duplicate charge is indistinguishable from a legitimate one once written
        assertThat(secondRun).isEmpty();
        assertThat(userChargeService.findHistory(EMAIL)).hasSize(1);
    }

    @Test
    void exitLoad_isChargedOnTheLotsInsideTheWindowAndOnNoOthers() {
        // Given — AC-6, over a real document rather than an object in memory. The load lives on the
        // scheme's profile, is banded by holding period and is priced per FIFO lot; all three of
        // those are fields that a mapping can drop silently, and each drop changes the amount.
        scheduleRepository.save(mutualFundCard());
        instrumentRepository.save(gradedExitLoadProfile());

        // When — ₹1,00,000 redeemed from two lots: 600 units held sixteen months, 400 held three
        ChargeComputation computation = userChargeService.computeAndRecord(redemption(
                List.of(new LotSlice(600, LocalDate.of(2024, 2, 1), 100),
                        new LotSlice(400, LocalDate.of(2025, 3, 1), 100))));

        // Then — 1% of the younger lot's ₹40,000 and nothing on the older one. Averaging the load
        // over the whole redemption would charge ₹1,000; ignoring perLot, the same. Banding by
        // turnover rather than by holding period would charge nothing at all.
        assertMoney(400.00, computation.amountOf("EXIT_LOAD"));

        UserChargeEntity stored = userChargeRepository.findByEmailAndTransactionId(EMAIL, "txn-mf").orElseThrow();
        assertThat(stored.getInstrumentId()).isNotBlank();
        assertBreakdown(Map.of("EXIT_LOAD", 400.00, "STT", 1.00), stored.getAmountByCode());
    }

    @Test
    void exitLoad_whenEveryLotIsOutsideTheWindow_isNotCharged() {
        // Given — the *only* in AC-6. A redemption of units held past the load period is free, and
        // has to read as free rather than as a scheme whose profile failed to load.
        scheduleRepository.save(mutualFundCard());
        instrumentRepository.save(gradedExitLoadProfile());

        // When
        ChargeComputation computation = userChargeService.computeAndRecord(redemption(
                List.of(new LotSlice(1000, LocalDate.of(2023, 1, 1), 100))));

        // Then — priced, resolved, and zero, which is a different fact from NO_INSTRUMENT_PROFILE
        assertThat(computation.resolution()).isEqualTo(ChargeResolution.RESOLVED);
        assertMoney(0.00, computation.amountOf("EXIT_LOAD"));
        assertMoney(1.00, computation.total());
    }

    // ------------------------------------------------------------------ Tier I — API

    @Test
    void simulateEndpoint_withFifoLots_chargesExitLoadOnTheLotsInsideTheWindow() {
        // Given — AC-6 over HTTP, which is the only way a human can see it. The scheme's exit load
        // is priced per lot, so the request has to be able to say which lots the redemption drew on.
        scheduleRepository.save(mutualFundCard());
        instrumentRepository.save(gradedExitLoadProfile());
        long before = userChargeRepository.count();

        // When — ₹1,00,000 redeemed from 600 units held sixteen months and 400 held three
        ResponseEntity<String> response = post("/charges/simulate", token(EMAIL), Map.of(
                "brokerName", "ZERODHA", "assetType", "MUTUAL_FUND", "event", "SELL",
                "stockCode", "PARAGPARIKHFLEXICAP", "price", 100, "quantity", 1000,
                "transactionDate", "2025-06-02",
                "lots", List.of(
                        Map.of("quantity", 600, "acquisitionDate", "2024-02-01", "price", 100),
                        Map.of("quantity", 400, "acquisitionDate", "2025-03-01", "price", 100))));

        // Then — 1% of the younger lot's ₹40,000 and nothing on the older one. Before lots were
        // accepted here this endpoint answered ₹0 however the profile was written.
        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getBody()).contains("\"EXIT_LOAD\"").contains("400.0");
        assertThat(userChargeRepository.count()).isEqualTo(before);
    }

    @Test
    void simulateEndpoint_whenTheLotsDoNotAddUpToTheTrade_isRejected() {
        // Given — a redemption of 1,000 units with 900 accounted for. Priced as sent, the shortfall
        // costs nothing and the caller gets a smaller number rather than an error.
        scheduleRepository.save(mutualFundCard());
        instrumentRepository.save(gradedExitLoadProfile());

        // When
        ResponseEntity<String> response = post("/charges/simulate", token(EMAIL), Map.of(
                "brokerName", "ZERODHA", "assetType", "MUTUAL_FUND", "event", "SELL",
                "stockCode", "PARAGPARIKHFLEXICAP", "price", 100, "quantity", 1000,
                "transactionDate", "2025-06-02",
                "lots", List.of(
                        Map.of("quantity", 500, "acquisitionDate", "2024-02-01", "price", 100),
                        Map.of("quantity", 400, "acquisitionDate", "2025-03-01", "price", 100))));

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getBody()).contains("900").contains("1000");
    }

    @Test
    void seedEndpoint_appliesTheShippedDataAndSaysWhoAskedForIt() {
        // Given — nothing seeds at startup any more, so this is the only way cards arrive
        long before = scheduleRepository.count();

        // When
        ResponseEntity<String> response = post("/charges/seed", adminToken(), null);

        // Then — every document it wrote names the caller, which auditing never did: the
        // annotations live inside AuditMetadata, an embedded document, and the callbacks fire for
        // an aggregate root
        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getBody()).contains("\"seededBy\":\"" + EMAIL + "\"");
        assertThat(scheduleRepository.count()).isGreaterThan(before);

        ChargeScheduleEntity seeded =
                scheduleRepository.findByScheduleCode("ZERODHA_EQ_DELIVERY_2025_04").orElseThrow();
        assertThat(seeded.getAuditMetadata().getCreatedBy()).isEqualTo(EMAIL);
        assertThat(seeded.getAuditMetadata().getCreatedAt()).isNotNull();
    }

    @Test
    void seedEndpoint_runTwice_writesNothingTheSecondTime() {
        // Given — safe to put on a deployment checklist rather than something to be careful about
        post("/charges/seed", adminToken(), null);

        // When
        ResponseEntity<String> second = post("/charges/seed", adminToken(), null);

        // Then
        assertThat(second.getStatusCode().value()).isEqualTo(HttpStatus.OK.value());
        assertThat(second.getBody()).contains("\"schedulesCreated\":[]");
    }

    @Test
    void seedEndpoint_isRefusedToAnOrdinaryUser() {
        // Given — it writes the rate cards every user is charged against
        ResponseEntity<String> response = post("/charges/seed", token(EMAIL), null);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void driftEndpoint_reportsShippedCardsThatTheDatabaseDoesNotMatch() {
        // Given — @BeforeEach clears charge_schedules, so every shipped card is absent. That is the
        // state a deployment is in before anybody has seeded it, and it must be reported rather
        // than mistaken for agreement.
        ResponseEntity<String> response = get("/charge-schedules/drift", adminToken());

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getBody())
                .contains("ZERODHA_EQ_DELIVERY_2025_04")
                .contains("absent from the database");
    }

    @Test
    void driftEndpoint_isRefusedToAnOrdinaryUser() {
        // Given — it discloses every broker's full pricing
        ResponseEntity<String> response = get("/charge-schedules/drift", token(EMAIL));

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void simulateEndpoint_returnsTheBreakdownAndPersistsNothing() {
        // Given
        long before = userChargeRepository.count();

        // When
        ResponseEntity<String> response = post("/charges/simulate", token(EMAIL), Map.of(
                "brokerName", "ZERODHA", "assetType", "EQUITY", "segment", "DELIVERY",
                "event", "SELL", "stockCode", "INFY", "price", 1000, "quantity", 100,
                "transactionDate", "2025-06-02"));

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getBody()).contains("\"STT\"").contains("115.93");
        assertThat(userChargeRepository.count()).isEqualTo(before);
    }

    @Test
    void publishEndpoint_overAnOpenCard_closesTheIncumbent() {
        // Given — AC-8. The superseded implementation threw unless the incumbent was closed by hand.
        ChargeScheduleEntity successor = equityCard("IT_EQ_2026", LocalDate.of(2026, 1, 1), null, 5.0);

        // When
        ResponseEntity<String> response = post("/charge-schedules", adminToken(), successor);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.OK.value());
        ChargeScheduleEntity incumbent = scheduleRepository.findByScheduleCode("IT_EQ_2025").orElseThrow();
        assertThat(incumbent.getEndDate()).isEqualTo(LocalDate.of(2025, 12, 31));

        // Superseding closes the window and deliberately leaves the status alone. Status says
        // whether a card is legitimate, not whether it is current — a card marked SUPERSEDED would
        // be excluded from resolution, and the trades inside its own window would then price at
        // nothing. The following test is the other half of that claim.
        assertThat(incumbent.getStatus()).isEqualTo(EntityStatus.ACTIVE);
    }

    @Test
    void aSupersededCard_stillPricesTradesInsideItsOwnWindow() {
        // Given — uploading a past quarter long after the rates changed is the normal case
        post("/charge-schedules", adminToken(), equityCard("IT_EQ_2026", LocalDate.of(2026, 1, 1), null, 5.0));

        // When
        ChargeComputation old = userChargeService.computeAndRecord(sell("txn-old", "INFY", HOLDER, TRADE_DATE));
        ChargeComputation recent = userChargeService.computeAndRecord(
                sell("txn-new", "INFY", HOLDER, LocalDate.of(2026, 6, 2)));

        // Then — the 2025 trade keeps the 2025 brokerage of nil; the 2026 trade pays the new one
        assertThat(old.scheduleCode()).isEqualTo("IT_EQ_2025");
        assertMoney(0.00, old.amountOf("BROKERAGE"));
        assertThat(recent.scheduleCode()).isEqualTo("IT_EQ_2026");
        assertMoney(5.00, recent.amountOf("BROKERAGE"));
    }

    @Test
    void publishEndpoint_whenTheCardIsInvalid_answers400WithAReadableMessage() {
        // Given — AC-9. A rule naming a code the catalogue does not carry
        ChargeScheduleEntity card = equityCard("IT_EQ_BAD", LocalDate.of(2027, 1, 1), null, 0.0);
        card.getRules().add(flatRule("NOT_A_REAL_CODE", ChargeCategory.EXCHANGE, 1.00, 90));

        // When
        ResponseEntity<String> response = post("/charge-schedules", adminToken(), card);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getBody()).contains("NOT_A_REAL_CODE").contains("catalogue");
    }

    @Test
    void historyEndpoint_returnsOnlyTheChargesOfTheUserInThePath() {
        // Given
        userChargeService.computeAndRecord(sell("txn-1", "INFY", HOLDER, TRADE_DATE));
        userChargeService.computeAndRecord(sellFor(OTHER_EMAIL, "txn-2", "TCS", HOLDER, TRADE_DATE));

        // When
        ResponseEntity<String> response = get("/user-charges/user/" + EMAIL, token(EMAIL));

        // Then
        assertThat(response.getBody()).contains("txn-1").doesNotContain("txn-2");
    }

    @Test
    void historyEndpoint_narrowsToTheRequestedDateRange() {
        // Given
        userChargeService.computeAndRecord(sell("txn-june", "INFY", HOLDER, TRADE_DATE));
        userChargeService.computeAndRecord(sell("txn-july", "TCS", HOLDER, LocalDate.of(2025, 7, 10)));

        // When
        ResponseEntity<String> response = get(
                "/user-charges/user/" + EMAIL + "?from=2025-06-01&to=2025-06-30", token(EMAIL));

        // Then
        assertThat(response.getBody()).contains("txn-june").doesNotContain("txn-july");
    }

    @Test
    void historyEndpoint_whenOnlyOneBoundIsGiven_answers400() {
        // When
        ResponseEntity<String> response = get(
                "/user-charges/user/" + EMAIL + "?from=2025-06-01", token(EMAIL));

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getBody()).contains("both");
    }

    @Test
    void contractNoteEndpoint_returnsTheLinesInTheOrderTheyWereApplied() {
        // Given
        userChargeService.computeAndRecord(sell("txn-1", "INFY", HOLDER, TRADE_DATE));

        // When
        ResponseEntity<String> response = get(
                "/user-charges/user/" + EMAIL + "/transaction/txn-1", token(EMAIL));

        // Then — a contract note reads in the order it was applied, tax last
        String body = response.getBody();
        assertThat(body.indexOf("BROKERAGE")).isLessThan(body.indexOf("STT"));
        assertThat(body.indexOf("STT")).isLessThan(body.indexOf("DP"));
        assertThat(body.indexOf("DP")).isLessThan(body.indexOf("GST"));
    }

    @Test
    void gapsEndpoint_surfacesATradeWithNoRateCardForItsPeriod() {
        // Given — a trade from before any card starts. A warning in a log scrolls away; this does not.
        userChargeService.computeAndRecord(sell("txn-old", "INFY", HOLDER, LocalDate.of(2019, 4, 1)));

        // When
        ResponseEntity<String> response = get("/user-charges/user/" + EMAIL + "/gaps", token(EMAIL));

        // Then
        assertThat(response.getBody()).contains("txn-old").contains("NO_SCHEDULE");
    }

    @Test
    void catalogueEndpoint_listsTheSeededCharges() {
        // When
        ResponseEntity<String> response = get("/charge-catalogue", token(EMAIL));

        // Then — seeded at startup and preserved between tests, so this is the shipped registry
        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getBody()).contains("BROKERAGE").contains("STT").contains("GST");
    }

    @Test
    void chargeEndpoints_rejectAnUnauthenticatedRequest() {
        // Given / When — the security chain ends in permitAll, so this is the test that catches a
        // prefix nobody added to AuthConfig
        // Then
        assertThat(get("/user-charges/user/" + EMAIL, null).getStatusCode().value())
                .isIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
        assertThat(get("/charge-schedules?broker=ZERODHA", null).getStatusCode().value())
                .isIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
        assertThat(get("/charge-catalogue", null).getStatusCode().value())
                .isIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
    }

    @Test
    void publishingARateCard_rejectsAnOrdinaryUsersToken() {
        // Given — a card decides what every user is charged
        ChargeScheduleEntity card = equityCard("IT_EQ_2027", LocalDate.of(2027, 1, 1), null, 0.0);

        // When
        ResponseEntity<String> response = post("/charge-schedules", token(EMAIL), card);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(scheduleRepository.findByScheduleCode("IT_EQ_2027")).isEmpty();
    }

    @Test
    void imposingAmcCharges_rejectsAnOrdinaryUsersToken() {
        // Given — one call bills every account of a frequency
        scheduleRepository.save(amcCard());
        chargeAccountRepository.save(account());

        // When
        ResponseEntity<String> response = post(
                "/charges/amc/impose?frequency=ANNUALLY&billedThrough=2025-12-31", token(EMAIL), null);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(userChargeRepository.count()).isZero();
    }

    @Test
    void registeringAChargeAccount_storesItAgainstTheUserInThePath() {
        // Given — the path owns the email, so a body claiming another user cannot smuggle one in
        ChargeAccountEntity account = account();
        account.setEmail(OTHER_EMAIL);

        // When
        ResponseEntity<String> response = post("/charge-accounts/user/" + EMAIL, token(EMAIL), account);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.OK.value());
        assertThat(chargeAccountRepository.findByEmail(EMAIL)).hasSize(1);
        assertThat(chargeAccountRepository.findByEmail(OTHER_EMAIL)).isEmpty();
    }


    // ------------------------------------------------------- Phase B — reconciliation

    /**
     * The report the phase exists to produce, over real documents in two collections. The join is
     * {@code UserChargeEntity.transactionId} to {@code TransactionEntity.id}, and a unit test that
     * stubs both repositories proves only that the stubs agree.
     */
    @Test
    void reconciliationEndpoint_reportsTheDifferenceBetweenComputedAndEntered() {
        // Given — the engine priced the trade, and the user had typed a figure of their own
        ChargeComputation computation = userChargeService.computeAndRecord(sell("txn-1", "INFY", HOLDER, TRADE_DATE));
        transactionRepository.save(transaction("txn-1", computation.total() - 5.00));

        // When
        ResponseEntity<String> response = get("/user-charges/user/" + EMAIL + "/reconciliation", token(EMAIL));

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getBody())
                .contains("txn-1")
                .contains("\"delta\":5.0")
                .contains("\"comparable\":true")
                .contains("\"comparableCount\":1");
    }

    /**
     * A trade from before any card starts computes zero for a stated reason. Subtracting the entered
     * figure from that zero would report the engine as undercharging by the whole amount.
     */
    @Test
    void reconciliationEndpoint_leavesATradeItCouldNotPriceOutOfTheTotals() {
        // Given
        userChargeService.computeAndRecord(sell("txn-old", "INFY", HOLDER, LocalDate.of(2019, 4, 1)));
        transactionRepository.save(transaction("txn-old", 120.00));

        // When
        ResponseEntity<String> response = get("/user-charges/user/" + EMAIL + "/reconciliation", token(EMAIL));

        // Then
        assertThat(response.getBody())
                .contains("txn-old")
                .contains("NO_SCHEDULE")
                .contains("\"comparable\":false")
                .contains("\"comparableCount\":0")
                .contains("\"totalDelta\":0.0")
                .contains("\"unresolvedCount\":1");
    }

    @Test
    void reconciliationEndpoint_countsTradesTheEngineNeverSaw() {
        // Given — one priced, one that shadow recording never reached
        userChargeService.computeAndRecord(sell("txn-1", "INFY", HOLDER, TRADE_DATE));
        transactionRepository.save(transaction("txn-1", 100.00));
        transactionRepository.save(transaction("txn-unseen", 80.00));

        // When
        ResponseEntity<String> response = get("/user-charges/user/" + EMAIL + "/reconciliation", token(EMAIL));

        // Then
        assertThat(response.getBody()).contains("\"transactionsWithoutComputation\":1");
    }

    @Test
    void reconciliationEndpoint_refusesAnUnauthenticatedRequest() {
        // When
        ResponseEntity<String> response = get("/user-charges/user/" + EMAIL + "/reconciliation", null);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }


    // ------------------------------------------------------------------- harness

    private String token(String email) {
        return generateToken(email);
    }

    private String adminToken() {
        return generateToken(EMAIL, "SUPER_USER");
    }

    private ResponseEntity<String> get(String path, String token) {
        return exchange(path, HttpMethod.GET, token, null);
    }

    private ResponseEntity<String> post(String path, String token, Object body) {
        return exchange(path, HttpMethod.POST, token, body);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return false;
            }
        });

        return restTemplate.exchange(
                URI.create("http://localhost:" + RestAssured.port + path),
                method, new HttpEntity<>(body, headers), String.class);
    }

    // ------------------------------------------------------------------ fixtures

    private static ChargeContext sell(String transactionId, String stockCode, String holder, LocalDate date) {
        return sellFor(EMAIL, transactionId, stockCode, holder, date);
    }

    private static ChargeContext sellFor(String email, String transactionId, String stockCode,
                                         String holder, LocalDate date) {
        Map<AmountBasis, Double> baseAmounts = new EnumMap<>(AmountBasis.class);
        baseAmounts.put(AmountBasis.TURNOVER, 1_00_000.00);

        return new ChargeContext(email, transactionId, "ord-" + transactionId, stockCode, holder,
                BrokerName.ZERODHA, AssetType.EQUITY, TradeSegment.DELIVERY, "NSE", null,
                ChargeEvent.SELL, date, null, 100, 1000, 1, baseAmounts, List.of(), new HashMap<>());
    }

    /**
     * A cut-down equity card: brokerage, securities transaction tax, the depository charge and the
     * tax over the taxable two of them. Enough to exercise every basis that matters here without
     * the arithmetic needing a calculator to check.
     */
    private static ChargeScheduleEntity equityCard(String code, LocalDate from, LocalDate to, double brokerage) {
        ChargeScheduleEntity schedule = new ChargeScheduleEntity();
        schedule.setScheduleCode(code);
        schedule.setBrokerName(BrokerName.ZERODHA);
        schedule.setAssetType(AssetType.EQUITY);
        schedule.setSegment(TradeSegment.DELIVERY);
        schedule.setStartDate(from);
        schedule.setEndDate(to);
        schedule.setStatus(EntityStatus.ACTIVE);
        schedule.setSourceUrl("https://example.test/charges");

        ChargeRule brokerageRule = flatRule("BROKERAGE", ChargeCategory.BROKERAGE, brokerage, 10);

        ChargeRule stt = new ChargeRule();
        stt.setCode("STT");
        stt.setDisplayName("Securities Transaction Tax");
        stt.setCategory(ChargeCategory.STATUTORY);
        stt.setBasis(ChargeBasis.TURNOVER);
        stt.setAmountBasis(AmountBasis.TURNOVER);
        stt.setSide(ChargeSide.BOTH);
        stt.setEvents(Set.of(ChargeEvent.BUY, ChargeEvent.SELL));
        stt.setRate(0.1);
        stt.setRounding(RoundingPolicy.HALF_UP_0);
        stt.setTaxable(false);
        stt.setActive(true);
        stt.setOrder(20);

        ChargeRule dp = new ChargeRule();
        dp.setCode("DP");
        dp.setDisplayName("Depository participant charges");
        dp.setCategory(ChargeCategory.DEPOSITORY);
        dp.setBasis(ChargeBasis.SCOPED_FLAT);
        dp.setSide(ChargeSide.SELL);
        dp.setEvents(Set.of(ChargeEvent.SELL));
        dp.setFlatAmount(13.5);
        dp.setDedupeScope(DedupeScope.PER_SCRIP_PER_DAY);
        dp.setRounding(RoundingPolicy.HALF_UP_2);
        dp.setTaxable(true);
        dp.setActive(true);
        dp.setOrder(70);

        ChargeRule gst = new ChargeRule();
        gst.setCode("GST");
        gst.setDisplayName("Goods and Services Tax");
        gst.setCategory(ChargeCategory.TAX);
        gst.setBasis(ChargeBasis.DERIVED);
        gst.setSide(ChargeSide.BOTH);
        gst.setEvents(Set.of(ChargeEvent.BUY, ChargeEvent.SELL));
        gst.setRate(18.0);
        gst.setBaseCodes(List.of("BROKERAGE", "DP"));
        gst.setRounding(RoundingPolicy.HALF_UP_2);
        gst.setTaxable(false);
        gst.setActive(true);
        gst.setOrder(100);

        schedule.setRules(new java.util.ArrayList<>(List.of(brokerageRule, stt, dp, gst)));
        return schedule;
    }

    /** The shipped mutual fund card's shape: it expects a profile, and taxes equity-oriented ones. */
    private static ChargeScheduleEntity mutualFundCard() {
        ChargeScheduleEntity schedule = new ChargeScheduleEntity();
        schedule.setScheduleCode("IT_MF_2025");
        schedule.setBrokerName(BrokerName.ZERODHA);
        schedule.setAssetType(AssetType.MUTUAL_FUND);
        schedule.setStartDate(LocalDate.of(2025, 1, 1));
        schedule.setStatus(EntityStatus.ACTIVE);
        schedule.setSourceUrl("https://example.test/charges");
        schedule.setRequiresInstrumentProfile(true);

        ChargeRule stt = new ChargeRule();
        stt.setCode("STT");
        stt.setDisplayName("Securities Transaction Tax");
        stt.setCategory(ChargeCategory.STATUTORY);
        stt.setBasis(ChargeBasis.TURNOVER);
        stt.setAmountBasis(AmountBasis.TURNOVER);
        stt.setSide(ChargeSide.SELL);
        stt.setEvents(Set.of(ChargeEvent.SELL));
        stt.setEligibility("#equityOriented == true");
        stt.setRate(0.001);
        stt.setRounding(RoundingPolicy.HALF_UP_0);
        stt.setTaxable(false);
        stt.setActive(true);
        stt.setOrder(20);

        schedule.setRules(new java.util.ArrayList<>(List.of(stt)));
        return schedule;
    }

    /** An exit load tapering to nil after a year, which is the shipped flexi-cap profile's shape. */
    private static ChargeInstrumentEntity gradedExitLoadProfile() {
        ChargeInstrumentEntity profile = new ChargeInstrumentEntity();
        profile.setStockCode("PARAGPARIKHFLEXICAP");
        profile.setAssetType(AssetType.MUTUAL_FUND);
        profile.setFundCategory(FundCategory.EQUITY);
        profile.setEquityOriented(true);
        profile.setPlanType(PlanType.DIRECT);
        profile.setStartDate(LocalDate.of(2025, 1, 1));
        profile.setStatus(EntityStatus.ACTIVE);
        profile.setSourceUrl("https://example.test/scheme");

        ChargeRule exitLoad = new ChargeRule();
        exitLoad.setCode("EXIT_LOAD");
        exitLoad.setDisplayName("Exit load");
        exitLoad.setCategory(ChargeCategory.FUND);
        exitLoad.setBasis(ChargeBasis.SLAB);
        exitLoad.setSlabBandBasis(SlabBandBasis.HOLDING_DAYS);
        exitLoad.setAmountBasis(AmountBasis.TURNOVER);
        exitLoad.setSide(ChargeSide.SELL);
        exitLoad.setEvents(Set.of(ChargeEvent.SELL));
        exitLoad.setPerLot(true);
        exitLoad.setSlabs(List.of(new ChargeSlab(0.0, 365.0, 1.0, null),
                new ChargeSlab(365.0, null, 0.0, null)));
        exitLoad.setRounding(RoundingPolicy.HALF_UP_2);
        exitLoad.setTaxable(false);
        exitLoad.setActive(true);
        exitLoad.setOrder(15);

        profile.setRules(new java.util.ArrayList<>(List.of(exitLoad)));
        return profile;
    }

    private static ChargeContext redemption(List<LotSlice> lots) {
        Map<AmountBasis, Double> baseAmounts = new EnumMap<>(AmountBasis.class);
        baseAmounts.put(AmountBasis.TURNOVER, 1_00_000.00);

        return new ChargeContext(EMAIL, "txn-mf", "ord-mf", "PARAGPARIKHFLEXICAP", HOLDER,
                BrokerName.ZERODHA, AssetType.MUTUAL_FUND, null, null, null,
                ChargeEvent.SELL, TRADE_DATE, null, 1000, 100, 1, baseAmounts, lots, new HashMap<>());
    }

    private static ChargeScheduleEntity amcCard() {
        ChargeScheduleEntity schedule = new ChargeScheduleEntity();
        schedule.setScheduleCode("IT_AMC_2025");
        schedule.setBrokerName(BrokerName.ZERODHA);
        schedule.setStartDate(LocalDate.of(2025, 1, 1));
        schedule.setStatus(EntityStatus.ACTIVE);
        schedule.setSourceUrl("https://example.test/charges");

        ChargeRule amc = flatRule("AMC", ChargeCategory.SUBSCRIPTION, 300.00, 10);
        amc.setEvents(Set.of(ChargeEvent.AMC_CYCLE));

        ChargeRule gst = new ChargeRule();
        gst.setCode("GST");
        gst.setDisplayName("Goods and Services Tax");
        gst.setCategory(ChargeCategory.TAX);
        gst.setBasis(ChargeBasis.DERIVED);
        gst.setSide(ChargeSide.BOTH);
        gst.setEvents(Set.of(ChargeEvent.AMC_CYCLE));
        gst.setRate(18.0);
        gst.setBaseCodes(List.of("AMC"));
        gst.setRounding(RoundingPolicy.HALF_UP_2);
        gst.setTaxable(false);
        gst.setActive(true);
        gst.setOrder(100);

        schedule.setRules(new java.util.ArrayList<>(List.of(amc, gst)));
        return schedule;
    }

    private static ChargeRule flatRule(String code, ChargeCategory category, double amount, int order) {
        ChargeRule rule = new ChargeRule();
        rule.setCode(code);
        rule.setDisplayName(code);
        rule.setCategory(category);
        rule.setBasis(ChargeBasis.FLAT);
        rule.setSide(ChargeSide.BOTH);
        rule.setEvents(Set.of(ChargeEvent.BUY, ChargeEvent.SELL));
        rule.setFlatAmount(amount);
        rule.setRounding(RoundingPolicy.HALF_UP_2);
        rule.setTaxable(true);
        rule.setActive(true);
        rule.setOrder(order);
        return rule;
    }

    private static TransactionEntity transaction(String id, double enteredCharges) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(id);
        transaction.setEmail(EMAIL);
        transaction.setStockCode("INFY");
        transaction.setBrokerName(BrokerName.ZERODHA);
        transaction.setAccountHolder(HOLDER);
        transaction.setTransactionDate(TRADE_DATE);
        transaction.setBrokerCharges(enteredCharges);
        return transaction;
    }

    private static TransactionEntity trade(String id, TransactionType type, double quantity,
                                           double price, LocalDate date, double enteredCharges) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(id);
        transaction.setEmail(EMAIL);
        transaction.setStockCode("INFY");
        transaction.setExchangeName("NSE");
        transaction.setBrokerName(BrokerName.ZERODHA);
        transaction.setAccountHolder(HOLDER);
        transaction.setAssetType(AssetType.EQUITY);
        transaction.setTransactionType(type);
        transaction.setQuantity(quantity);
        transaction.setPrice(price);
        transaction.setTransactionDate(date);
        transaction.setBrokerCharges(enteredCharges);
        transaction.setStatus(TransactionStatus.PROCESSED);
        return transaction;
    }

    private static ChargeAccountEntity account() {
        ChargeAccountEntity account = new ChargeAccountEntity();
        account.setEmail(EMAIL);
        account.setAccountHolder(HOLDER);
        account.setBrokerName(BrokerName.ZERODHA);
        account.setDematAccountId("DEMAT-1");
        account.setOpenedOn(LocalDate.of(2024, 1, 1));
        account.setAmcFrequency(AmcChargeFrequency.ANNUALLY);
        account.setStatus(EntityStatus.ACTIVE);
        return account;
    }
}
