package com.thiru.wealthlens.brokercharges.service;

import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.engine.ChargeFormulaEvaluator;
import com.thiru.wealthlens.brokercharges.entity.ChargeCatalogueEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeSlab;
import com.thiru.wealthlens.brokercharges.repository.ChargeCatalogueRepository;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * Decides whether a rate card is safe to accept.
 *
 * <p>A rate card is data, so nothing checks it at compile time and a mistake in one survives until a
 * trade is priced — by which point it has been applied to a quarter of transactions and folded into
 * a cost basis. Everything here is a rejection the engine relies on: the orchestrator's ambiguous
 * rule (D7), a derived rule ordered before its own base, and an expression variable that does not
 * exist.
 *
 * <h2>Why every problem is reported at once</h2>
 * A seed file is authored by hand. Surfacing one failure per run turns fixing it into an afternoon
 * of re-running the application.
 *
 * <h2>Why expression variables are checked against a fixed vocabulary</h2>
 * {@code #equityOrientd} parses cleanly, evaluates to null, makes its rule's predicate false, and
 * disables a statutory charge permanently and silently. The cost of catching that is that a new
 * runtime fact has to be added here as well as published by the engine — a deliberate trade, per
 * ADR-24.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class ChargeScheduleValidator {

    /**
     * Everything an expression may name: what {@code ChargeFormulaEvaluator} publishes, every
     * {@link AmountBasis} by lower-cased name, the instrument attributes the engine merges in, and
     * the holding period it publishes per lot.
     */
    private static final Set<String> VOCABULARY = vocabulary();

    private final ChargeCatalogueRepository chargeCatalogueRepository;
    private final ChargeFormulaEvaluator formulaEvaluator;

    /**
     * @throws BadRequestException listing every problem found, naming the rule each belongs to
     */
    public void validate(ChargeScheduleEntity schedule) {
        Set<String> catalogue = chargeCatalogueRepository.findByStatus(EntityStatus.ACTIVE).stream()
                .map(ChargeCatalogueEntity::getCode)
                .collect(Collectors.toSet());

        List<String> errors = new ArrayList<>();
        validateValidityWindow(schedule, errors);

        List<ChargeRule> rules = schedule.getRules() == null ? List.of() : schedule.getRules();
        if (rules.isEmpty()) {
            // An empty card still resolves, and then prices every trade at zero — indistinguishable
            // from a broker that charges nothing.
            errors.add("it declares no rules");
        } else {
            validateRules(rules, catalogue, errors);
        }

        if (!errors.isEmpty()) {
            throw new BadRequestException("Charge schedule " + schedule.getScheduleCode()
                    + " is invalid: " + String.join("; ", errors));
        }
    }

    private static void validateValidityWindow(ChargeScheduleEntity schedule, List<String> errors) {
        if (schedule.getStartDate() == null) {
            // Which card applies is decided by the window alone, so a card without one is never
            // selected for any trade.
            errors.add("it has no start date");
            return;
        }
        if (schedule.getEndDate() != null && schedule.getEndDate().isBefore(schedule.getStartDate())) {
            errors.add("its end date " + schedule.getEndDate() + " precedes its start date "
                    + schedule.getStartDate());
        }
    }

    private void validateRules(List<ChargeRule> rules, Set<String> catalogue, List<String> errors) {
        validateCodesAreUnique(rules, errors);

        Map<String, ChargeRule> byCode = new HashMap<>();
        rules.forEach(rule -> byCode.putIfAbsent(rule.getCode(), rule));

        for (ChargeRule rule : rules) {
            validateCatalogued(rule, catalogue, errors);
            validateBasisParameters(rule, errors);
            validateAmountsArePositive(rule, errors);
            validateAggregator(rule, errors);
            validateSlabs(rule, errors);
            validateDerivedBase(rule, byCode, errors);
            validateExpression(rule, "formula", rule.getFormula(), errors);
            validateExpression(rule, "eligibility", rule.getEligibility(), errors);
        }
    }

    private static void validateCodesAreUnique(List<ChargeRule> rules, List<String> errors) {
        Set<String> seen = new HashSet<>();
        rules.stream()
                .map(ChargeRule::getCode)
                .filter(code -> !seen.add(code))
                .distinct()
                // A derived rule naming the code would silently draw on both.
                .forEach(code -> errors.add("code " + code + " is declared more than once"));
    }

    private static void validateCatalogued(ChargeRule rule, Set<String> catalogue, List<String> errors) {
        if (!catalogue.contains(rule.getCode())) {
            errors.add("rule " + rule.getCode() + " is not in the charge catalogue");
        }
    }

    /** Each basis is arithmetic over one parameter; without it there is nothing to compute. */
    private static void validateBasisParameters(ChargeRule rule, List<String> errors) {
        switch (rule.getBasis()) {
            case TURNOVER, DERIVED -> requirePresent(rule, rule.getRate(), "rate", errors);
            case FLAT, SCOPED_FLAT -> requirePresent(rule, rule.getFlatAmount(), "flat amount", errors);
            case PER_UNIT -> requirePresent(rule, rule.getPerUnitAmount(), "per unit amount", errors);
            case SLAB -> requireBands(rule, errors);
            case FORMULA -> requireFormula(rule, errors);
        }
    }

    private static void requirePresent(ChargeRule rule, Double value, String what, List<String> errors) {
        if (value == null) {
            errors.add("rule " + rule.getCode() + " is " + rule.getBasis() + " but declares no " + what);
        }
    }

    private static void requireBands(ChargeRule rule, List<String> errors) {
        if (rule.getSlabs() == null || rule.getSlabs().isEmpty()) {
            errors.add("rule " + rule.getCode() + " is SLAB but declares no bands");
        }
    }

    private static void requireFormula(ChargeRule rule, List<String> errors) {
        if (rule.getFormula() == null || rule.getFormula().isBlank()) {
            errors.add("rule " + rule.getCode() + " is FORMULA but declares no formula");
        }
    }

    private static void validateAmountsArePositive(ChargeRule rule, List<String> errors) {
        rejectNegative(rule, rule.getRate(), "rate", errors);
        rejectNegative(rule, rule.getFlatAmount(), "flat amount", errors);
        rejectNegative(rule, rule.getPerUnitAmount(), "per unit amount", errors);
        rejectNegative(rule, rule.getMinAmount(), "minimum amount", errors);
        rejectNegative(rule, rule.getMaxAmount(), "maximum amount", errors);
    }

    private static void rejectNegative(ChargeRule rule, Double value, String what, List<String> errors) {
        if (value != null && value < 0) {
            // A charge that pays the user is a card error, not a discount.
            errors.add("rule " + rule.getCode() + " has a negative " + what);
        }
    }

    /**
     * D7. A rule carrying both a rate and a flat amount has to say which wins; the superseded
     * implementation resolved the ambiguity to zero, so a mispriced card looked like a free trade.
     */
    private static void validateAggregator(ChargeRule rule, List<String> errors) {
        boolean bothOperands = rule.getRate() != null && rule.getFlatAmount() != null;

        if (bothOperands && rule.getAggregator() == null) {
            errors.add("rule " + rule.getCode() + " declares both a rate and a flat amount but no"
                    + " aggregator, so which one applies is undefined");
        }
        if (!bothOperands && rule.getAggregator() != null) {
            errors.add("rule " + rule.getCode() + " declares an aggregator but not both a rate and a"
                    + " flat amount to choose between");
        }
    }

    /** Bands are half-open and must tile the range: no trade may fall in two, and none in none. */
    private static void validateSlabs(ChargeRule rule, List<String> errors) {
        List<ChargeSlab> slabs = rule.getSlabs();
        if (slabs == null || slabs.size() < 2) {
            return;
        }

        List<ChargeSlab> ordered = slabs.stream()
                .sorted(Comparator.comparingDouble(slab -> floor(slab.getFromValue())))
                .toList();

        for (int index = 1; index < ordered.size(); index++) {
            double previousCeiling = ceiling(ordered.get(index - 1).getToValue());
            double nextFloor = floor(ordered.get(index).getFromValue());

            if (nextFloor < previousCeiling) {
                errors.add("rule " + rule.getCode() + " has bands that overlap at " + nextFloor);
            } else if (nextFloor > previousCeiling) {
                // A trade landing in the gap charges nothing, and looks exactly like a trade that
                // genuinely attracts none.
                errors.add("rule " + rule.getCode() + " has a gap between its bands at " + previousCeiling);
            }
        }
    }

    private static double floor(Double value) {
        return value == null ? Double.NEGATIVE_INFINITY : value;
    }

    private static double ceiling(Double value) {
        return value == null ? Double.POSITIVE_INFINITY : value;
    }

    /**
     * A derived rule reads the accumulator, which holds only what has already been applied. Naming
     * a code that evaluates later taxes a line that does not exist yet and quietly under-collects.
     */
    private static void validateDerivedBase(ChargeRule rule, Map<String, ChargeRule> byCode, List<String> errors) {
        if (rule.getBasis() != ChargeBasis.DERIVED) {
            return;
        }

        List<String> baseCodes = rule.getBaseCodes();
        if (baseCodes == null || baseCodes.isEmpty()) {
            errors.add("rule " + rule.getCode() + " is DERIVED but names no base codes");
            return;
        }

        for (String baseCode : baseCodes) {
            ChargeRule base = byCode.get(baseCode);
            if (base == null) {
                errors.add("rule " + rule.getCode() + " names base code " + baseCode
                        + ", which the schedule does not declare");
            } else if (base.getOrder() >= rule.getOrder()) {
                errors.add("rule " + rule.getCode() + " names base code " + baseCode + ", whose order "
                        + base.getOrder() + " is not before its own order " + rule.getOrder());
            }
        }
    }

    private void validateExpression(ChargeRule rule, String what, String expression, List<String> errors) {
        if (expression == null || expression.isBlank()) {
            return;
        }

        try {
            formulaEvaluator.validate(expression);
        } catch (BadRequestException e) {
            errors.add("rule " + rule.getCode() + " has an unparseable " + what + ": " + expression);
            return;
        }

        Set<String> unknown = formulaEvaluator.referencedVariables(expression).stream()
                .filter(name -> !VOCABULARY.contains(name))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!unknown.isEmpty()) {
            errors.add("rule " + rule.getCode() + " " + what + " refers to " + unknown
                    + ", which the charge context does not carry");
        }
    }

    private static Set<String> vocabulary() {
        Set<String> names = new LinkedHashSet<>(Set.of(
                "quantity", "price", "lotSize", "side", "charges",
                "equityOriented", "fundCategory", "planType", "amc", "holdingDays"));
        Arrays.stream(AmountBasis.values())
                .map(basis -> basis.name().toLowerCase())
                .forEach(names::add);
        return Set.copyOf(names);
    }
}
