package com.thiru.wealthlens.brokercharges.engine;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.context.LotSlice;
import com.thiru.wealthlens.brokercharges.dto.enums.AggregatorType;
import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeRuleSource;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeSide;
import com.thiru.wealthlens.brokercharges.dto.enums.RoundingPolicy;
import com.thiru.wealthlens.brokercharges.entity.ChargeInstrumentEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeLine;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * Prices one chargeable event: resolve the rate card, select the rules that apply, evaluate them in
 * order, and assemble the lines.
 *
 * <h2>Why the modifiers live here and not in the calculators</h2>
 * A calculator returns a raw amount and nothing else. Floors, caps, the choice between a rate and a
 * flat fee, and rounding are applied by this class, in one place, in a fixed order. Spread across
 * seven calculators they would drift, and rounding applied twice compounds into paise of
 * disagreement with a real contract note.
 *
 * <h2>Why an empty result carries a reason</h2>
 * Charging nothing is a legitimate outcome — a bonus allotment is issued free — and so is having no
 * rate card on file for the date. Those are the same number and completely different facts, so
 * every empty computation names which one it was rather than returning a bare zero.
 *
 * <h2>Two sources of rules, one ordered evaluation</h2>
 * The broker's card and the instrument's own profile are resolved separately and merged into a
 * single list sorted by {@code order}, rather than evaluated one after the other. That is what lets
 * a derived rule on the broker's card include an instrument-sourced line in its base.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class ChargeEngine {

    /** Rules are evaluated in this order, and a derived rule may only read what precedes it. */
    private static final Comparator<SourcedRule> EVALUATION_ORDER = Comparator
            .comparingInt((SourcedRule sourced) -> sourced.rule().getOrder())
            .thenComparing(sourced -> sourced.rule().getCode(), Comparator.nullsLast(Comparator.naturalOrder()));

    private final ChargeScheduleResolver scheduleResolver;
    private final ChargeInstrumentResolver instrumentResolver;
    private final ChargeCalculatorRegistry calculatorRegistry;
    private final ChargeFormulaEvaluator formulaEvaluator;

    /**
     * What this event costs, line by line.
     *
     * @throws BadRequestException if a rule cannot be priced unambiguously — a rate card defect is
     *                             surfaced rather than absorbed into a zero
     */
    public ChargeComputation compute(ChargeContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Charge context must not be null");
        }

        ChargeScheduleEntity schedule = scheduleResolver.resolve(context).orElse(null);
        if (schedule == null) {
            log.warn("No charge schedule resolved for broker={}, assetType={}, segment={} on {}; "
                            + "transaction {} accrues no charges and is recorded as a gap",
                    context.brokerName(), context.assetType(), context.segment(),
                    context.transactionDate(), context.transactionId());
            return ChargeComputation.empty(ChargeResolution.NO_SCHEDULE);
        }

        ChargeInstrumentEntity instrument = resolveInstrument(schedule, context);
        boolean profileMissing = schedule.isRequiresInstrumentProfile() && instrument == null;
        ChargeContext enriched = withInstrumentAttributes(context, instrument);

        ChargeAccumulator accumulator = new ChargeAccumulator();
        for (SourcedRule sourced : applicableRules(schedule, instrument, enriched)) {
            evaluate(sourced, enriched, accumulator);
        }

        List<ChargeLine> lines = accumulator.lines();
        if (lines.isEmpty()) {
            return ChargeComputation.empty(emptyResolution(context, profileMissing));
        }

        BigDecimal total = ChargeRounding.apply(accumulator.total(), RoundingPolicy.HALF_UP_2);
        return new ChargeComputation(
                schedule.getId(), schedule.getScheduleCode(),
                instrument == null ? null : instrument.getId(),
                profileMissing ? ChargeResolution.NO_INSTRUMENT_PROFILE : ChargeResolution.RESOLVED,
                List.copyOf(lines), total.doubleValue());
    }

    /**
     * Looked up only where the card says a profile is expected. Equity cards carry no scheme-level
     * charges, and this runs once per transaction.
     */
    private ChargeInstrumentEntity resolveInstrument(ChargeScheduleEntity schedule, ChargeContext context) {
        if (!schedule.isRequiresInstrumentProfile()) {
            return null;
        }

        ChargeInstrumentEntity instrument = instrumentResolver.resolve(context).orElse(null);
        if (instrument == null) {
            // Recorded on the computation rather than merely logged, because a missing profile
            // silently disables a statutory charge: the securities transaction tax rule's
            // eligibility reads #equityOriented, and an unset variable makes it quietly false.
            log.warn("Charge schedule {} expects an instrument profile for {} but none is in force on {};"
                            + " broker charges priced, scheme charges omitted",
                    schedule.getScheduleCode(), context.stockCode(), context.transactionDate());
        }
        return instrument;
    }

    /**
     * Publishes the scheme's own facts so a rule's eligibility can read them. Without this the rule
     * "securities transaction tax applies to equity-oriented funds but not debt funds" cannot be
     * expressed at all.
     *
     * <p>The instrument wins over anything the caller supplied under the same name: it is the source
     * of truth for its own attributes.
     */
    private static ChargeContext withInstrumentAttributes(ChargeContext context, ChargeInstrumentEntity instrument) {
        if (instrument == null) {
            return context;
        }

        Map<String, Object> attributes = new HashMap<>();
        if (context.attributes() != null) {
            attributes.putAll(context.attributes());
        }
        putIfPresent(attributes, "equityOriented", instrument.getEquityOriented());
        putIfPresent(attributes, "fundCategory", name(instrument.getFundCategory()));
        putIfPresent(attributes, "planType", name(instrument.getPlanType()));
        putIfPresent(attributes, "amc", instrument.getAmc());

        return new ChargeContext(
                context.email(), context.transactionId(), context.orderId(), context.stockCode(),
                context.accountHolder(), context.brokerName(), context.assetType(), context.segment(),
                context.exchange(), context.planCode(), context.event(), context.transactionDate(),
                context.corporateActionType(), context.quantity(), context.price(), context.lotSize(),
                context.baseAmounts(), context.lots(), attributes);
    }

    private static void putIfPresent(Map<String, Object> attributes, String name, Object value) {
        if (value != null) {
            attributes.put(name, value);
        }
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    /**
     * Why nothing was charged. A deliberate exemption, a card with no applicable rule and a missing
     * scheme profile are the same number and three different facts.
     */
    private static ChargeResolution emptyResolution(ChargeContext context, boolean profileMissing) {
        if (context.isCorporateAction()) {
            return ChargeResolution.CORPORATE_ACTION_EXEMPT;
        }
        return profileMissing ? ChargeResolution.NO_INSTRUMENT_PROFILE : ChargeResolution.NO_MATCHING_RULES;
    }

    /**
     * The rules this event attracts, in evaluation order.
     *
     * <p>Eligibility is deliberately not applied here. A predicate may read the charges already
     * applied, and a per-lot predicate is answered differently for each lot — both need the running
     * evaluation, so the question is asked at the point the rule is priced.
     */
    private static List<SourcedRule> applicableRules(
            ChargeScheduleEntity schedule, ChargeInstrumentEntity instrument, ChargeContext context) {

        Stream<SourcedRule> fromSchedule = sourced(schedule.getRules(), ChargeRuleSource.SCHEDULE);
        Stream<SourcedRule> fromInstrument = instrument == null
                ? Stream.empty()
                : sourced(instrument.getRules(), ChargeRuleSource.INSTRUMENT);

        return Stream.concat(fromSchedule, fromInstrument)
                .filter(sourced -> sourced.rule().isActive())
                .filter(sourced -> declaresEvent(sourced.rule(), context))
                .filter(sourced -> sideMatches(sourced.rule(), context))
                .filter(sourced -> survivesCorporateActionExemption(sourced.rule(), context))
                .sorted(EVALUATION_ORDER)
                .toList();
    }

    private static Stream<SourcedRule> sourced(List<ChargeRule> rules, ChargeRuleSource source) {
        return rules == null ? Stream.empty() : rules.stream().map(rule -> new SourcedRule(rule, source));
    }

    /** A rule naming no event applies to none. Charges are opted into explicitly, never inferred. */
    private static boolean declaresEvent(ChargeRule rule, ChargeContext context) {
        return rule.getEvents() != null && rule.getEvents().contains(context.event());
    }

    private static boolean sideMatches(ChargeRule rule, ChargeContext context) {
        ChargeSide side = rule.getSide();
        if (side == null || side == ChargeSide.BOTH) {
            return true;
        }
        return context.event() != null && side.name().equals(context.event().name());
    }

    /**
     * Bonus shares, split allotments and demerger entitlements are issued free, so a rule reaches a
     * corporate action only by declaring that it should. Default-deny because the failure modes are
     * not symmetric: charging free shares takes money the user never spent, while a missed buyback
     * charge understates a cost and is caught by reconciliation.
     */
    private static boolean survivesCorporateActionExemption(ChargeRule rule, ChargeContext context) {
        return !context.isCorporateAction() || rule.isAppliesToCorporateActions();
    }

    private void evaluate(SourcedRule sourced, ChargeContext context, ChargeAccumulator accumulator) {
        ChargeRule rule = sourced.rule();
        rejectAmbiguousRule(rule);

        if (!rule.isPerLot()) {
            evaluateOnce(sourced, context, accumulator);
            return;
        }

        // Per lot, because a charge conditioned on holding period is answered per lot. A redemption
        // spanning lots of different ages attracts a different amount on each, and averaging over
        // the transaction can be wrong by the whole charge rather than by a rounding error.
        List<LotSlice> lots = context.lots() == null ? List.of() : context.lots();
        for (LotSlice lot : lots) {
            evaluateOnce(sourced, lotContext(context, lot), accumulator);
        }
    }

    private void evaluateOnce(SourcedRule sourced, ChargeContext context, ChargeAccumulator accumulator) {
        ChargeRule rule = sourced.rule();
        if (!formulaEvaluator.matches(rule.getEligibility(), context, accumulator)) {
            return;
        }

        BigDecimal base = baseAmountOf(rule, context, accumulator);
        BigDecimal raw = calculatorRegistry.get(rule.getBasis()).compute(rule, context, accumulator);
        BigDecimal amount = ChargeRounding.apply(applyModifiers(rule, raw), rule.getRounding());

        accumulator.add(lineOf(sourced, base, amount));
    }

    /**
     * The §5.5 contract: aggregator, then floor and cap, then rounding — in that order, exactly
     * once each.
     *
     * <p>The order is observable rather than academic. A rate of 30 capped by a flat 20 and then
     * floored at 25 gives 25; applying the floor first gives 20. Rounding last is what keeps a
     * stored amount equal to the one a contract note shows.
     */
    private static BigDecimal applyModifiers(ChargeRule rule, BigDecimal raw) {
        BigDecimal amount = raw;

        if (rule.getAggregator() != null && rule.getFlatAmount() != null) {
            BigDecimal flat = BigDecimal.valueOf(rule.getFlatAmount());
            amount = rule.getAggregator() == AggregatorType.MIN ? amount.min(flat) : amount.max(flat);
        }
        if (rule.getMinAmount() != null) {
            amount = amount.max(BigDecimal.valueOf(rule.getMinAmount()));
        }
        if (rule.getMaxAmount() != null) {
            amount = amount.min(BigDecimal.valueOf(rule.getMaxAmount()));
        }
        return amount;
    }

    /**
     * A rule carrying both a rate and a flat amount has to say which wins. The superseded
     * implementation resolved this silently to zero, so a mispriced card looked exactly like a free
     * trade (D7).
     */
    private static void rejectAmbiguousRule(ChargeRule rule) {
        if (rule.getRate() != null && rule.getFlatAmount() != null && rule.getAggregator() == null) {
            throw new BadRequestException("Charge rule " + rule.getCode()
                    + " declares both a rate and a flat amount but no aggregator, so which one applies"
                    + " is undefined. Declare MIN or MAX.");
        }
    }

    /** The amount the line was computed from, recorded so a stored charge can be re-derived. */
    private static BigDecimal baseAmountOf(ChargeRule rule, ChargeContext context, ChargeAccumulator accumulator) {
        if (rule.getBasis() == ChargeBasis.DERIVED) {
            return accumulator.sumOf(rule.getBaseCodes());
        }
        return BigDecimal.valueOf(context.amount(rule.effectiveAmountBasis()));
    }

    /**
     * The trade as one lot sees it.
     *
     * <p>Turnover is restated from the lot's own quantity and price, and {@code holdingDays} is
     * published so an eligibility predicate can ask how long <em>this</em> lot was held. The other
     * amount bases are carried through unchanged; none of them is per-lot in the cash segment,
     * which is the only segment Phase A prices.
     */
    private static ChargeContext lotContext(ChargeContext context, LotSlice lot) {
        Map<AmountBasis, Double> baseAmounts = new EnumMap<>(AmountBasis.class);
        if (context.baseAmounts() != null) {
            baseAmounts.putAll(context.baseAmounts());
        }
        baseAmounts.put(AmountBasis.TURNOVER, lot.quantity() * lot.price());

        Map<String, Object> attributes = new HashMap<>();
        if (context.attributes() != null) {
            attributes.putAll(context.attributes());
        }
        attributes.put("holdingDays", lot.holdingDays(context.transactionDate()));

        return new ChargeContext(
                context.email(), context.transactionId(), context.orderId(), context.stockCode(), context.accountHolder(),
                context.brokerName(), context.assetType(), context.segment(), context.exchange(),
                context.planCode(), context.event(), context.transactionDate(), context.corporateActionType(),
                lot.quantity(), lot.price(), context.lotSize(), baseAmounts, List.of(lot), attributes);
    }

    /**
     * A rule paired with where it was read from.
     *
     * <p>The provenance is not written onto the rule itself: rules live inside cached schedule and
     * instrument documents, and stamping a field on them would mutate shared state through the
     * resolver cache.
     */
    private record SourcedRule(ChargeRule rule, ChargeRuleSource source) {
    }

    private static ChargeLine lineOf(SourcedRule sourced, BigDecimal baseAmount, BigDecimal amount) {
        ChargeRule rule = sourced.rule();
        ChargeLine line = new ChargeLine();
        line.setCode(rule.getCode());
        line.setDisplayName(rule.getDisplayName());
        line.setCategory(rule.getCategory());
        line.setBasis(rule.getBasis());
        line.setSource(rule.getSource() == null ? sourced.source() : rule.getSource());
        line.setRate(rule.getRate());
        line.setBaseAmount(baseAmount.doubleValue());
        line.setAmount(amount.doubleValue());
        line.setTaxable(rule.isTaxable());
        return line;
    }
}
