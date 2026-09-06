package com.thiru.wealthlens.integration;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertMoney;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeCategory;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeSide;
import com.thiru.wealthlens.brokercharges.dto.enums.TradeSegment;
import com.thiru.wealthlens.brokercharges.entity.ChargeInstrumentEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.brokercharges.repository.ChargeInstrumentRepository;
import com.thiru.wealthlens.brokercharges.repository.ChargeScheduleRepository;
import com.thiru.wealthlens.brokercharges.repository.UserChargeRepository;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifies the charge documents map and the repository queries execute against a real MongoDB.
 *
 * <p>Written now rather than with the rest of the integration tier, because two of these queries
 * are the sort that fail silently: a parameter substituted into a field name, and a status
 * predicate whose whole purpose is to keep historical rate cards resolvable.
 */
class ChargeRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "charges@example.com";
    private static final LocalDate IN_2024 = LocalDate.of(2024, 6, 15);

    @Autowired
    private ChargeScheduleRepository scheduleRepository;

    @Autowired
    private ChargeInstrumentRepository instrumentRepository;

    @Autowired
    private UserChargeRepository userChargeRepository;

    /**
     * The application seeds its shipped rate cards at startup, and this class asserts over exactly
     * the cards it writes. Clearing first makes that true whatever ran before it.
     */
    @BeforeEach
    void clearShippedRateCards() {
        mongoTemplate.getCollection("charge_schedules").deleteMany(new org.bson.Document());
        mongoTemplate.getCollection("charge_instruments").deleteMany(new org.bson.Document());
    }

    @Test
    void schedule_roundTripsWithItsEmbeddedRules() {
        // Given
        ChargeScheduleEntity schedule = schedule("RT_CARD", LocalDate.of(2024, 1, 1), null);
        schedule.setRules(List.of(sttRule(), gstRule()));

        // When
        scheduleRepository.save(schedule);
        ChargeScheduleEntity found = scheduleRepository.findByScheduleCode("RT_CARD").orElseThrow();

        // Then
        assertThat(found.getRules()).hasSize(2);
        ChargeRule gst = found.getRules().get(1);
        assertThat(gst.getBaseCodes()).containsExactly("BROKERAGE", "EXCHANGE_TXN");
        assertThat(gst.getBasis()).isEqualTo(ChargeBasis.DERIVED);
        assertThat(gst.getEvents()).containsExactlyInAnyOrder(ChargeEvent.BUY, ChargeEvent.SELL);
        assertMoney(18.0, gst.getRate());
    }

    @Test
    void findCandidates_whenCardWasSupersededButDateFallsInItsWindow_stillResolves() {
        // Given — a 2024 card closed when its 2025 successor was published.
        // Uploading a past quarter long after the rates changed is the normal case.
        scheduleRepository.save(schedule("OLD_CARD", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)));
        scheduleRepository.save(schedule("NEW_CARD", LocalDate.of(2025, 1, 1), null));

        // When
        List<ChargeScheduleEntity> candidates = scheduleRepository.findCandidates(BrokerName.ZERODHA, IN_2024);

        // Then — the superseded card prices the backdated trade, not the current one
        assertThat(candidates).extracting(ChargeScheduleEntity::getScheduleCode).containsExactly("OLD_CARD");
    }

    @Test
    void findCandidates_whenStatusIsSuperseded_stillResolves() {
        // Given — a maintainer set SUPERSEDED believing it correct. The predicate is != INACTIVE
        // precisely so that this does not silently charge zero.
        ChargeScheduleEntity old = schedule("MARKED_CARD", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
        old.setStatus(EntityStatus.SUPERSEDED);
        scheduleRepository.save(old);

        // When / Then
        assertThat(scheduleRepository.findCandidates(BrokerName.ZERODHA, IN_2024)).hasSize(1);
    }

    @Test
    void findCandidates_whenStatusIsInactive_resolvesNothing() {
        // Given — INACTIVE means entered in error, so unusable for any date, window or not
        ChargeScheduleEntity retracted = schedule("BAD_CARD", LocalDate.of(2024, 1, 1), null);
        retracted.setStatus(EntityStatus.INACTIVE);
        scheduleRepository.save(retracted);

        // When / Then
        assertThat(scheduleRepository.findCandidates(BrokerName.ZERODHA, IN_2024)).isEmpty();
    }

    @Test
    void findCandidates_whenEndDateIsNull_treatsCardAsOpenEnded() {
        // Given
        scheduleRepository.save(schedule("OPEN_CARD", LocalDate.of(2024, 1, 1), null));

        // When / Then
        assertThat(scheduleRepository.findCandidates(BrokerName.ZERODHA, LocalDate.of(2030, 1, 1))).hasSize(1);
    }

    @Test
    void findCandidates_whenDatePrecedesEveryCard_resolvesNothing() {
        // Given
        scheduleRepository.save(schedule("LATER_CARD", LocalDate.of(2024, 1, 1), null));

        // When / Then
        assertThat(scheduleRepository.findCandidates(BrokerName.ZERODHA, LocalDate.of(2019, 1, 1))).isEmpty();
    }

    @Test
    void findByVerifiedOnIsNull_findsCardsWithUnverifiedRates() {
        // Given
        scheduleRepository.save(schedule("UNVERIFIED", LocalDate.of(2024, 1, 1), null));
        ChargeScheduleEntity verified = schedule("VERIFIED", LocalDate.of(2024, 1, 1), null);
        verified.setBrokerName(BrokerName.UPSTOX);
        verified.setVerifiedOn(LocalDate.of(2024, 1, 1));
        scheduleRepository.save(verified);

        // When / Then
        assertThat(scheduleRepository.findByVerifiedOnIsNull())
                .extracting(ChargeScheduleEntity::getScheduleCode).containsExactly("UNVERIFIED");
    }

    @Test
    void instrumentProfile_resolvesTheVersionInForceOnTheTradeDate() {
        // Given — an AMC revised the exit load at the start of 2025
        instrumentRepository.save(instrument("HDFCFLEXI", LocalDate.of(2023, 1, 1), LocalDate.of(2024, 12, 31)));
        instrumentRepository.save(instrument("HDFCFLEXI", LocalDate.of(2025, 1, 1), null));

        // When
        List<ChargeInstrumentEntity> candidates = instrumentRepository.findCandidates("HDFCFLEXI", IN_2024);

        // Then — a redemption backdated into the old window uses the load in force then
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getEndDate()).isEqualTo(LocalDate.of(2024, 12, 31));
        assertThat(instrumentRepository.findOpenProfile("HDFCFLEXI").orElseThrow().getEndDate()).isNull();
    }

    @Test
    void existsChargeForScripOnDate_whenSameHolderAlreadyCharged_isTrue() {
        // Given
        userChargeRepository.save(userCharge("txn-1", "self", "RELIANCE", Map.of("DP", 13.5)));

        // When / Then
        assertThat(userChargeRepository.existsChargeForScripOnDate(
                EMAIL, "self", BrokerName.ZERODHA, "RELIANCE", IN_2024, "DP")).isTrue();
    }

    @Test
    void existsChargeForScripOnDate_whenDifferentHolder_isFalse() {
        // Given — a depository charge is levied per demat account, so another holder's charge
        // must not suppress this one
        userChargeRepository.save(userCharge("txn-1", "self", "RELIANCE", Map.of("DP", 13.5)));

        // When / Then
        assertThat(userChargeRepository.existsChargeForScripOnDate(
                EMAIL, "spouse", BrokerName.ZERODHA, "RELIANCE", IN_2024, "DP")).isFalse();
    }

    @Test
    void existsChargeForScripOnDate_whenTheChargeCodeIsAbsent_isFalse() {
        // Given — a sell that attracted brokerage but no depository charge
        userChargeRepository.save(userCharge("txn-1", "self", "RELIANCE", Map.of("BROKERAGE", 20.0)));

        // When / Then — proves the parameter substituted into the field path actually binds
        assertThat(userChargeRepository.existsChargeForScripOnDate(
                EMAIL, "self", BrokerName.ZERODHA, "RELIANCE", IN_2024, "DP")).isFalse();
    }

    @Test
    void userCharge_transactionIdIsUnique() {
        // Given
        userChargeRepository.save(userCharge("txn-dup", "self", "RELIANCE", Map.of("DP", 13.5)));

        // When / Then — the unique index is what makes a re-uploaded quarter idempotent
        assertThatThrownBy(() -> userChargeRepository.save(userCharge("txn-dup", "self", "TCS", Map.of("DP", 13.5))))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void findByEmailAndResolutionIn_findsRowsWhoseChargesCouldNotBeAssessed() {
        // Given
        userChargeRepository.save(userCharge("txn-ok", "self", "RELIANCE", Map.of("DP", 13.5)));
        UserChargeEntity gap = userCharge("txn-gap", "self", "TCS", Map.of());
        gap.setResolution(ChargeResolution.NO_SCHEDULE);
        userChargeRepository.save(gap);

        // When / Then — a missing charge is queryable, not merely logged
        assertThat(userChargeRepository.findByEmailAndResolutionIn(
                EMAIL, List.of(ChargeResolution.NO_SCHEDULE, ChargeResolution.NO_INSTRUMENT_PROFILE)))
                .extracting(UserChargeEntity::getTransactionId).containsExactly("txn-gap");
    }

    @Test
    void findFirstByEmailOrderByTransactionDateDesc_findsTheLatestRecordedTrade() {
        // Given — used to detect a batch reaching back before what is already recorded
        userChargeRepository.save(userCharge("txn-old", "self", "RELIANCE", Map.of()));
        UserChargeEntity later = userCharge("txn-new", "self", "TCS", Map.of());
        later.setTransactionDate(LocalDate.of(2025, 3, 1));
        userChargeRepository.save(later);

        // When / Then
        assertThat(userChargeRepository.findFirstByEmailOrderByTransactionDateDesc(EMAIL).orElseThrow()
                .getTransactionId()).isEqualTo("txn-new");
    }

    private static ChargeScheduleEntity schedule(String code, LocalDate from, LocalDate to) {
        ChargeScheduleEntity schedule = new ChargeScheduleEntity();
        schedule.setScheduleCode(code);
        schedule.setBrokerName(BrokerName.ZERODHA);
        schedule.setAssetType(AssetType.EQUITY);
        schedule.setSegment(TradeSegment.DELIVERY);
        schedule.setStartDate(from);
        schedule.setEndDate(to);
        schedule.setStatus(EntityStatus.ACTIVE);
        schedule.setCurrency("INR");
        return schedule;
    }

    private static ChargeInstrumentEntity instrument(String stockCode, LocalDate from, LocalDate to) {
        ChargeInstrumentEntity instrument = new ChargeInstrumentEntity();
        instrument.setStockCode(stockCode);
        instrument.setAssetType(AssetType.MUTUAL_FUND);
        instrument.setEquityOriented(true);
        instrument.setStartDate(from);
        instrument.setEndDate(to);
        instrument.setStatus(EntityStatus.ACTIVE);
        return instrument;
    }

    private static UserChargeEntity userCharge(String transactionId, String holder, String stockCode,
                                               Map<String, Double> amountByCode) {
        UserChargeEntity charge = new UserChargeEntity();
        charge.setEmail(EMAIL);
        charge.setAccountHolder(holder);
        charge.setBrokerName(BrokerName.ZERODHA);
        charge.setAssetType(AssetType.EQUITY);
        charge.setStockCode(stockCode);
        charge.setTransactionId(transactionId);
        charge.setEvent(ChargeEvent.SELL);
        charge.setTransactionDate(IN_2024);
        charge.setResolution(ChargeResolution.RESOLVED);
        charge.setAmountByCode(amountByCode);
        charge.setTotalCharges(amountByCode.values().stream().mapToDouble(Double::doubleValue).sum());
        return charge;
    }

    private static ChargeRule sttRule() {
        ChargeRule rule = new ChargeRule();
        rule.setCode("STT");
        rule.setCategory(ChargeCategory.STATUTORY);
        rule.setBasis(ChargeBasis.TURNOVER);
        rule.setSide(ChargeSide.BOTH);
        rule.setEvents(Set.of(ChargeEvent.BUY, ChargeEvent.SELL));
        rule.setRate(0.1);
        rule.setOrder(20);
        rule.setActive(true);
        return rule;
    }

    private static ChargeRule gstRule() {
        ChargeRule rule = new ChargeRule();
        rule.setCode("GST");
        rule.setCategory(ChargeCategory.TAX);
        rule.setBasis(ChargeBasis.DERIVED);
        rule.setSide(ChargeSide.BOTH);
        rule.setEvents(Set.of(ChargeEvent.BUY, ChargeEvent.SELL));
        rule.setRate(18.0);
        rule.setBaseCodes(List.of("BROKERAGE", "EXCHANGE_TXN"));
        rule.setOrder(100);
        rule.setActive(true);
        return rule;
    }
}
