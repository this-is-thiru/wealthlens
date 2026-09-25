package com.thiru.wealthlens.taxengine.result;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.InputsFingerprint;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeValue;
import com.thiru.wealthlens.taxengine.pipeline.PipelineRunner;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;

/**
 * A pipeline run as the durable, date-independent {@link TaxComputation} — the one place that says which
 * slot is which field (STATE Q3, D-081).
 *
 * <ul>
 *   <li>{@code totalIncome} is {@code round.totalincome}: the rounded amount "shall be deemed to be the
 *       total income" (S2 s.516).
 *   <li>{@code taxByBasis} is {@code tax.bybasis}, the tax before rebate: the split exists because the
 *       rebate reads one part and not the other.
 *   <li>{@code netPayableBeforeFilingEffects} is {@code tax.netpayable}, unrounded: {@code round.netpayable}
 *       follows interest and fees, which move with the filing date, and rounding here would add a third
 *       rounding point.
 * </ul>
 */
public final class ComputationProjector {

    private ComputationProjector() {
    }

    /**
     * Projects a run computed from {@code inputs}.
     *
     * @param id supplied by the caller, so identical inputs project to equal computations bar the id
     * @throws IllegalArgumentException if the inputs are not the ones the run was computed from, or an
     *     anchor slot is missing or holds the wrong kind of value
     */
    public static TaxComputation project(PipelineRunner.Result run, EngineInputs inputs, ComputationId id) {
        if (!run.inputs().equals(InputsFingerprint.of(inputs))) {
            throw new IllegalArgumentException("The inputs given do not match the run's fingerprint " + run.inputs()
                    + "; a run is projected only under the year and regime it was computed for");
        }
        return new TaxComputation(
                id,
                inputs.taxYear(),
                inputs.regime(),
                run.lawAndExec(),
                run.inputs(),
                run.trace(),
                anchor(run, NodeIds.ROUND_TOTALINCOME, MoneyValue.class).amount(),
                anchor(run, NodeIds.TAX_BYBASIS, TaxByBasis.class),
                anchor(run, NodeIds.TAX_NETPAYABLE, MoneyValue.class).amount());
    }

    private static <T extends NodeValue> T anchor(PipelineRunner.Result run, NodeId slot, Class<T> kind) {
        NodeValue value = run.valueOf(slot);
        if (!kind.isInstance(value)) {
            throw new IllegalArgumentException("Anchor " + slot + " holds a " + value.getClass().getSimpleName()
                    + ", not a " + kind.getSimpleName());
        }
        return kind.cast(value);
    }
}
