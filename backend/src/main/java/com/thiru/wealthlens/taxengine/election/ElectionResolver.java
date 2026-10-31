package com.thiru.wealthlens.taxengine.election;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeRegistry;
import com.thiru.wealthlens.taxengine.pipeline.PipelineRunner;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Resolves an election by recomputation ({@code DR 15} C2, {@code D-MARGINAL}): never by a heuristic.
 *
 * <p>A stated choice is computed once, each choice under the regime it names ({@link Election#regimeFor}; D-222). Otherwise each choice is a whole pipeline run and the one with the least
 * {@code tax.netpayable} is taken; a tie goes to the earlier choice, so to the declared default where it is among the
 * cheapest. Whole runs, because the in-run {@code Recomputation} (D-086) seeds what it does not rerun from the run's
 * values so far, which an election read early in the graph does not yet have; a whole run costs milliseconds, and
 * reusing part of one needs input reads the graph does not yet record (D-235, W-143a). The resolver sits outside the graph, so it runs the pipeline itself (D-220).
 *
 * <p>Every alternative becomes a MEASURE line of the election's basis rule under its node, appended to the chosen
 * run's trace — the outputs are projections of the trace ({@code D-VIEWFROMTRACE}), so an alternative kept anywhere
 * else would be one no view could show.
 *
 * <p>Elections are not independent — the regime changes what a house specification is worth (D-230) — so several are
 * resolved together over every combination of their choices, not one after another (D-236). Each election's outcome
 * then prices each of its choices at the best combination that makes it, so its lines number its choices, not the
 * combinations.
 */
public final class ElectionResolver {

    private ElectionResolver() {}

    /**
     * An election resolved.
     *
     * @param outcome the choice and the alternatives priced
     * @param run the computation with the choice made, its trace ending in the alternatives' lines
     * @param <T> what is chosen
     */
    public record Resolution<T>(ElectionOutcome<T> outcome, PipelineRunner.Result run) {

        public Resolution {
            if (outcome == null || run == null) {
                throw new IllegalArgumentException("A resolution needs its outcome and its run");
            }
        }
    }

    /**
     * Several elections resolved together.
     *
     * @param outcomes each election's outcome, in the order the elections were given
     * @param run the computation with every choice made, its trace ending in the alternatives' lines
     */
    public record JointResolution(List<ElectionOutcome<?>> outcomes, PipelineRunner.Result run) {

        public JointResolution {
            if (outcomes == null || run == null) {
                throw new IllegalArgumentException("A joint resolution needs its outcomes and its run");
            }
            outcomes = List.copyOf(outcomes);
        }
    }

    /**
     * Resolve one election for one computation.
     *
     * @throws IllegalArgumentException if anything is missing, or the election offers no choice
     */
    public static <T> Resolution<T> resolve(
            Election<T> election, NodeRegistry registry, RulePack pack, RegimeSpec regime, EngineInputs inputs) {
        if (election == null || registry == null || pack == null || regime == null || inputs == null) {
            throw new IllegalArgumentException("Resolving an election needs the election, a registry, a pack, a regime"
                    + " and the inputs; the engine fetches none of them for itself");
        }
        NodeContext rules = rulesFor(election, registry, pack, regime, inputs);
        Optional<T> stated = election.stated(inputs);
        List<T> choices = stated.map(List::of).orElseGet(() -> election.candidates(rules));
        if (choices.isEmpty()) {
            throw new IllegalArgumentException(election.getClass().getSimpleName() + " offers no choice to price");
        }
        List<PricedAlternative<T>> priced = new ArrayList<>(choices.size());
        PipelineRunner.Result cheapest = null;
        T chosen = null;
        for (T choice : choices) {
            PipelineRunner.Result run = PipelineRunner.run(registry, pack, election.regimeFor(choice, regime, pack),
                    election.choosing(choice, inputs));
            Money netPayable = run.valueOf(NodeIds.TAX_NETPAYABLE).asMoney();
            priced.add(new PricedAlternative<>(choice, netPayable));
            if (cheapest == null || netPayable.isLessThan(cheapest.valueOf(NodeIds.TAX_NETPAYABLE).asMoney())) {
                cheapest = run;
                chosen = choice;
            }
        }
        ElectionMode mode = stated.isPresent() ? ElectionMode.USER_SPECIFIED : ElectionMode.AUTO_OPTIMISE;
        ElectionOutcome<T> outcome = new ElectionOutcome<>(chosen, mode, priced);
        return new Resolution<>(outcome, withAlternatives(cheapest, election, outcome, rules));
    }

    /**
     * Resolve several elections together: every combination of their choices is a whole run — each election's choices
     * in its order, a stated election only its stated choice, the regime the one the choices name — and the one with
     * the least {@code tax.netpayable} is taken. A tie goes to the earlier combination, the first election's choice
     * the most significant, so to the declared defaults where they are among the cheapest.
     *
     * @throws IllegalArgumentException if anything is missing, fewer than two elections are given, two are of one
     *     kind, or one offers no choice
     */
    public static JointResolution resolveTogether(
            List<Election<?>> elections, NodeRegistry registry, RulePack pack, RegimeSpec regime, EngineInputs inputs) {
        requireJointlyResolvable(elections, registry, pack, regime, inputs);
        List<Choices<?>> all = IntStream.range(0, elections.size())
                .<Choices<?>>mapToObj(i -> Choices.of(elections.get(i), i,
                        rulesFor(elections.get(i), registry, pack, regime, inputs)))
                .toList();
        int[] radix = all.stream().mapToInt(Choices::size).toArray();
        int combinations = Arrays.stream(radix).reduce(1, Math::multiplyExact);
        PipelineRunner.Result cheapest = null;
        int[] chosen = null;
        for (int combination = 0; combination < combinations; combination++) {
            int[] picks = digits(combination, radix);
            Combination folded = combination(all, picks, regime, inputs, pack);
            PipelineRunner.Result run = PipelineRunner.run(registry, pack, folded.regime(), folded.inputs());
            Money netPayable = run.valueOf(NodeIds.TAX_NETPAYABLE).asMoney();
            all.forEach(choices -> choices.price(picks, netPayable));
            if (cheapest == null || netPayable.isLessThan(cheapest.valueOf(NodeIds.TAX_NETPAYABLE).asMoney())) {
                cheapest = run;
                chosen = picks;
            }
        }
        List<TraceLine> trace = new ArrayList<>(cheapest.trace());
        List<ElectionOutcome<?>> outcomes = new ArrayList<>(all.size());
        for (int i = 0; i < all.size(); i++) {
            outcomes.add(all.get(i).outcome(chosen[i], trace, all, combinations, pack));
        }
        return new JointResolution(outcomes, withTrace(cheapest, trace));
    }

    private static void requireJointlyResolvable(List<Election<?>> elections, NodeRegistry registry, RulePack pack,
            RegimeSpec regime, EngineInputs inputs) {
        if (elections == null || registry == null || pack == null || regime == null || inputs == null) {
            throw new IllegalArgumentException("Resolving elections together needs the elections, a registry, a pack,"
                    + " a regime and the inputs; the engine fetches none of them for itself");
        }
        if (elections.size() < 2 || elections.stream().map(Object::getClass).distinct().count() != elections.size()) {
            throw new IllegalArgumentException("Resolving together needs two or more elections of different kinds, but"
                    + " was given " + elections.stream().map(e -> e.getClass().getSimpleName()).toList()
                    + "; resolve one alone");
        }
    }

    /**
     * The rules as the election's node reads them, in the computing regime: an election reaches a row only through a
     * set its node declares, chosen as the node chooses (D-255).
     */
    private static NodeContext rulesFor(Election<?> election, NodeRegistry registry, RulePack pack, RegimeSpec regime,
            EngineInputs inputs) {
        return NodeContext.forRun(pack, regime, inputs).forNode(registry.producing(election.node()));
    }

    /** The regime one combination of choices names, and the inputs with every choice made. */
    private record Combination(RegimeSpec regime, EngineInputs inputs) {
    }

    /** One combination folded: each election's pick applied in turn to the inputs and the regime. */
    private static Combination combination(List<Choices<?>> all, int[] picks, RegimeSpec regime, EngineInputs inputs,
            RulePack pack) {
        EngineInputs choosing = inputs;
        RegimeSpec computing = regime;
        for (int i = 0; i < all.size(); i++) {
            choosing = all.get(i).choosing(picks[i], choosing);
            computing = all.get(i).regimeFor(picks[i], computing, pack);
        }
        return new Combination(computing, choosing);
    }

    /** {@code combination}'s choice index for each election, the last election's varying fastest. */
    private static int[] digits(int combination, int[] radix) {
        int[] picks = new int[radix.length];
        int rest = combination;
        for (int i = radix.length - 1; i >= 0; i--) {
            picks[i] = rest % radix[i];
            rest /= radix[i];
        }
        return picks;
    }

    /**
     * One election's choices in a joint resolution, and the best combination found so far for each — its least net
     * payable, the earlier combination keeping a tie.
     */
    private static final class Choices<T> {

        private final Election<T> election;
        private final NodeContext rules;
        private final int position;
        private final List<T> choices;
        private final boolean stated;
        private final List<Money> best;
        private final List<int[]> bestPicks;

        private Choices(Election<T> election, NodeContext rules, int position, List<T> choices, boolean stated) {
            this.election = election;
            this.rules = rules;
            this.position = position;
            this.choices = choices;
            this.stated = stated;
            this.best = new ArrayList<>(Collections.nCopies(choices.size(), null));
            this.bestPicks = new ArrayList<>(Collections.nCopies(choices.size(), null));
        }

        static <T> Choices<T> of(Election<T> election, int position, NodeContext rules) {
            Optional<T> stated = election.stated(rules.inputs());
            List<T> choices = stated.map(List::of).orElseGet(() -> election.candidates(rules));
            if (choices.isEmpty()) {
                throw new IllegalArgumentException(election.getClass().getSimpleName() + " offers no choice to price");
            }
            return new Choices<>(election, rules, position, List.copyOf(choices), stated.isPresent());
        }

        int size() {
            return choices.size();
        }

        EngineInputs choosing(int pick, EngineInputs inputs) {
            return election.choosing(choices.get(pick), inputs);
        }

        RegimeSpec regimeFor(int pick, RegimeSpec computing, RulePack pack) {
            return election.regimeFor(choices.get(pick), computing, pack);
        }

        String describe(int pick, RulePack pack) {
            return election.describe(choices.get(pick), pack);
        }

        /** Record a combination's net payable against this election's choice in it. */
        void price(int[] picks, Money netPayable) {
            int pick = picks[position];
            if (best.get(pick) == null || netPayable.isLessThan(best.get(pick))) {
                best.set(pick, netPayable);
                bestPicks.set(pick, picks);
            }
        }

        /** The outcome, its lines appended to {@code trace}: one per choice, at its best combination. */
        ElectionOutcome<T> outcome(int chosen, List<TraceLine> trace, List<Choices<?>> all, int combinations,
                RulePack pack) {
            List<PricedAlternative<T>> priced = new ArrayList<>(choices.size());
            for (int pick = 0; pick < choices.size(); pick++) {
                priced.add(new PricedAlternative<>(choices.get(pick), best.get(pick)));
            }
            ElectionOutcome<T> outcome = new ElectionOutcome<>(choices.get(chosen),
                    stated ? ElectionMode.USER_SPECIFIED : ElectionMode.AUTO_OPTIMISE, priced);
            for (int pick = 0; pick < choices.size(); pick++) {
                String why = stated ? why(election, outcome, priced.get(pick), pack)
                        : jointly(pick, chosen, all, combinations, pack);
                trace.add(line(trace.size() + 1, election, priced.get(pick), why, rules));
            }
            return outcome;
        }

        private String jointly(int pick, int chosen, List<Choices<?>> all, int combinations, RulePack pack) {
            List<String> others = new ArrayList<>();
            for (Choices<?> other : all) {
                if (other != this) {
                    others.add(other.describe(bestPicks.get(pick)[other.position], pack));
                }
            }
            return describe(pick, pack) + ": net payable, the other elections at their best for it ("
                    + String.join("; ", others) + "); " + (pick == chosen ? "chosen" : "not chosen")
                    + " — priced jointly by recomputation against " + combinations + " combinations, the least taken,"
                    + " a tie going to the earlier, every declared default first";
        }
    }

    /** The run, its trace ending in one line per alternative priced. */
    private static <T> PipelineRunner.Result withAlternatives(PipelineRunner.Result run, Election<T> election,
            ElectionOutcome<T> outcome, NodeContext rules) {
        List<TraceLine> trace = new ArrayList<>(run.trace());
        for (PricedAlternative<T> alternative : outcome.alternatives()) {
            trace.add(line(trace.size() + 1, election, alternative, why(election, outcome, alternative, rules.pack()),
                    rules));
        }
        return withTrace(run, trace);
    }

    /** An alternative's MEASURE line: its net payable, under the election's node, citing the choice's basis. */
    private static <T> TraceLine line(int sequence, Election<T> election, PricedAlternative<T> alternative, String why,
            NodeContext rules) {
        RuleId basis = election.basis(alternative.choice(), rules);
        Provenance provenance = TemporallyApportionedCap.governingVersion(rules.pack().rule(basis),
                rules.inputs().temporal()).provenance();
        MoneyValue netPayable = MoneyValue.of(alternative.netPayable());
        return TraceLine.of(sequence, election.node(), basis, StepKind.MEASURE, netPayable, netPayable, provenance)
                .withLimitedBy(why);
    }

    private static PipelineRunner.Result withTrace(PipelineRunner.Result run, List<TraceLine> trace) {
        return new PipelineRunner.Result(run.order(), run.values(), trace, run.annotations(), run.inputs(),
                run.lawAndExec());
    }

    private static <T> String why(Election<T> election, ElectionOutcome<T> outcome, PricedAlternative<T> alternative,
            RulePack pack) {
        String choice = election.describe(alternative.choice(), pack) + ": net payable";
        if (outcome.mode() == ElectionMode.USER_SPECIFIED) {
            return choice + "; stated by the assessee, applied as stated and not priced against the others";
        }
        boolean taken = alternative.choice().equals(outcome.chosen());
        return choice + "; " + (taken ? "chosen" : "not chosen") + " — priced by recomputation against "
                + outcome.alternatives().size() + " choices, the least taken, a tie going to the earlier, the declared"
                + " default first";
    }
}
