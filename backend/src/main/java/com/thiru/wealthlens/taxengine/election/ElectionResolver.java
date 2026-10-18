package com.thiru.wealthlens.taxengine.election;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
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
import java.util.List;
import java.util.Optional;

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
        Optional<T> stated = election.stated(inputs);
        List<T> choices = stated.map(List::of).orElseGet(() -> election.candidates(inputs, pack));
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
        return new Resolution<>(outcome, withAlternatives(cheapest, election, outcome, pack, inputs));
    }

    /** The run, its trace ending in one line per alternative priced. */
    private static <T> PipelineRunner.Result withAlternatives(PipelineRunner.Result run, Election<T> election,
            ElectionOutcome<T> outcome, RulePack pack, EngineInputs inputs) {
        List<TraceLine> trace = new ArrayList<>(run.trace());
        for (PricedAlternative<T> alternative : outcome.alternatives()) {
            RuleId basis = election.basis(alternative.choice(), pack);
            Provenance provenance =
                    TemporallyApportionedCap.governingVersion(pack.rule(basis), inputs.temporal()).provenance();
            MoneyValue netPayable = MoneyValue.of(alternative.netPayable());
            trace.add(TraceLine.of(trace.size() + 1, election.node(), basis, StepKind.MEASURE, netPayable, netPayable,
                    provenance).withLimitedBy(why(election, outcome, alternative, pack)));
        }
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
