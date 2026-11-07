package com.thiru.wealthlens.taxengine.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.pipeline.PipelineRunner;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Relations that must hold at every income, checked through the real engine over a dense sweep ({@code DR 19}
 * E4.1, D-088) — so a missing, misplaced or over-generous relief fails even where no golden case sits.
 *
 * <p>The sweep is deterministic rather than random: a relief binds only in a narrow zone (about ₹1.6 lakh above
 * ₹50 lakh, {@code DR 08} T2-3), which random tries seldom reach and a ₹10,000 grid cannot miss. Every figure is
 * {@code tax.netpayable}, unrounded, so the ₹10 rounding adds no noise.
 */
class EngineMetamorphicTest {

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");
    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");

    private static final Money ONE_CRORE = Money.of("10000000");
    private static final Money SIX_CRORE = Money.of("60000000");
    private static final Money FINE_STEP = Money.of("10000");
    private static final Money COARSE_STEP = Money.of("50000");

    /**
     * Extra income never costs more than itself plus the cess on it: each relief caps tax and surcharge at the
     * extra income (W = U + V; s.156(2)(b)), and the cess is "four per cent. of such income-tax and surcharge"
     * (FA 2026 s.2(6), s.3(15)). Held as 104/100 so the comparison stays in whole multiples.
     */
    private static final int CEILING_PERCENT = 104;

    /**
     * The one lawful cliff: the First Schedule rebate has no marginal relief, so income just above ₹5,00,000
     * loses it whole (rulebook H-3; {@code DR 08} T1-O2, "Expected: ₹13,000" at ₹5,00,010).
     */
    private static final Money FIRST_SCHEDULE_REBATE_CEILING = Money.of("500000");

    private static final GoldenCaseRunner RUNNER = new GoldenCaseRunner();
    private static final Map<Cell, List<Point>> SWEEPS = new ConcurrentHashMap<>();

    /** One regime, year and age, swept from nil to {@code top}. */
    record Cell(RegimeCode regime, TaxYearId year, int ageAtYearEnd, Money top) {

        @Override
        public String toString() {
            return regime.value() + " " + year.label() + " age " + ageAtYearEnd + " to " + top;
        }
    }

    private record Point(Money income, Money payable, List<TraceLine> trace) {
    }

    /** The default taxpayer in both regimes to ₹6 crore; the First Schedule's age ladders where they differ. */
    static Stream<Cell> cells() {
        return Stream.of(TaxYearId.of(2025), TaxYearId.of(2026)).flatMap(year -> Stream.of(
                new Cell(NEW_TAX_REGIME, year, 40, SIX_CRORE),
                new Cell(FIRST_SCHEDULE_RATES, year, 40, SIX_CRORE),
                new Cell(FIRST_SCHEDULE_RATES, year, 65, ONE_CRORE),
                new Cell(FIRST_SCHEDULE_RATES, year, 82, ONE_CRORE)));
    }

    /** ₹10,000 steps to ₹1 crore, ₹50,000 beyond: every statutory threshold (5L … 5Cr) is on the grid. */
    private static List<Money> incomesTo(Money top) {
        List<Money> incomes = new ArrayList<>();
        for (Money income = Money.ZERO; !income.isGreaterThan(top);
                income = income.plus(income.isLessThan(ONE_CRORE) ? FINE_STEP : COARSE_STEP)) {
            incomes.add(income);
        }
        return incomes;
    }

    private static List<Point> sweep(Cell cell) {
        return SWEEPS.computeIfAbsent(cell, c -> incomesTo(c.top()).stream().map(income -> {
            GoldenCase at = new GoldenCase("sweep", "EngineMetamorphicTest " + c, c.regime(), income,
                    Optional.of(c.ageAtYearEnd()), List.of(c.year()), Money.ZERO);
            PipelineRunner.Result result = RUNNER.run(at, c.year()).result();
            return new Point(income, result.valueOf(NodeIds.TAX_NETPAYABLE).asMoney(), result.trace());
        }).toList());
    }

    private static boolean isTheFirstScheduleRebateCliff(Cell cell, Point below, Point above) {
        return cell.regime().equals(FIRST_SCHEDULE_RATES)
                && !below.income().isGreaterThan(FIRST_SCHEDULE_REBATE_CEILING)
                && above.income().isGreaterThan(FIRST_SCHEDULE_REBATE_CEILING);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cells")
    void payable_whenIncomeRises_neverFalls(Cell cell) {
        // Given
        List<Point> points = sweep(cell);

        // When
        List<String> falls = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            Point below = points.get(i - 1);
            Point above = points.get(i);
            if (above.payable().isLessThan(below.payable())) {
                falls.add(below.income() + " → " + below.payable() + " but " + above.income() + " → " + above.payable());
            }
        }

        // Then — an over-generous or misplaced relief is what makes more income pay less
        assertThat(falls).as("%s: payable fell as income rose", cell).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cells")
    void payable_whenIncomeRises_neverRisesByMoreThanTheIncomePlusCess(Cell cell) {
        // Given
        List<Point> points = sweep(cell);

        // When
        List<String> jumps = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            Point below = points.get(i - 1);
            Point above = points.get(i);
            Money extraIncome = above.income().minus(below.income());
            Money extraPayable = above.payable().minus(below.payable());
            if (extraPayable.times(100).isGreaterThan(extraIncome.times(CEILING_PERCENT))
                    && !isTheFirstScheduleRebateCliff(cell, below, above)) {
                jumps.add(below.income() + " → " + above.income() + " costs " + extraPayable + " more");
            }
        }

        // Then — a missing marginal relief is a cliff here (H-1), even though the payable still rises
        assertThat(jumps).as("%s: extra income cost more than itself plus cess", cell).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cells")
    void trace_whenSummed_reconcilesToThePayable(Cell cell) {
        // Given
        List<Point> points = sweep(cell);

        // When — the charges (slab tax, surcharge, cess) less the reliefs (rebates, surcharge relief)
        List<String> gaps = new ArrayList<>();
        for (Point point : points) {
            Money explained = Money.ZERO;
            for (TraceLine line : point.trace()) {
                if (line.kind() == StepKind.TAX) {
                    explained = explained.plus(line.out().asMoney());
                } else if (line.kind() == StepKind.RELIEF) {
                    explained = explained.minus(line.out().asMoney());
                }
            }
            if (!explained.equals(point.payable())) {
                gaps.add(point.income() + ": trace explains " + explained + ", payable is " + point.payable());
            }
        }

        // Then — P5: the trace accounts for the figure, every rupee of it
        assertThat(gaps).as("%s: the trace does not add up to the payable", cell).isEmpty();
    }
}
