package com.thiru.wealthlens.integration;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertMoney;
import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeCategory;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeSide;
import com.thiru.wealthlens.brokercharges.dto.enums.RoundingPolicy;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.brokercharges.entity.model.ChargeSummaryReport;
import com.thiru.wealthlens.brokercharges.service.ChargeScheduleService;
import com.thiru.wealthlens.brokercharges.service.ChargeSeederService;
import com.thiru.wealthlens.brokercharges.service.UserChargeService;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The extensibility promise, defended by the build.
 *
 * <p>The claim the whole redesign rests on is that adding or repricing a charge is a data change.
 * The superseded implementation could not make it: a rate card was a fixed set of Java fields
 * repeated across three classes, and a new charge meant an eight-file change across two modules.
 *
 * <p>Every charge here is {@code SYNTHETIC_LEVY_FOR_TEST}, a code that appears in a catalogue row
 * and a rate card and <em>nowhere in Java</em>. If anyone later reaches for a switch on charge code,
 * a field per charge, or an enum of the ones we happen to ship, these tests fail — which is the
 * only durable way to state a design promise.
 */
class ChargeExtensibilityTest extends AbstractIntegrationTest {

    private static final String EMAIL = "extensibility@example.com";
    private static final String SYNTHETIC_CODE = "SYNTHETIC_LEVY_FOR_TEST";
    private static final LocalDate TRADE_DATE = LocalDate.of(2025, 6, 2);

    @Autowired
    private ChargeScheduleService chargeScheduleService;

    @Autowired
    private ChargeSeederService chargeSeederService;

    @Autowired
    private UserChargeService userChargeService;

    /**
     * Seeds the shipped catalogue before adding the synthetic code, because the cards published here
     * also carry real ones — {@code GST} among them — and the validator rejects a rule naming a code
     * the catalogue does not hold.
     *
     * <p>This class used to rely on some earlier class having seeded, since {@code charge_catalogue}
     * is whitelisted to survive {@code cleanDatabase()}. That worked only for as long as a seeding
     * class happened to run first: seeding stopped being automatic at ADR-27, so nothing guarantees
     * it. Adding two integration classes in Chunk 10 changed the order and the dependency surfaced in
     * CI as {@code rule GST is not in the charge catalogue}. Seeding is idempotent by code, so doing
     * it here costs nothing and makes the class self-sufficient.
     */
    @BeforeEach
    void clearRateCardsAndRegisterTheSyntheticCode() {
        chargeSeederService.seed("extensibility-test");

        // The shipped cards go: this class asserts over the ones it publishes, and leaving eleven
        // more in the collection would make those assertions depend on which card resolved first.
        mongoTemplate.getCollection("charge_schedules").deleteMany(new Document());
        mongoTemplate.getCollection("charge_instruments").deleteMany(new Document());

        mongoTemplate.getCollection("charge_catalogue").insertOne(new Document()
                .append("code", SYNTHETIC_CODE)
                .append("display_name", "Synthetic levy")
                .append("category", "EXCHANGE")
                .append("status", "ACTIVE"));
    }

    /**
     * {@code charge_catalogue} is seeded reference data and deliberately survives
     * {@code cleanDatabase()}, so a code added here has to be taken back out. Left behind it would
     * be visible to every class that runs afterwards.
     */
    @AfterEach
    void removeTheSyntheticCode() {
        mongoTemplate.getCollection("charge_catalogue").deleteMany(new Document("code", SYNTHETIC_CODE));
    }

    @Test
    void aChargeThatExistsOnlyInJson_isComputed_recorded_andAggregated() {
        // Given — a catalogue entry and a rule naming it. No Java change of any kind.
        chargeScheduleService.publish(cardWith(flat(SYNTHETIC_CODE, 7.00, 10)));

        // When
        ChargeComputation computation = userChargeService.computeAndRecord(sell("txn-1", TRADE_DATE));

        ChargeSummaryReport summary = new ChargeSummaryReport();
        userChargeService.findHistory(EMAIL).forEach(charge -> summary.merge(charge.getAmountByCode()));

        // Then — all three places AC-1 promises it reaches
        assertMoney(7.00, computation.amountOf(SYNTHETIC_CODE));
        assertThat(userChargeService.findForTransaction(EMAIL, "txn-1").getAmountByCode())
                .containsEntry(SYNTHETIC_CODE, 7.00);
        assertThat(summary.getAmountByCode()).containsEntry(SYNTHETIC_CODE, 7.00);
    }

