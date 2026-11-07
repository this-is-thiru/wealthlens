package com.thiru.wealthlens.taxengine.golden;

import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.rules.cap.SlabLadder;
import com.thiru.wealthlens.taxengine.rules.condition.AgeAtLeast;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.relief.ExcessOverThreshold;
import com.thiru.wealthlens.taxengine.rules.relief.FlatWithCeiling;
import com.thiru.wealthlens.taxengine.rules.relief.NoRelief;
import com.thiru.wealthlens.taxengine.rules.relief.ThresholdLadderCeiling;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import lombok.RequiredArgsConstructor;

/**
 * The total incomes at which a regime's tax changes behaviour, read from the year's pack ({@code DR 15} C5; W-140, C1a):
 * each rebate ceiling, each surcharge threshold and each age variant's first-band ceiling, with the next income on the
 * rounding grid, and the last and first grid incomes either side of where a marginal relief stops binding (C2a: found by
 * running the engine, never by a second copy of its arithmetic). It yields <b>inputs</b> only; what each should pay is
 * worked by hand in a T1/T2 case ({@code D-GOLDEN}).
 */
@RequiredArgsConstructor
final class BoundaryCaseGenerator {

    /** The rule whose rounding step is the grid total income moves on (s.288A; the 2025 Act's equivalent). */
    private static final RuleId TOTAL_INCOME_ROUNDING = new RuleId("RATE-ROUND-TOTAL-INCOME");

    /**
     * One boundary.
     *
     * @param ageAtLeast the age the regime's slab ladder for this boundary starts at; 0 for the ladder with no age condition
     * @param kind what it is, for the reader of a failure
     */
    record Boundary(RegimeCode regime, TaxYearId year, Money totalIncome, int ageAtLeast, String kind, RuleId rule) {
    }

    private final GoldenCaseRunner runner;

    List<Boundary> generate(TaxYearId year) {
        RulePack pack = runner.pack(year);
        Money step = pack.rule(TOTAL_INCOME_ROUNDING).versions().getFirst().rounding().orElseThrow().toNearest();
        List<Boundary> boundaries = new ArrayList<>();
        for (RegimeCode regime : pack.regimes().keySet()) {
            for (RuleRow row : pack.rules().values()) {
                for (RuleVersion version : row.versions()) {
                    if (version.regimes().appliesIn(regime)) {
                        boundariesOf(new Scope(regime, year, step, row.id()), version, boundaries);
                    }
                }
            }
        }
        return List.copyOf(boundaries);
    }

    /** Where one rule version is read: its regime, year, the grid step and the rule. */
    private record Scope(RegimeCode regime, TaxYearId year, Money step, RuleId rule) {

        Boundary at(Money income, int ageAtLeast, String kind) {
            return new Boundary(regime, year, income, ageAtLeast, kind, rule);
        }
    }

    private void boundariesOf(Scope scope, RuleVersion version, List<Boundary> out) {
        version.relief().map(relief -> relief.shape()).ifPresent(shape -> {
            switch (shape) {
                case FlatWithCeiling flat -> atAndAbove(scope, flat.incomeCeiling(), 0, "rebate ceiling", out);
                case ExcessOverThreshold excess -> crossover(scope, excess.threshold(), Optional.empty(), out);
                case ThresholdLadderCeiling ladder -> {
                    List<Money> thresholds = ladder.thresholds();
                    for (int i = 0; i < thresholds.size(); i++) {
                        atAndAbove(scope, thresholds.get(i), 0, "surcharge threshold", out);
                        crossover(scope, thresholds.get(i),
                                i + 1 < thresholds.size() ? Optional.of(thresholds.get(i + 1)) : Optional.empty(), out);
                    }
                }
                case NoRelief none -> {
                }
            }
        });
        OptionalInt age = ageAtLeast(version);
        if (age.isPresent() && version.cap().orElse(null) instanceof SlabLadder ladder) {
            ladder.bands().getFirst().upperInclusive().ifPresent(ceiling ->
                    atAndAbove(scope, ceiling, age.getAsInt(), "basic exemption, age " + age.getAsInt() + "+", out));
        }
    }

    private static OptionalInt ageAtLeast(RuleVersion version) {
        return version.conditions().stream().filter(AgeAtLeast.class::isInstance).map(AgeAtLeast.class::cast)
                .mapToInt(AgeAtLeast::years).max();
    }

    private static void atAndAbove(Scope scope, Money income, int age, String kind, List<Boundary> out) {
        out.add(scope.at(income, age, kind));
        out.add(scope.at(income.plus(scope.step()), age, kind + ", one step above"));
    }

    /**
     * The last grid income above {@code threshold} at which the rule's relief is still given, and the next one. The relief
     * binds just above the threshold and stops before {@code limit} (the next threshold) or, with none, before twice the
     * threshold; bisection on the grid between them.
     */
    private void crossover(Scope scope, Money threshold, Optional<Money> limit, List<Boundary> out) {
        Money ceiling = limit.orElse(threshold.times(2));
        int low = 1;
        int high = 1;
        while (threshold.plus(scope.step().times(high)).isLessThan(ceiling) && reliefBinds(scope, threshold, high)) {
            low = high;
            high *= 2;
        }
        if (!reliefBinds(scope, threshold, low) || reliefBinds(scope, threshold, high)) {
            throw new IllegalStateException(scope.rule() + " at " + threshold + ": the relief does not stop binding between "
                    + threshold + " and " + ceiling + ", so its crossover cannot be found");
        }
        while (high - low > 1) {
            int middle = low + (high - low) / 2;
            if (reliefBinds(scope, threshold, middle)) {
                low = middle;
            } else {
                high = middle;
            }
        }
        out.add(scope.at(threshold.plus(scope.step().times(low)), 0, "relief crossover, last binding"));
        out.add(scope.at(threshold.plus(scope.step().times(high)), 0, "relief crossover, first not binding"));
    }

    private boolean reliefBinds(Scope scope, Money threshold, int steps) {
        Money income = threshold.plus(scope.step().times(steps));
        GoldenCase probe = new GoldenCase("boundary " + scope.rule() + " " + income, "W-140 bisection", scope.regime(),
                income, Optional.empty(), List.of(scope.year()), Money.ZERO);
        return runner.run(probe, scope.year()).result().trace().stream()
                .filter(line -> line.kind() == StepKind.RELIEF && line.rule().equals(scope.rule()))
                .anyMatch(line -> line.out() instanceof MoneyValue relief && relief.asMoney().isGreaterThan(Money.ZERO));
    }
}
