package com.thiru.wealthlens.brokercharges.engine;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.context.LotSlice;
import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeCategory;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeSide;
import com.thiru.wealthlens.brokercharges.dto.enums.RoundingPolicy;
import com.thiru.wealthlens.brokercharges.dto.enums.TradeSegment;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared fixtures for the calculator tests.
 *
 * <p>Seven test classes need a rule and a trade that differ in one field each. Building them here
 * keeps the difference the only thing a test states, and means a change to {@code ChargeContext}
 * lands in one place rather than seven.
 */
final class ChargeFixtures {

    static final String EMAIL = "investor@example.com";
    static final String ACCOUNT_HOLDER = "self";
    static final String STOCK_CODE = "RELIANCE";
    static final LocalDate TRADE_DATE = LocalDate.of(2025, 6, 1);

    private ChargeFixtures() {
    }

    /** A rule that applies to everything, so a test only has to say what makes it special. */
    static ChargeRule rule(String code, ChargeBasis basis) {
        ChargeRule rule = new ChargeRule();
        rule.setCode(code);
        rule.setDisplayName(code);
        rule.setCategory(ChargeCategory.BROKERAGE);
        rule.setBasis(basis);
        rule.setSide(ChargeSide.BOTH);
        rule.setEvents(Set.of(ChargeEvent.BUY, ChargeEvent.SELL));
        rule.setAmountBasis(AmountBasis.TURNOVER);
        rule.setRounding(RoundingPolicy.HALF_UP_2);
        rule.setOrder(10);
        rule.setActive(true);
        return rule;
    }

    /** A sell of 100 at 1000, so turnover is 100000. */
    static ChargeContext trade() {
        return trade(100000.0);
    }

    static ChargeContext trade(double turnover) {
        Map<AmountBasis, Double> amounts = new EnumMap<>(AmountBasis.class);
        amounts.put(AmountBasis.TURNOVER, turnover);
        return trade(amounts, Map.of(), List.of());
    }

    static ChargeContext tradeWithAttributes(Map<String, Object> attributes) {
        Map<AmountBasis, Double> amounts = new EnumMap<>(AmountBasis.class);
        amounts.put(AmountBasis.TURNOVER, 100000.0);
        return trade(amounts, attributes, List.of());
    }

    static ChargeContext trade(
            Map<AmountBasis, Double> amounts, Map<String, Object> attributes, List<LotSlice> lots) {

        return new ChargeContext(
                EMAIL, "txn-1", "ord-1", STOCK_CODE, ACCOUNT_HOLDER, BrokerName.ZERODHA, AssetType.EQUITY,
                TradeSegment.DELIVERY, "NSE", null, ChargeEvent.SELL, TRADE_DATE, null,
                100, 1000, 1, amounts, lots, new HashMap<>(attributes));
    }
}
