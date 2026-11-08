package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxengine.api.Optimisation;
import java.util.List;
import java.util.stream.Stream;

/**
 * What the optimiser suggests for a draft, with how likely the employer is to agree to each move (A2.6; W-238f C1a).
 * The engine's answer passes through unchanged; HR support is {@code taxplanning}'s product data, so it is joined
 * here, never in the engine.
 *
 * @param optimisation the engine's answer
 * @param hrSupport one entry per changed component, in the answer's order, then per changed fund. An amount that only
 *     follows another has none of its own: it moves with what it follows (W-238d). Nor has an employee's own
 *     contribution: it is the employee's declaration, not a structure the employer agrees to (W-238k C5a)
 */
public record DraftOptimisation(Optimisation optimisation, List<HrSupport> hrSupport) {

    public DraftOptimisation {
        hrSupport = List.copyOf(hrSupport);
    }

    /** The answer with each move's value from {@code table}. */
    static DraftOptimisation of(Optimisation optimisation, HrSupportTable table) {
        List<HrSupport> support = Stream.concat(
                        optimisation.changes().pay().stream().map(Optimisation.Change::component),
                        optimisation.changes().funds().stream().map(Optimisation.FundChange::fund))
                .map(name -> new HrSupport(name, table.likelihoodOf(name)))
                .toList();
        return new DraftOptimisation(optimisation, support);
    }

    /**
     * One move's HR support.
     *
     * @param name the component's code or the fund's {@code EmployerFund} name, as the change names it
     * @param likelihood the table's value; the table covers every name that can move (W-238f C3a, W-238g C4a)
     */
    public record HrSupport(String name, HrSupportLikelihood likelihood) {
    }
}
