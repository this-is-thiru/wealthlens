package com.thiru.wealthlens.brokercharges.engine;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertMoney;
import static com.thiru.wealthlens.testsupport.MoneyAssert.assertNoCharge;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.context.LotSlice;
import com.thiru.wealthlens.brokercharges.dto.enums.AggregatorType;
import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeCategory;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeRuleSource;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeSide;
import com.thiru.wealthlens.brokercharges.dto.enums.RoundingPolicy;
import com.thiru.wealthlens.brokercharges.dto.enums.TradeSegment;
import com.thiru.wealthlens.brokercharges.entity.ChargeLine;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The orchestrator: resolve, filter, sort, dispatch, apply modifiers, assemble.
 *
 * <p>Calculators are stubbed. What is under test is the sequencing around them — which rules are
 * selected, in what order they see each other's output, and the order the modifiers are applied in.
 * That order is a contract (tech-spec §5.5) rather than an implementation detail: aggregator, then
 * floor and cap, then rounding, applied once per line by the engine and never inside a calculator.
 */
@ExtendWith(MockitoExtension.class)
class ChargeEngineTest {

    private static final LocalDate TRADE_DATE = LocalDate.of(2025, 6, 1);

    @Mock
    private ChargeScheduleResolver scheduleResolver;

    // ---------------------------------------------------------------- rule selection

    @Test
    void compute_whenRuleDoesNotDeclareTheEvent_skipsIt() {
        // Given — a sell-only rule, and a buy
        ChargeRule sellOnly = rule("DP", ChargeBasis.FLAT, 10);
        sellOnly.setEvents(Set.of(ChargeEvent.SELL));
        sellOnly.setFlatAmount(13.5);
        givenSchedule(sellOnly);

        // When
        ChargeComputation computation = engine().compute(buy());

        // Then
        assertThat(computation.lines()).isEmpty();
        assertNoCharge(computation.total());
    }

    @Test
    void compute_whenRuleSideIsBuyAndTheEventIsASell_skipsIt() {
        // Given — stamp duty is levied on purchases only
        ChargeRule stampDuty = rule("STAMP_DUTY", ChargeBasis.FLAT, 10);
        stampDuty.setSide(ChargeSide.BUY);
        stampDuty.setEvents(Set.of(ChargeEvent.BUY, ChargeEvent.SELL));
        stampDuty.setFlatAmount(15.0);
        givenSchedule(stampDuty);

        // When
        ChargeComputation computation = engine().compute(sell());

        // Then
        assertThat(computation.lines()).isEmpty();
    }

    @Test
    void compute_whenRuleSideIsBoth_appliesToEitherSide() {
        // Given
        ChargeRule brokerage = flatRule("BROKERAGE", 20.0, 10);
        brokerage.setSide(ChargeSide.BOTH);
        givenSchedule(brokerage);

        // When / Then
        assertMoney("on a buy", 20.0, engine().compute(buy()).total());
        assertMoney("on a sell", 20.0, engine().compute(sell()).total());
    }

    @Test
    void compute_whenRuleIsInactive_skipsIt() {
        // Given — a rule disabled on the card rather than removed from it
        ChargeRule retired = flatRule("CALL_AND_TRADE", 50.0, 10);
        retired.setActive(false);
        givenSchedule(retired);

        // When
        ChargeComputation computation = engine().compute(buy());

        // Then
        assertThat(computation.lines()).isEmpty();
    }

    @Test
    void compute_whenEligibilityPredicateIsFalse_emitsNoLine() {
        // Given — a charge conditioned on a fact this trade does not carry
        ChargeRule conditional = flatRule("EXIT_LOAD", 100.0, 10);
        conditional.setEligibility("#fundCategory == 'DEBT'");
        givenSchedule(conditional);

        // When — the context says EQUITY
        ChargeComputation computation = engine().compute(
                contextWith(ChargeEvent.SELL, null, Map.of("fundCategory", "EQUITY")));

        // Then
        assertThat(computation.lines()).isEmpty();
    }