    @Test
    void aDerivedChargeCanNameASyntheticCodeInItsBase() {
        // Given — tax over a charge the engine has never heard of. This is the harder half of the
        // promise: a new charge must be usable by the rules that come after it, not merely emitted.
        ChargeRule gst = new ChargeRule();
        gst.setCode("GST");
        gst.setDisplayName("Goods and Services Tax");
        gst.setCategory(ChargeCategory.TAX);
        gst.setBasis(ChargeBasis.DERIVED);
        gst.setSide(ChargeSide.BOTH);
        gst.setEvents(Set.of(ChargeEvent.BUY, ChargeEvent.SELL));
        gst.setRate(18.0);
        gst.setBaseCodes(List.of(SYNTHETIC_CODE));
        gst.setRounding(RoundingPolicy.HALF_UP_2);
        gst.setActive(true);
        gst.setOrder(100);

        chargeScheduleService.publish(cardWith(flat(SYNTHETIC_CODE, 100.00, 10), gst));

        // When
        ChargeComputation computation = userChargeService.computeAndRecord(sell("txn-1", TRADE_DATE));

        // Then
        assertMoney(100.00, computation.amountOf(SYNTHETIC_CODE));
        assertMoney(18.00, computation.amountOf("GST"));
        assertMoney(118.00, computation.total());
    }

    @Test
    void repricingACharge_appliesTheNewRateOnlyAfterTheBoundary() {
        // Given — the same charge at two prices, in two cards, either side of 1 July
        chargeScheduleService.publish(card("SYNTH_H1", LocalDate.of(2025, 1, 1), flat(SYNTHETIC_CODE, 7.00, 10)));
        chargeScheduleService.publish(card("SYNTH_H2", LocalDate.of(2025, 7, 1), flat(SYNTHETIC_CODE, 9.50, 10)));

        // When
        ChargeComputation before = userChargeService.computeAndRecord(sell("txn-june", LocalDate.of(2025, 6, 30)));
        ChargeComputation after = userChargeService.computeAndRecord(sell("txn-july", LocalDate.of(2025, 7, 1)));

        // Then — a repricing is a published card, not a deployment, and it does not rewrite history
        assertThat(before.scheduleCode()).isEqualTo("SYNTH_H1");
        assertMoney(7.00, before.amountOf(SYNTHETIC_CODE));
        assertThat(after.scheduleCode()).isEqualTo("SYNTH_H2");
        assertMoney(9.50, after.amountOf(SYNTHETIC_CODE));
    }

    // ------------------------------------------------------------------ fixtures

    private static ChargeScheduleEntity cardWith(ChargeRule... rules) {
        return card("SYNTH_CARD", LocalDate.of(2025, 1, 1), rules);
    }

    private static ChargeScheduleEntity card(String code, LocalDate from, ChargeRule... rules) {
        ChargeScheduleEntity schedule = new ChargeScheduleEntity();
        schedule.setScheduleCode(code);
        schedule.setBrokerName(BrokerName.ZERODHA);
        schedule.setAssetType(AssetType.EQUITY);
        schedule.setSegment(TradeSegment.DELIVERY);
        schedule.setStartDate(from);
        schedule.setStatus(EntityStatus.ACTIVE);
        schedule.setSourceUrl("https://example.test/charges");
        schedule.setRules(new ArrayList<>(List.of(rules)));
        return schedule;
    }

    private static ChargeRule flat(String code, double amount, int order) {
        ChargeRule rule = new ChargeRule();
        rule.setCode(code);
        rule.setDisplayName(code);
        rule.setCategory(ChargeCategory.EXCHANGE);
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

    private static ChargeContext sell(String transactionId, LocalDate date) {
        Map<AmountBasis, Double> baseAmounts = new EnumMap<>(AmountBasis.class);
        baseAmounts.put(AmountBasis.TURNOVER, 1_00_000.00);

        return new ChargeContext(EMAIL, transactionId, "ord-" + transactionId, "INFY", "self",
                BrokerName.ZERODHA, AssetType.EQUITY, TradeSegment.DELIVERY, "NSE", null,
                ChargeEvent.SELL, date, null, 100, 1000, 1, baseAmounts, List.of(), new HashMap<>());
    }
}
