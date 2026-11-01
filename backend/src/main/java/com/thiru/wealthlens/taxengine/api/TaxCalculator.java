package com.thiru.wealthlens.taxengine.api;

import com.thiru.wealthlens.taxengine.election.ElectionResolver;
import com.thiru.wealthlens.taxengine.election.RegimeElection;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.node.ShippedNodes;
import com.thiru.wealthlens.taxengine.pipeline.BucketOutcome;
import com.thiru.wealthlens.taxengine.pipeline.CapitalGainsHead;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeRegistry;
import com.thiru.wealthlens.taxengine.pipeline.PipelineRunner;
import com.thiru.wealthlens.taxengine.result.ComputationId;
import com.thiru.wealthlens.taxengine.result.ComputationProjector;
import com.thiru.wealthlens.taxengine.result.ComputationReceipt;
import com.thiru.wealthlens.taxengine.result.TaxComputation;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackProvider;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Runs the shipped pipeline. The engine reads no clock and fetches nothing: everything arrives in {@link EngineInputs}. */
@Component
@RequiredArgsConstructor
public class TaxCalculator {

    private static final NodeRegistry REGISTRY = NodeRegistry.of(ShippedNodes.all());

    private final RulePackProvider rulePacks;

    /**
     * The inputs' year under their regime.
     *
     * @throws IllegalArgumentException if the pack has no such regime, or the inputs are refused
     */
    public ComputedTax compute(EngineInputs inputs) {
        RulePack pack = rulePacks.load(inputs.taxYear());
        PipelineRunner.Result run = PipelineRunner.run(REGISTRY, pack, spec(pack, inputs.regime()), inputs);
        ComputationId id = ComputationId.fresh();
        TaxComputation computation = ComputationProjector.project(run, inputs, id);
        return new ComputedTax(id.value(), ComputationReceipt.of(id, inputs.taxYear()).value(), inputs.taxYear().label(),
                inputs.regime().value(), computation.totalIncome(), computation.netPayableBeforeFilingEffects(),
                ((MoneyValue) run.valueOf(NodeIds.CREDITS)).amount(),
                ((MoneyValue) run.valueOf(NodeIds.ROUND_NETPAYABLE)).amount(), computation.inputs().sha256(),
                computation.lawAndExec().values().stream().map(Object::toString).sorted().toList(), steps(computation),
                buckets((CapitalGainsHead) run.valueOf(NodeIds.CAPITALGAINS_INCOME)));
    }

    /** Every candidate regime priced on these inputs. */
    public RegimeComparison compareRegimes(EngineInputs inputs) {
        RulePack pack = rulePacks.load(inputs.taxYear());
        var outcome = ElectionResolver.resolve(new RegimeElection(), REGISTRY, pack, spec(pack, inputs.regime()), inputs)
                .outcome();
        return new RegimeComparison(outcome.chosen().value(), outcome.mode().name(), outcome.alternatives().stream()
                .map(priced -> new RegimeComparison.Priced(priced.choice().value(), priced.netPayable())).toList());
    }

    /** The regime the year's pack calls its default. */
    public RegimeCode defaultRegime(TaxYearId year) {
        return rulePacks.load(year).regimes().values().stream().filter(RegimeSpec::isDefault).map(RegimeSpec::code)
                .findFirst().orElseThrow(() -> new IllegalStateException("The pack for " + year.label() + " has no default"));
    }

    private static RegimeSpec spec(RulePack pack, RegimeCode code) {
        RegimeSpec spec = pack.regimes().get(code);
        if (spec == null) {
            throw new IllegalArgumentException("The tax year has no regime " + code.value());
        }
        return spec;
    }

    private static List<ComputedTax.Bucket> buckets(CapitalGainsHead head) {
        return head.bucketOutcomes().stream().map(outcome -> {
            Optional<BucketOutcome.TermDecision> decision = outcome.term();
            return new ComputedTax.Bucket(outcome.facts(), outcome.gain(), decision.map(d -> d.term().name()).orElse(null),
                    decision.map(d -> d.row().value()).orElse(null), decision.map(BucketOutcome.TermDecision::months).orElse(null),
                    outcome.leftOutBy().map(RuleId::value).orElse(null));
        }).toList();
    }

    private static List<ComputedTax.Step> steps(TaxComputation computation) {
        return computation.trace().stream()
                .map(line -> new ComputedTax.Step(line.sequence(), line.node().value(), line.rule().value(), line.kind().name(),
                        line.limitedBy().orElse("")))
                .toList();
    }
}