    @Test
    void compute_whenEligibilityPredicateIsTrue_emitsTheLine() {
        // Given
        ChargeRule conditional = flatRule("EXIT_LOAD", 100.0, 10);
        conditional.setEligibility("#fundCategory == 'DEBT'");
        givenSchedule(conditional);

        // When
        ChargeComputation computation = engine().compute(
                contextWith(ChargeEvent.SELL, null, Map.of("fundCategory", "DEBT")));

        // Then
        assertMoney(100.0, computation.total());
    }

    // ---------------------------------------------------------------- ordering

    @Test
    void compute_evaluatesRulesAscendingByOrder() {
        // Given — declared out of order on the card
        givenSchedule(flatRule("GST", 1.0, 100), flatRule("BROKERAGE", 1.0, 10), flatRule("STT", 1.0, 20));

        // When
        ChargeComputation computation = engine().compute(buy());

        // Then
        assertThat(computation.lines()).extracting(ChargeLine::getCode)
                .containsExactly("BROKERAGE", "STT", "GST");
    }

    @Test
    void compute_whenTwoRulesShareAnOrder_sortsThemDeterministically() {
        // Given — a data error the validator should catch, but the engine must not resolve it
        // differently between two runs of the same input
        givenSchedule(flatRule("SEBI_FEE", 1.0, 30), flatRule("IPFT", 1.0, 30));

        // When
        List<String> first = codesOf(engine().compute(buy()));
        List<String> second = codesOf(engine().compute(buy()));

        // Then
        assertThat(first).containsExactly("IPFT", "SEBI_FEE");
        assertThat(second).isEqualTo(first);
    }

    @Test
    void compute_whenRuleIsDerived_readsOnlyTheLinesAlreadyEmitted() {
        // Given — GST at 18% of brokerage, but not of the statutory charges
        ChargeRule brokerage = flatRule("BROKERAGE", 20.0, 10);
        ChargeRule stt = flatRule("STT", 100.0, 20);
        ChargeRule gst = derivedRule("GST", 18.0, List.of("BROKERAGE"), 100);
        givenSchedule(brokerage, stt, gst);

        // When
        ChargeComputation computation = engine().compute(buy());

        // Then — 18% of 20, never of 120. This is the D1 defect stated as an assertion.
        assertMoney(3.60, computation.amountOf("GST"));
    }

    @Test
    void compute_whenDerivedRuleNamesALaterRule_contributesNothingWithoutCrashing() {
        // Given — the validator rejects this ordering; the engine must degrade rather than throw
        ChargeRule gst = derivedRule("GST", 18.0, List.of("BROKERAGE"), 10);
        ChargeRule brokerage = flatRule("BROKERAGE", 20.0, 20);
        givenSchedule(gst, brokerage);

        // When
        ChargeComputation computation = engine().compute(buy());

        // Then
        assertNoCharge(computation.amountOf("GST"));
        assertMoney(20.0, computation.amountOf("BROKERAGE"));
    }

    // ---------------------------------------------------------------- assembly

    @Test
    void compute_totalEqualsTheSumOfTheLines() {
        // Given
        givenSchedule(flatRule("BROKERAGE", 20.0, 10), flatRule("STT", 100.0, 20), flatRule("DP", 13.5, 30));

        // When
        ChargeComputation computation = engine().compute(sell());

        // Then
        assertMoney(133.5, computation.total());
        assertThat(computation.lines()).hasSize(3);
    }

    @Test
    void compute_recordsTheScheduleItPriced() {
        // Given
        givenSchedule(flatRule("BROKERAGE", 20.0, 10));

        // When
        ChargeComputation computation = engine().compute(buy());

        // Then — provenance, so a stored charge can be traced back to the card that produced it
        assertThat(computation.scheduleId()).isEqualTo("sched-1");
        assertThat(computation.scheduleCode()).isEqualTo("ZERODHA_EQ_DELIVERY_2025_04");
        assertThat(computation.resolution()).isEqualTo(ChargeResolution.RESOLVED);
    }

    @Test
    void compute_recordsTheRateAndBaseEachLineWasComputedFrom() {
        // Given — a stored line has to be re-derivable, not taken on trust
        ChargeRule brokerage = flatRule("BROKERAGE", 20.0, 10);
        ChargeRule gst = derivedRule("GST", 18.0, List.of("BROKERAGE"), 100);
        givenSchedule(brokerage, gst);

        // When
        ChargeLine line = engine().compute(buy()).lines().get(1);

        // Then
        assertThat(line.getRate()).isEqualTo(18.0);
        assertMoney(20.0, line.getBaseAmount());
        assertThat(line.getSource()).isEqualTo(ChargeRuleSource.SCHEDULE);
        assertThat(line.getBasis()).isEqualTo(ChargeBasis.DERIVED);
    }

