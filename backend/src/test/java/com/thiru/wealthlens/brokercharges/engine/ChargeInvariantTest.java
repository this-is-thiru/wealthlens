package com.thiru.wealthlens.brokercharges.engine;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertMoney;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeCategory;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeSide;
import com.thiru.wealthlens.brokercharges.dto.enums.RoundingPolicy;
import com.thiru.wealthlens.brokercharges.entity.ChargeLine;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeSlab;
import com.thiru.wealthlens.brokercharges.repository.UserChargeRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Properties that must hold for any valid rate card, checked over generated ones.
 *
 * <p>Every other test in this package fixes an input and asserts an output, which proves the engine
 * right about the cases someone thought of. These assert relationships that must hold whatever the
 * card says — the closest thing to a proof this design admits, and the tier most likely to catch a
 * change that is correct for the fixtures and wrong in general.
 *
 * <p>Run against the real calculators rather than stubs. A property over stubbed arithmetic would
 * only restate the stubs.
 *
 * <p>The seed is fixed so a failure is reproducible. Widening it is how you look for more.
 */
class ChargeInvariantTest {

    private static final int CASES = 200;
    private static final long SEED = 20260906L;
    private static final double TURNOVER = 100000.0;

    private final UserChargeRepository userChargeRepository = mock(UserChargeRepository.class);
    private final ChargeFormulaEvaluator formulaEvaluator = new ChargeFormulaEvaluator();

    @Test
    void total_isAlwaysTheSumOfTheLines() {
        forEachGeneratedCard((schedule, computation) -> {
            double summed = computation.lines().stream().mapToDouble(ChargeLine::getAmount).sum();
            assertMoney("total for " + codes(computation), summed, computation.total());
        });
    }

    @Test
    void noLineIsEverNegative() {
        // A charge that pays the user cannot arise from any combination of valid rules.
        forEachGeneratedCard((schedule, computation) ->
                assertThat(computation.lines()).allSatisfy(line ->
                        assertThat(line.getAmount()).isGreaterThanOrEqualTo(0.0)));
    }

    @Test
    void noLineIsEmittedForARuleThatDoesNotApplyToTheEvent() {
        forEachGeneratedCard((schedule, computation) -> {
            for (ChargeLine line : computation.lines()) {
                ChargeRule rule = ruleFor(schedule, line.getCode());
                assertThat(rule.isActive()).as("%s is active", rule.getCode()).isTrue();
                assertThat(rule.getEvents()).as("%s declares SELL", rule.getCode())
                        .contains(ChargeEvent.SELL);
                assertThat(rule.getSide()).as("%s applies to a sell", rule.getCode())
                        .isIn(ChargeSide.SELL, ChargeSide.BOTH);
            }
        });
    }

    @Test
    void anInactiveRuleNeverContributes() {
        forEachGeneratedCard((schedule, computation) -> {
            List<String> inactive = schedule.getRules().stream()
                    .filter(rule -> !rule.isActive())
                    .map(ChargeRule::getCode)
                    .toList();
            // Most generated cards have no disabled rule; AssertJ rejects an empty expectation.
            if (!inactive.isEmpty()) {
                assertThat(codes(computation)).doesNotContainAnyElementsOf(inactive);
            }
        });
    }

    @Test
    void aDerivedLineIsTheRateOnItsNamedBaseAndNothingElse() {
        // The structural guard on D1. Whatever else the card charges, a derived rule sees only the
        // codes it lists — so a statutory line cannot creep into a tax base by being present.
        forEachGeneratedCard((schedule, computation) -> {
            for (ChargeLine line : computation.lines()) {
                ChargeRule rule = ruleFor(schedule, line.getCode());
                if (rule.getBasis() != ChargeBasis.DERIVED) {
                    continue;
                }
                double base = computation.lines().stream()
                        .filter(other -> rule.getBaseCodes().contains(other.getCode()))
                        .mapToDouble(ChargeLine::getAmount)
                        .sum();
                assertMoney("derived " + rule.getCode(), round(base * rule.getRate() / 100.0), line.getAmount());
            }
        });
    }

    @Test
    void computingTwiceWithTheSameInputGivesTheSameAnswer() {
        // Catches anything that leaks map iteration order into an amount.
        Random random = new Random(SEED);
        for (int i = 0; i < CASES; i++) {
            ChargeScheduleEntity schedule = generateCard(random);
            ChargeEngine engine = engineFor(schedule);

            ChargeComputation first = engine.compute(sell(TURNOVER));
            ChargeComputation second = engine.compute(sell(TURNOVER));

            assertThat(codes(second)).isEqualTo(codes(first));
            assertMoney(first.total(), second.total());
            assertThat(second.amountByCode()).isEqualTo(first.amountByCode());
        }
    }

