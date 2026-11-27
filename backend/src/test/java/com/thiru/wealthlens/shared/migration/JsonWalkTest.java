package com.thiru.wealthlens.shared.migration;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertBreakdown;
import static com.thiru.wealthlens.testsupport.MoneyAssert.assertMoney;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeCategory;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import com.thiru.wealthlens.brokercharges.entity.ChargeRuleListConverter;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeSlab;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.portfolio.dto.OrderTimeQuantity;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.RealisedProfitsConverter;
import com.thiru.wealthlens.portfolio.entity.model.FinancialReport;
import com.thiru.wealthlens.portfolio.entity.model.FortnightReport;
import com.thiru.wealthlens.portfolio.entity.model.MonthlyReport;
import com.thiru.wealthlens.portfolio.entity.model.RealisedProfits;
import com.thiru.wealthlens.portfolio.entity.model.ReportModel;
import com.thiru.wealthlens.taxplanning.workspace.DraftStaleness;
import com.thiru.wealthlens.taxplanning.workspace.DraftStalenessConverter;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.mapping.Field;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class JsonWalkTest {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");

    /** A type holding a time EmbeddedTimes does not classify. */
    record Stamp(@Field("at") LocalDateTime at) {
    }

    @Test
    void json_whenChargeRulesAsStored_isWhatTheApplicationsConverterReads() throws NoSuchFieldException {
        // Given — a rule as m1.6.1 stored it: @Field names, enums as text, a whole number where the field is a Double
        Document rule = new Document("code", "STT").append("display_name", "Securities transaction tax")
                .append("category", "STATUTORY").append("basis", "SLAB").append("events", List.of("SELL", "BUY"))
                .append("slabs", List.of(new Document("from_value", 0).append("to_value", 100.5).append("rate", 0.1)))
                .append("order", 2).append("taxable", false).append("active", true).append("per_lot", false);

        // When
        String json = JsonWalk.json(List.of(rule), rulesType(), KOLKATA, "charge_schedules schedule-1, rules");

        // Then — the rule the source describes, field by field
        ChargeRule expected = new ChargeRule();
        expected.setCode("STT");
        expected.setDisplayName("Securities transaction tax");
        expected.setCategory(ChargeCategory.STATUTORY);
        expected.setBasis(ChargeBasis.SLAB);
        expected.setEvents(Set.of(ChargeEvent.SELL, ChargeEvent.BUY));
        expected.setSlabs(List.of(new ChargeSlab(0.0, 100.5, 0.1, null)));
        expected.setOrder(2);
        expected.setActive(true);
        assertThat(new ChargeRuleListConverter().convertToEntityAttribute(json)).containsExactly(expected);
    }

    @Test
    void json_whenAStoredKeyHasNoField_isRefusedNamingIt() {
        // Given
        Document rule = new Document("code", "STT").append("legacy_flag", true);

        // When / Then
        assertThatThrownBy(() -> JsonWalk.json(List.of(rule), rulesType(), KOLKATA, "charge_schedules schedule-1, rules"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("charge_schedules schedule-1, rules: ChargeRule has no field for the stored key legacy_flag");
    }

    @Test
    void json_whenAnEnumValueIsNotAConstant_isRefused() {
        // Given
        Document rule = new Document("code", "STT").append("category", "LEVY");

        // When / Then
        assertThatThrownBy(() -> JsonWalk.json(List.of(rule), rulesType(), KOLKATA, "charge_schedules schedule-1, rules"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("charge_schedules schedule-1, rules: LEVY is not a ChargeCategory");
    }

    @Test
    void json_whenAUtcHeldTimeWasStoredThroughKolkata_isThatUtcTime() {
        // Given — StalenessSweep held context.nowUtc() = 2026-02-01T00:00 UTC; Spring Data stored it as Kolkata's
        // midnight, 18:30Z (IST = UTC + 05:30, D-958)
        Document staleness = new Document("since", Date.from(Instant.parse("2026-01-31T18:30:00Z"))).append("receipt", "receipt-1")
                .append("computed_under", List.of("pack-1")).append("current", List.of("pack-2"))
                .append("changed_rules", List.of("RS-1")).append("rule_diff_available", true);

        // When
        String json = JsonWalk.json(staleness, DraftStaleness.class, KOLKATA, "tax_filing_drafts draft-1, staleness");

        // Then
        assertThat(new DraftStalenessConverter().convertToEntityAttribute(json)).isEqualTo(new DraftStaleness(
                LocalDateTime.of(2026, 2, 1, 0, 0), "receipt-1", List.of("pack-1"), List.of("pack-2"), List.of("RS-1"), true));
    }

    @Test
    void json_whenATimeNoEntryClassifies_isRefused() {
        // Given
        Document stamp = new Document("at", Date.from(Instant.parse("2026-01-31T18:30:00Z")));

        // When / Then
        assertThatThrownBy(() -> JsonWalk.json(stamp, Stamp.class, KOLKATA, "somewhere 1, stamp"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("somewhere 1, stamp: no entry in EmbeddedTimes says how " + Stamp.class.getName() + ".at was held");
    }

    @Test
    void json_whenACorporateActionCopyAsStored_carriesItsCiphertextAndItsAuditTimeInUtc() throws NoSuchFieldException {
        // Given — a copy as m1.6.1 stored it: CorporateActionEntity's @Field names (snake_case; the class carries none now),
        // its id under _id (MappingMongoConverter, checked at build), its audit actor sealed, its audit time local-held:
        // auditing held Kolkata's 2026-02-01T00:00, stored as its true instant 18:30Z (IST = UTC + 05:30, D-958)
        Document copy = new Document("_id", "ca-1").append("stock_code", "INFY").append("type", "BONUS")
                .append("record_date", Date.from(Instant.parse("2026-01-10T00:00:00Z"))).append("priority", 1)
                .append("demerger_detail", new Document("demerger_ratio", "1:1")
                        .append("demerger_stocks", List.of(new Document("stock_code", "NEW1").append("stock_name", "New One"))))
                .append("audit_metadata", new Document("createdBy", "sealed-actor")
                        .append("createdAt", Date.from(Instant.parse("2026-01-31T18:30:00Z"))));

        // When
        String json = JsonWalk.json(List.of(copy), AssetEntity.class.getDeclaredField("corporateActions").getGenericType(), KOLKATA,
                "assets asset-1, corporate_actions");

        // Then — read without the converter, so the stored ciphertext is seen as it is
        CorporateActionEntity read = JsonMapper.builder().build()
                .readValue(json, new TypeReference<List<CorporateActionEntity>>() { }).getFirst();
        assertThat(read.getId()).isEqualTo("ca-1");
        assertThat(read.getStockCode()).isEqualTo("INFY");
        assertThat(read.getRecordDate()).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(read.getDemergerDetail().getDemergerStocks()).singleElement()
                .satisfies(stock -> assertThat(stock.getStockCode()).isEqualTo("NEW1"));
        assertThat(read.getAuditMetadata().getCreatedBy()).isEqualTo("sealed-actor");
        assertThat(read.getAuditMetadata().getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 31, 18, 30));
    }

    @Test
    void json_whenOrderTimesStoredThroughKolkata_areTheUtcTimesTheApplicationHeld() throws NoSuchFieldException {
        // Given — PortfolioService held 2026-01-15T05:00 UTC (orderExecutionDateTime(), atUtc); stored through Kolkata as
        // 05:00 IST, 2026-01-14T23:30Z; the quantity a whole number stored as an int
        Document orderTime = new Document("quantity", 5).append("orderExecutionTime", Date.from(Instant.parse("2026-01-14T23:30:00Z")))
                .append("timezoneId", "Asia/Kolkata");

        // When
        String json = JsonWalk.json(List.of(orderTime), AssetEntity.class.getDeclaredField("orderTimeQuantities").getGenericType(),
                KOLKATA, "assets asset-1, order_time_quantities");

        // Then
        OrderTimeQuantity read = JsonMapper.builder().build()
                .readValue(json, new TypeReference<List<OrderTimeQuantity>>() { }).getFirst();
        assertThat(read.getOrderExecutionTime()).isEqualTo(LocalDateTime.of(2026, 1, 15, 5, 0));
        assertThat(read.getQuantity()).isEqualTo(5.0);
    }

    @Test
    void json_whenTheProfitTreeAsStored_isTheTreeTheApplicationsConverterReads() {
        // Given — a P&L tree as m1.6.1 stored it: capital_gains, first_half_report, second_half_report are its former @Field
        // names; RealisedProfits' time local-held (LocalDateTime.now(), Kolkata's 2026-02-01T00:00 stored as 18:30Z); the charge
        // summary's time UTC-held (TLocalDateTime.now() = 2026-02-01T00:00 UTC, stored through Kolkata as 18:30Z; D-958)
        Date kolkataMidnight = Date.from(Instant.parse("2026-01-31T18:30:00Z"));
        Document fortnight = new Document("purchase_amount", 1000.0).append("sell_amount", 1200.0).append("profit", 200.0)
                .append("misc_charges", 0.0).append("brokerage", 20.0);
        Document empty = new Document("purchase_amount", 0.0).append("sell_amount", 0.0).append("profit", 0.0)
                .append("misc_charges", 0.0).append("brokerage", 0.0);
        Document gains = new Document(fortnight).append("capital_gains", new Document("JANUARY", new Document(fortnight)
                .append("month", "JANUARY").append("first_half_report", fortnight).append("second_half_report", empty)));
        Document charges = new Document("amount_by_code", new Document("BROKERAGE", 20.0)).append("total_charges", 20.0);
        Document stored = new Document("gains_by_asset_and_segment", new Document("EQUITY", new Document("DELIVERY", gains)))
                .append("yearly_charge_summary", new Document(charges).append("last_updated_time", kolkataMidnight)
                        .append("monthly_report", new Document("JANUARY", new Document(charges).append("month", "JANUARY")
                                .append("first_half_charges", charges)
                                .append("second_half_charges", new Document("amount_by_code", new Document()).append("total_charges", 0.0)))))
                .append("last_updated_time", kolkataMidnight);

        // When
        String json = JsonWalk.json(stored, RealisedProfits.class, KOLKATA, "profit_and_loss pnl-1, realised_profits");

        // Then
        RealisedProfits read = new RealisedProfitsConverter().convertToEntityAttribute(json);
        FinancialReport report = read.getGainsByAssetAndSegment().get(AssetType.EQUITY).get(TradeSegment.DELIVERY);
        assertMoney(200.0, report.getProfit());
        MonthlyReport january = report.getMonthlyReport().get(Month.JANUARY);
        assertMoney(1200.0, january.getFirstFortnightReport().getSellAmount());
        assertMoney(0.0, january.getSecondFortnightReport().getSellAmount());
        assertBreakdown(Map.of("BROKERAGE", 20.0),
                read.getYearlyChargeSummary().getMonthlyReport().get(Month.JANUARY).getFirstHalfCharges().getAmountByCode());
        assertThat(read.getLastUpdatedTime()).isEqualTo(LocalDateTime.of(2026, 1, 31, 18, 30));
        assertThat(read.getYearlyChargeSummary().getLastUpdatedTime()).isEqualTo(LocalDateTime.of(2026, 2, 1, 0, 0));
    }

    @Test
    void json_whenAReportHoldsATimeNothingSet_isRefused() {
        // Given — ReportModel.lastUpdatedTime only carried @LastModifiedDate, which never fires for a nested object
        Document report = new Document("profit", 1.0).append("last_updated_time", Date.from(Instant.parse("2026-01-31T18:30:00Z")));

        // When / Then
        assertThatThrownBy(() -> JsonWalk.json(report, FortnightReport.class, KOLKATA, "profit_and_loss pnl-1, report"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("profit_and_loss pnl-1, report: no entry in EmbeddedTimes says how " + ReportModel.class.getName()
                        + ".lastUpdatedTime was held");
    }

    @Test
    void json_whenAMapKeyIsNotItsEnumsConstant_isRefused() {
        // Given
        Document stored = new Document("gains_by_asset_and_segment", new Document("BONDS", new Document()));

        // When / Then
        assertThatThrownBy(() -> JsonWalk.json(stored, RealisedProfits.class, KOLKATA, "profit_and_loss pnl-1, realised_profits"))
                .isInstanceOf(IllegalStateException.class).hasMessage("profit_and_loss pnl-1, realised_profits: BONDS is not a AssetType");
    }

    private static Type rulesType() throws NoSuchFieldException {
        return ChargeScheduleEntity.class.getDeclaredField("rules").getGenericType();
    }
}