    @Test
    void compute_whenNoScheduleResolves_isEmptyAndSaysSo() {
        // Given — a transaction dated before any rate card on file (AC-12)
        when(scheduleResolver.resolve(any())).thenReturn(Optional.empty());

        // When
        ChargeComputation computation = engine().compute(buy());

        // Then — empty, but never silently: a missing card is distinguishable from a zero charge
        assertThat(computation.resolution()).isEqualTo(ChargeResolution.NO_SCHEDULE);
        assertThat(computation.lines()).isEmpty();
        assertNoCharge(computation.total());
    }

    @Test
    void compute_whenTheScheduleCarriesNoRules_isEmptyWithoutThrowing() {
        // Given
        givenSchedule();

        // When
        ChargeComputation computation = engine().compute(buy());

        // Then
        assertThat(computation.resolution()).isEqualTo(ChargeResolution.NO_MATCHING_RULES);
        assertNoCharge(computation.total());
    }

    @Test
    void compute_whenNoRuleMatchesTheEvent_isEmptyWithoutThrowing() {
        // Given — a card that prices sells only, applied to a buy
        ChargeRule sellOnly = flatRule("DP", 13.5, 10);
        sellOnly.setEvents(Set.of(ChargeEvent.SELL));
        givenSchedule(sellOnly);

        // When
        ChargeComputation computation = engine().compute(buy());

        // Then
        assertThat(computation.resolution()).isEqualTo(ChargeResolution.NO_MATCHING_RULES);
    }