    @Test
    void amountByCodeHoldsExactlyTheCodesThatWereCharged() {
        forEachGeneratedCard((schedule, computation) ->
                assertThat(computation.amountByCode().keySet())
                        .containsExactlyInAnyOrderElementsOf(Set.copyOf(codes(computation))));
    }

    @Test
    void removingARuleReducesTheTotalByExactlyThatRulesAmount() {
        // Except where the rule is a derived base, in which case removing it also shrinks the tax.
        Random random = new Random(SEED);
        for (int i = 0; i < CASES; i++) {
            ChargeScheduleEntity schedule = generateCard(random);
            ChargeComputation before = engineFor(schedule).compute(sell(TURNOVER));
            if (before.lines().isEmpty()) {
                continue;
            }

            ChargeLine victim = before.lines().getFirst();
            if (isNamedAsABase(schedule, victim.getCode())) {
                continue;
            }

            ChargeScheduleEntity reduced = withoutRule(schedule, victim.getCode());
            ChargeComputation after = engineFor(reduced).compute(sell(TURNOVER));

            assertMoney("removing " + victim.getCode(),
                    before.total() - victim.getAmount(), after.total());
        }
    }

    @Test
    void scalingTurnoverScalesEveryTurnoverLineByTheSameFactor() {
        // Linearity, asserted without rounding: a percentage of twice the trade is twice the charge.
        Random random = new Random(SEED);
        for (int i = 0; i < CASES; i++) {
            ChargeScheduleEntity schedule = unroundedTurnoverCard(random);
            ChargeEngine engine = engineFor(schedule);

            ChargeComputation single = engine.compute(sell(TURNOVER));
            ChargeComputation tripled = engine.compute(sell(TURNOVER * 3));

            for (ChargeLine line : single.lines()) {
                assertMoney(line.getCode(), line.getAmount() * 3, tripled.amountOf(line.getCode()));
            }
        }
    }

    @Test
    void aCardWithNoRulesChargesNothingAndDoesNotThrow() {
        ChargeScheduleEntity empty = card();
        ChargeEngine engine = engineFor(empty);

        assertThatCode(() -> {
            ChargeComputation computation = engine.compute(sell(TURNOVER));
            assertThat(computation.lines()).isEmpty();
            assertMoney(0.0, computation.total());
        }).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------- harness

    private interface Property {
        void check(ChargeScheduleEntity schedule, ChargeComputation computation);
    }

    private void forEachGeneratedCard(Property property) {
        Random random = new Random(SEED);
        for (int i = 0; i < CASES; i++) {
            ChargeScheduleEntity schedule = generateCard(random);
            property.check(schedule, engineFor(schedule).compute(sell(TURNOVER)));
        }
    }

    private ChargeEngine engineFor(ChargeScheduleEntity schedule) {
        ChargeScheduleResolver scheduleResolver = mock(ChargeScheduleResolver.class);
        when(scheduleResolver.resolve(any())).thenReturn(Optional.of(schedule));
        return new ChargeEngine(scheduleResolver, mock(ChargeInstrumentResolver.class),
                realRegistry(), formulaEvaluator);
    }

    private ChargeCalculatorRegistry realRegistry() {
        when(userChargeRepository.existsChargeForScripOnDate(
                anyString(), anyString(), any(), anyString(), any(), anyString())).thenReturn(false);

        return new ChargeCalculatorRegistry(List.of(
                new TurnoverChargeCalculator(),
                new FlatChargeCalculator(),
                new PerUnitChargeCalculator(),
                new SlabChargeCalculator(),
                new ScopedFlatChargeCalculator(userChargeRepository),
                new DerivedChargeCalculator(),
                new FormulaChargeCalculator(formulaEvaluator)));
    }

    // ---------------------------------------------------------------- generation

    private static final List<String> CODES =
            List.of("BROKERAGE", "STT", "EXCHANGE_TXN", "SEBI_FEE", "IPFT", "STAMP_DUTY", "DP");

    /** A card of ordinary shape: several charges, then a tax on some of them. */
    private static ChargeScheduleEntity generateCard(Random random) {
        ChargeScheduleEntity schedule = card();
        List<ChargeRule> rules = new ArrayList<>();

        int count = 1 + random.nextInt(CODES.size());
        for (int index = 0; index < count; index++) {
            rules.add(generateRule(random, CODES.get(index), (index + 1) * 10));
        }

        if (random.nextBoolean() && rules.size() > 1) {
            List<String> base = rules.stream()
                    .filter(rule -> random.nextBoolean())
                    .map(ChargeRule::getCode)
                    .toList();
            if (!base.isEmpty()) {
                ChargeRule gst = rule("GST", ChargeBasis.DERIVED, 1000);
                gst.setRate(18.0);
                gst.setBaseCodes(base);
                rules.add(gst);
            }
        }

        schedule.setRules(rules);
        return schedule;
    }

    private static ChargeRule generateRule(Random random, String code, int order) {
        ChargeBasis basis = switch (random.nextInt(6)) {
            case 0 -> ChargeBasis.FLAT;
            case 1 -> ChargeBasis.PER_UNIT;
            case 2 -> ChargeBasis.SLAB;
            case 3 -> ChargeBasis.SCOPED_FLAT;
            case 4 -> ChargeBasis.FORMULA;
            default -> ChargeBasis.TURNOVER;
        };

        ChargeRule rule = rule(code, basis, order);
        rule.setActive(random.nextInt(10) > 0);
        rule.setSide(switch (random.nextInt(4)) {
            case 0 -> ChargeSide.BUY;
            case 1 -> ChargeSide.SELL;
            default -> ChargeSide.BOTH;
        });
        rule.setEvents(random.nextInt(10) > 0
                ? Set.of(ChargeEvent.BUY, ChargeEvent.SELL)
                : Set.of(ChargeEvent.BUY));

        switch (basis) {
            case TURNOVER -> rule.setRate(round(random.nextDouble() * 0.5));
            case FLAT, SCOPED_FLAT -> rule.setFlatAmount(round(random.nextDouble() * 50));
            case PER_UNIT -> rule.setPerUnitAmount(round(random.nextDouble()));
            case FORMULA -> rule.setFormula("#turnover * 0.001");
            case SLAB -> rule.setSlabs(List.of(
                    new ChargeSlab(0.0, 50000.0, null, round(random.nextDouble() * 20)),
                    new ChargeSlab(50000.0, null, null, round(random.nextDouble() * 40))));
            default -> throw new IllegalStateException("unreachable");
        }
        return rule;
    }

    /** Turnover-only and unrounded, so linearity can be asserted exactly. */
    private static ChargeScheduleEntity unroundedTurnoverCard(Random random) {
        ChargeScheduleEntity schedule = card();
        List<ChargeRule> rules = new ArrayList<>();

        int count = 1 + random.nextInt(4);
        for (int index = 0; index < count; index++) {
            ChargeRule rule = rule(CODES.get(index), ChargeBasis.TURNOVER, (index + 1) * 10);
            rule.setRate(round(random.nextDouble() * 0.5));
            rule.setRounding(RoundingPolicy.NONE);
            rules.add(rule);
        }

        schedule.setRules(rules);
        return schedule;
    }

    private static ChargeScheduleEntity card() {
        ChargeScheduleEntity schedule = new ChargeScheduleEntity();
        schedule.setId("sched-1");
        schedule.setScheduleCode("GENERATED");
        schedule.setRules(new ArrayList<>());
        return schedule;
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

    // ---------------------------------------------------------------- helpers

    private static ChargeContext sell(double turnover) {
        return ChargeFixtures.trade(turnover);
    }

    private static List<String> codes(ChargeComputation computation) {
        return computation.lines().stream().map(ChargeLine::getCode).toList();
    }

    private static ChargeRule ruleFor(ChargeScheduleEntity schedule, String code) {
        return schedule.getRules().stream()
                .filter(rule -> rule.getCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("charged a code the card does not declare: " + code));
    }

    private static boolean isNamedAsABase(ChargeScheduleEntity schedule, String code) {
        return schedule.getRules().stream()
                .filter(rule -> rule.getBaseCodes() != null)
                .anyMatch(rule -> rule.getBaseCodes().contains(code));
    }

    private static ChargeScheduleEntity withoutRule(ChargeScheduleEntity schedule, String code) {
        ChargeScheduleEntity reduced = card();
        reduced.setRules(schedule.getRules().stream()
                .filter(rule -> !rule.getCode().equals(code))
                .toList());
        return reduced;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