    @Test
    void compute_whenContextIsNull_isRejected() {
        assertThatThrownBy(() -> engine().compute(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("context");
    }

    // ---------------------------------------------------------------- corporate actions

    @Test
    void compute_whenCorporateActionAndNoRuleOptsIn_isExempt() {
        // Given — bonus shares are issued free; charging them takes money never spent
        givenSchedule(flatRule("BROKERAGE", 20.0, 10), flatRule("STT", 100.0, 20));

        // When
        ChargeComputation computation = engine().compute(
                contextWith(ChargeEvent.BUY, CorporateActionType.BONUS, Map.of()));

        // Then
        assertThat(computation.resolution()).isEqualTo(ChargeResolution.CORPORATE_ACTION_EXEMPT);
        assertThat(computation.lines()).isEmpty();
        assertNoCharge(computation.total());
    }

    @Test
    void compute_whenARuleOptsIntoCorporateActions_appliesIt() {
        // Given — a buyback tender attracts brokerage and STT for real
        ChargeRule brokerage = flatRule("BROKERAGE", 20.0, 10);
        brokerage.setAppliesToCorporateActions(true);
        ChargeRule stt = flatRule("STT", 100.0, 20);
        givenSchedule(brokerage, stt);

        // When
        ChargeComputation computation = engine().compute(
                contextWith(ChargeEvent.SELL, CorporateActionType.BUYBACK, Map.of()));

        // Then — only the rule that opted in
        assertThat(codesOf(computation)).containsExactly("BROKERAGE");
        assertMoney(20.0, computation.total());
        assertThat(computation.resolution()).isEqualTo(ChargeResolution.RESOLVED);
    }

    @Test
    void compute_whenNotACorporateAction_isUnaffectedByTheExemption() {
        // Given — a rule that has not opted in still prices an ordinary trade
        givenSchedule(flatRule("BROKERAGE", 20.0, 10));

        // When
        ChargeComputation computation = engine().compute(buy());

        // Then
        assertMoney(20.0, computation.total());
    }

    @Test
    void compute_exemptCorporateActionIsDistinguishableFromAMissingCard() {
        // Given — both compute nothing; only the reason separates a deliberate zero from a gap
        givenSchedule(flatRule("BROKERAGE", 20.0, 10));
        ChargeComputation exempt = engine().compute(
                contextWith(ChargeEvent.BUY, CorporateActionType.BONUS, Map.of()));

        when(scheduleResolver.resolve(any())).thenReturn(Optional.empty());
        ChargeComputation missing = engine().compute(buy());

        // Then
        assertThat(exempt.resolution()).isEqualTo(ChargeResolution.CORPORATE_ACTION_EXEMPT);
        assertThat(missing.resolution()).isEqualTo(ChargeResolution.NO_SCHEDULE);
        assertThat(exempt.total()).isEqualTo(missing.total());
    }

    // ---------------------------------------------------------------- modifier order (§5.5)

    @Test
    void compute_whenAggregatorIsMin_capsTheRateByTheFlatAmount() {
        // Given — 0.03% of 100000 is 30, capped at the broker's flat 20
        ChargeRule brokerage = turnoverRule("BROKERAGE", 0.03, 10);
        brokerage.setFlatAmount(20.0);
        brokerage.setAggregator(AggregatorType.MIN);
        givenSchedule(brokerage);

        // When / Then
        assertMoney(20.0, engine().compute(buy()).total());
    }

    @Test
    void compute_whenAggregatorIsMax_floorsTheRateByTheFlatAmount() {
        // Given — 0.001% of 100000 is 1, floored at 20
        ChargeRule brokerage = turnoverRule("BROKERAGE", 0.001, 10);
        brokerage.setFlatAmount(20.0);
        brokerage.setAggregator(AggregatorType.MAX);
        givenSchedule(brokerage);

        // When / Then
        assertMoney(20.0, engine().compute(buy()).total());
    }

    @Test
    void compute_appliesTheAggregatorBeforeTheFloorAndCap() {
        // Given — MIN(30, 20) = 20, then a floor of 25 lifts it to 25.
        // Applied the other way round the floor would act on 30, the cap would pull it to 20, and
        // the answer would be 20 — so the order is observable, not academic.
        ChargeRule brokerage = turnoverRule("BROKERAGE", 0.03, 10);
        brokerage.setFlatAmount(20.0);
        brokerage.setAggregator(AggregatorType.MIN);
        brokerage.setMinAmount(25.0);
        givenSchedule(brokerage);

        // When / Then
        assertMoney(25.0, engine().compute(buy()).total());
    }

    @Test
    void compute_appliesTheFloorAndCapBeforeRounding() {
        // Given — a cap of 19.999 rounds to 20.00; rounding first would cap 20.00 to 19.999
        // and store an amount no contract note would ever show
        ChargeRule brokerage = turnoverRule("BROKERAGE", 0.03, 10);
        brokerage.setMaxAmount(19.999);
        brokerage.setRounding(RoundingPolicy.HALF_UP_2);
        givenSchedule(brokerage);

        // When / Then
        assertMoney(20.0, engine().compute(buy()).total());
    }

    @Test
    void compute_roundsEachLineOnceUnderItsOwnPolicy() {
        // Given — statutory charges go to the rupee, brokerage carries paise
        ChargeRule stt = turnoverRule("STT", 0.1, 10);
        stt.setRounding(RoundingPolicy.HALF_UP_0);
        ChargeRule brokerage = turnoverRule("BROKERAGE", 0.0297, 20);
        brokerage.setRounding(RoundingPolicy.HALF_UP_2);
        givenSchedule(stt, brokerage);

        // When
        ChargeComputation computation = engine().compute(buy());

        // Then — 0.1% of 100000 = 100 exactly; 0.0297% = 29.70
        assertMoney(100.0, computation.amountOf("STT"));
        assertMoney(29.70, computation.amountOf("BROKERAGE"));
    }

    @Test
    void compute_whenARuleHasBothARateAndAFlatAmountWithNoAggregator_failsLoudly() {
        // Given — the superseded implementation returned 0 here, silently. D7.
        ChargeRule ambiguous = turnoverRule("BROKERAGE", 0.03, 10);
        ambiguous.setFlatAmount(20.0);
        ambiguous.setAggregator(null);
        givenSchedule(ambiguous);

        // When / Then
        assertThatThrownBy(() -> engine().compute(buy()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("BROKERAGE");
    }

    // ---------------------------------------------------------------- per-lot evaluation (§5.7)

    @Test
    void compute_whenRuleIsPerLot_emitsOneLinePerLot() {
        // Given — a charge that applies to every lot the disposal consumes
        ChargeRule exitLoad = formulaRule("EXIT_LOAD", 10);
        exitLoad.setPerLot(true);
        givenSchedule(exitLoad);

        // When — two lots: 100 @ 900 and 50 @ 1000
        ChargeComputation computation = engine().compute(sellFromLots(
                new LotSlice(100, LocalDate.of(2024, 1, 1), 900),
                new LotSlice(50, LocalDate.of(2025, 5, 1), 1000)));

        // Then — 1% of each lot's own turnover, not of the transaction's
        assertThat(computation.lines()).hasSize(2);
        assertMoney(900.0 + 500.0, computation.amountOf("EXIT_LOAD"));
    }

    @Test
    void compute_whenRuleIsPerLot_evaluatesEligibilityAgainstEachLot() {
        // Given — the case a transaction-level holding period gets wrong by the entire charge:
        // 100 units held ~22 months attract nothing, 50 held ~1 month attract 1%. Averaged, the
        // holding period is ~15 months and the computed load is zero.
        ChargeRule exitLoad = formulaRule("EXIT_LOAD", 10);
        exitLoad.setPerLot(true);
        exitLoad.setEligibility("#holdingDays < 365");
        givenSchedule(exitLoad);

        // When
        ChargeComputation computation = engine().compute(sellFromLots(
                new LotSlice(100, LocalDate.of(2023, 8, 1), 900),
                new LotSlice(50, LocalDate.of(2025, 5, 1), 1000)));

        // Then — the young lot only
        assertThat(computation.lines()).hasSize(1);
        assertMoney(500.0, computation.total());
    }

    @Test
    void compute_whenRuleIsPerLotAndTheTradeHasNoLots_emitsNothing() {
        // Given — a purchase consumes no lots
        ChargeRule exitLoad = formulaRule("EXIT_LOAD", 10);
        exitLoad.setPerLot(true);
        givenSchedule(exitLoad);

        // When
        ChargeComputation computation = engine().compute(buy());

        // Then
        assertThat(computation.lines()).isEmpty();
        assertNoCharge(computation.total());
    }

    @Test
    void compute_whenRuleIsNotPerLot_evaluatesOnceAgainstTheWholeTrade() {
        // Given
        ChargeRule brokerage = formulaRule("BROKERAGE", 10);
        givenSchedule(brokerage);

        // When — the same two lots as above
        ChargeComputation computation = engine().compute(sellFromLots(
                new LotSlice(100, LocalDate.of(2024, 1, 1), 900),
                new LotSlice(50, LocalDate.of(2025, 5, 1), 1000)));

        // Then — one line, 1% of the transaction turnover
        assertThat(computation.lines()).hasSize(1);
        assertMoney(1000.0, computation.total());
    }

    // ---------------------------------------------------------------- rate-card shapes

    @Test
    void compute_whenTheScheduleCarriesNoRuleListAtAll_isEmptyWithoutThrowing() {
        // Given — a card persisted without a rules array, rather than with an empty one
        ChargeScheduleEntity schedule = new ChargeScheduleEntity();
        schedule.setId("sched-1");
        schedule.setScheduleCode("ZERODHA_EQ_DELIVERY_2025_04");
        schedule.setRules(null);
        when(scheduleResolver.resolve(any())).thenReturn(Optional.of(schedule));

        // When
        ChargeComputation computation = engine().compute(buy());

        // Then
        assertThat(computation.resolution()).isEqualTo(ChargeResolution.NO_MATCHING_RULES);
        assertNoCharge(computation.total());
    }

    @Test
    void compute_whenRuleNamesNoEvent_appliesToNone() {
        // Given — default-deny. A rule that forgot to say when it applies must charge nothing
        // rather than everything, because the two mistakes cost the user very differently.
        ChargeRule undeclared = flatRule("MYSTERY", 500.0, 10);
        undeclared.setEvents(null);
        givenSchedule(undeclared);

        // When / Then
        assertThat(engine().compute(buy()).lines()).isEmpty();
    }

    @Test
    void compute_whenRuleNamesNoSide_appliesToEitherSide() {
        // Given — an omitted side is the common case on a card; it must not disable the charge
        ChargeRule brokerage = flatRule("BROKERAGE", 20.0, 10);
        brokerage.setSide(null);
        givenSchedule(brokerage);

        // When / Then
        assertMoney("on a buy", 20.0, engine().compute(buy()).total());
        assertMoney("on a sell", 20.0, engine().compute(sell()).total());
    }

    @Test
    void compute_whenRuleNamesNoAmountBasis_pricesOnTurnover() {
        // Given — turnover is the default, and the cash segment never declares anything else
        ChargeRule stt = turnoverRule("STT", 0.1, 10);
        stt.setAmountBasis(null);
        givenSchedule(stt);

        // When / Then — 0.1% of 100000
        assertMoney(100.0, engine().compute(buy()).total());
    }

    @Test
    void compute_whenAnAggregatorIsDeclaredWithoutAFlatAmount_leavesTheAmountAlone() {
        // Given — there is no second value to aggregate against, so the rate stands
        ChargeRule brokerage = turnoverRule("BROKERAGE", 0.03, 10);
        brokerage.setAggregator(AggregatorType.MIN);
        brokerage.setFlatAmount(null);
        givenSchedule(brokerage);

        // When / Then — 0.03% of 100000, uncapped
        assertMoney(30.0, engine().compute(buy()).total());
    }

    @Test
    void compute_whenRuleCameFromTheInstrument_keepsThatProvenance() {
        // Given — exit load is the scheme's charge, not the broker's, and a merged line has to say
        // which of the two it came from
        ChargeRule exitLoad = flatRule("EXIT_LOAD", 100.0, 10);
        exitLoad.setSource(ChargeRuleSource.INSTRUMENT);
        givenSchedule(exitLoad);

        // When
        ChargeLine line = engine().compute(sell()).lines().get(0);

        // Then
        assertThat(line.getSource()).isEqualTo(ChargeRuleSource.INSTRUMENT);
    }

    @Test
    void compute_whenRuleIsPerLotAndTheContextCarriesNoLotList_emitsNothing() {
        // Given
        ChargeRule exitLoad = formulaRule("EXIT_LOAD", 10);
        exitLoad.setPerLot(true);
        givenSchedule(exitLoad);

        // When — lots absent rather than empty
        ChargeComputation computation = engine().compute(sellFromLots((List<LotSlice>) null));

        // Then
        assertThat(computation.lines()).isEmpty();
    }

    @Test
    void compute_whenRuleSideIsBuyAndTheEventIsABuy_appliesIt() {
        // Given — stamp duty is levied on purchases. The negative case is asserted above; without
        // this one, a side filter that rejected everything would look correct.
        ChargeRule stampDuty = flatRule("STAMP_DUTY", 15.0, 10);
        stampDuty.setSide(ChargeSide.BUY);
        givenSchedule(stampDuty);

        // When / Then
        assertMoney(15.0, engine().compute(buy()).total());
    }

    @Test
    void compute_carriesEveryDescriptiveFieldFromTheRuleOntoTheLine() {
        // Given — taxable in particular is load-bearing: it is what a derived rule's base is drawn
        // from downstream, so a line that lost the flag would quietly change a GST bill
        ChargeRule brokerage = flatRule("BROKERAGE", 20.0, 10);
        brokerage.setDisplayName("Brokerage");
        brokerage.setCategory(ChargeCategory.BROKERAGE);
        brokerage.setTaxable(true);
        givenSchedule(brokerage);

        // When
        ChargeLine line = engine().compute(buy()).lines().get(0);

        // Then
        assertThat(line.getCode()).isEqualTo("BROKERAGE");
        assertThat(line.getDisplayName()).isEqualTo("Brokerage");
        assertThat(line.getCategory()).isEqualTo(ChargeCategory.BROKERAGE);
        assertThat(line.isTaxable()).isTrue();
    }

    @Test
    void compute_whenRuleIsPerLot_carriesTheTradeAttributesIntoEveryLot() {
        // Given — a per-lot rule still sees the facts that belong to the trade as a whole, not only
        // the ones the lot contributes
        ChargeRule exitLoad = formulaRule("EXIT_LOAD", 10);
        exitLoad.setPerLot(true);
        exitLoad.setEligibility("#fundCategory == 'DEBT' and #holdingDays < 365");
        givenSchedule(exitLoad);

        ChargeContext trade = contextWith(ChargeEvent.SELL, null, Map.of("fundCategory", "DEBT"));
        ChargeContext withLots = new ChargeContext(
                trade.email(), trade.transactionId(), trade.orderId(), trade.stockCode(), trade.accountHolder(),
                trade.brokerName(), trade.assetType(), trade.segment(), trade.exchange(),
                trade.planCode(), trade.event(), trade.transactionDate(), trade.corporateActionType(),
                trade.quantity(), trade.price(), trade.lotSize(), trade.baseAmounts(),
                List.of(new LotSlice(50, LocalDate.of(2025, 5, 1), 1000)), trade.attributes());

        // When
        ChargeComputation computation = engine().compute(withLots);

        // Then — the lot supplies holdingDays, the trade supplies fundCategory, both are readable
        assertMoney(500.0, computation.total());
    }

    @Test
    void compute_whenRuleIsPerLot_carriesTheOtherAmountBasesIntoEveryLot() {
        // Given — only turnover is restated per lot; anything else the trade carries survives
        ChargeRule levy = turnoverRule("REMITTANCE_LEVY", 1.0, 10);
        levy.setAmountBasis(AmountBasis.PRINCIPAL);
        levy.setPerLot(true);
        givenSchedule(levy);

        Map<AmountBasis, Double> baseAmounts = new EnumMap<>(AmountBasis.class);
        baseAmounts.put(AmountBasis.TURNOVER, 100000.0);
        baseAmounts.put(AmountBasis.PRINCIPAL, 5000.0);

        ChargeContext trade = sell();
        ChargeContext withLots = new ChargeContext(
                trade.email(), trade.transactionId(), trade.orderId(), trade.stockCode(), trade.accountHolder(),
                trade.brokerName(), trade.assetType(), trade.segment(), trade.exchange(),
                trade.planCode(), trade.event(), trade.transactionDate(), trade.corporateActionType(),
                trade.quantity(), trade.price(), trade.lotSize(), baseAmounts,
                List.of(new LotSlice(50, LocalDate.of(2025, 5, 1), 1000)), trade.attributes());

        // When / Then — 1% of the principal, which the lot did not restate
        assertMoney(50.0, engine().compute(withLots).total());
    }

    // ---------------------------------------------------------------- fixtures

    private ChargeEngine engine() {
        return new ChargeEngine(scheduleResolver, stubRegistry(), new ChargeFormulaEvaluator());
    }

    private void givenSchedule(ChargeRule... rules) {
        ChargeScheduleEntity schedule = new ChargeScheduleEntity();
        schedule.setId("sched-1");
        schedule.setScheduleCode("ZERODHA_EQ_DELIVERY_2025_04");
        schedule.setRules(new ArrayList<>(List.of(rules)));
        when(scheduleResolver.resolve(any())).thenReturn(Optional.of(schedule));
    }

    private static List<String> codesOf(ChargeComputation computation) {
        return computation.lines().stream().map(ChargeLine::getCode).toList();
    }

    private static ChargeRule rule(String code, ChargeBasis basis, int order) {
        ChargeRule rule = new ChargeRule();
        rule.setCode(code);
        rule.setDisplayName(code);
        rule.setCategory(ChargeCategory.BROKERAGE);
        rule.setBasis(basis);
        rule.setSide(ChargeSide.BOTH);
        rule.setEvents(Set.of(ChargeEvent.BUY, ChargeEvent.SELL));
        rule.setAmountBasis(AmountBasis.TURNOVER);
        rule.setRounding(RoundingPolicy.HALF_UP_2);
        rule.setOrder(order);
        rule.setActive(true);
        return rule;
    }

    private static ChargeRule flatRule(String code, double flatAmount, int order) {
        ChargeRule rule = rule(code, ChargeBasis.FLAT, order);
        rule.setFlatAmount(flatAmount);
        return rule;
    }

    private static ChargeRule turnoverRule(String code, double rate, int order) {
        ChargeRule rule = rule(code, ChargeBasis.TURNOVER, order);
        rule.setRate(rate);
        return rule;
    }

    private static ChargeRule derivedRule(String code, double rate, List<String> baseCodes, int order) {
        ChargeRule rule = rule(code, ChargeBasis.DERIVED, order);
        rule.setRate(rate);
        rule.setBaseCodes(baseCodes);
        return rule;
    }

    private static ChargeRule formulaRule(String code, int order) {
        ChargeRule rule = rule(code, ChargeBasis.FORMULA, order);
        rule.setFormula("#turnover * 0.01");
        return rule;
    }

    private static ChargeContext buy() {
        return contextWith(ChargeEvent.BUY, null, Map.of());
    }

    private static ChargeContext sell() {
        return contextWith(ChargeEvent.SELL, null, Map.of());
    }

    private static ChargeContext sellFromLots(LotSlice... lots) {
        return sellFromLots(List.of(lots));
    }

    private static ChargeContext sellFromLots(List<LotSlice> lots) {
        ChargeContext base = sell();
        return new ChargeContext(
                base.email(), base.transactionId(), base.orderId(), base.stockCode(), base.accountHolder(),
                base.brokerName(), base.assetType(), base.segment(), base.exchange(), base.planCode(),
                base.event(), base.transactionDate(), base.corporateActionType(), base.quantity(),
                base.price(), base.lotSize(), base.baseAmounts(), lots, base.attributes());
    }

    private static ChargeContext contextWith(
            ChargeEvent event, CorporateActionType corporateActionType, Map<String, Object> attributes) {

        Map<AmountBasis, Double> baseAmounts = new EnumMap<>(AmountBasis.class);
        baseAmounts.put(AmountBasis.TURNOVER, 100000.0);

        return new ChargeContext(
                "investor@example.com", "txn-1", "ord-1", "RELIANCE", "self", BrokerName.ZERODHA, AssetType.EQUITY,
                TradeSegment.DELIVERY, "NSE", null, event, TRADE_DATE, corporateActionType,
                100, 1000, 1, baseAmounts, List.of(), new HashMap<>(attributes));
    }

    /**
     * Every basis served, with arithmetic simple enough that a failing engine test is unambiguous
     * about which of the two is wrong. The real implementations arrive in Chunk 4.
     */
    private static ChargeCalculatorRegistry stubRegistry() {
        List<ChargeCalculator> calculators = List.of(
                stub(ChargeBasis.TURNOVER, (rule, context, accumulator) ->
                        percentageOf(rule.getRate(), context.amount(rule.effectiveAmountBasis()))),
                stub(ChargeBasis.FLAT, (rule, context, accumulator) -> amount(rule.getFlatAmount())),
                stub(ChargeBasis.SCOPED_FLAT, (rule, context, accumulator) -> amount(rule.getFlatAmount())),
                stub(ChargeBasis.PER_UNIT, (rule, context, accumulator) ->
                        amount(rule.getPerUnitAmount()).multiply(BigDecimal.valueOf(context.quantity()))),
                stub(ChargeBasis.SLAB, (rule, context, accumulator) -> BigDecimal.ZERO),
                stub(ChargeBasis.DERIVED, (rule, context, accumulator) ->
                        percentageOf(rule.getRate(), accumulator.sumOf(rule.getBaseCodes()).doubleValue())),
                stub(ChargeBasis.FORMULA, (rule, context, accumulator) ->
                        percentageOf(1.0, context.amount(AmountBasis.TURNOVER))));

        return new ChargeCalculatorRegistry(calculators);
    }

    private static BigDecimal percentageOf(Double rate, double base) {
        return amount(rate).multiply(BigDecimal.valueOf(base)).movePointLeft(2);
    }

    private static BigDecimal amount(Double value) {
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }

    private static ChargeCalculator stub(ChargeBasis basis, Arithmetic arithmetic) {
        return new ChargeCalculator() {

            @Override
            public ChargeBasis basis() {
                return basis;
            }

            @Override
            public BigDecimal compute(ChargeRule rule, ChargeContext context, ChargeAccumulator accumulator) {
                return arithmetic.compute(rule, context, accumulator);
            }
        };
    }

    @FunctionalInterface
    private interface Arithmetic {
        BigDecimal compute(ChargeRule rule, ChargeContext context, ChargeAccumulator accumulator);
    }
}
