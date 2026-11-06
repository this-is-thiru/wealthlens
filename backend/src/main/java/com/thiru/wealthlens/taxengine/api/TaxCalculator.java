package com.thiru.wealthlens.taxengine.api;

import com.thiru.wealthlens.taxengine.election.ElectionResolver;
import com.thiru.wealthlens.taxengine.election.RegimeElection;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.node.CapitalGainsNode;
import com.thiru.wealthlens.taxengine.node.CapitalGainsTerm;
import com.thiru.wealthlens.taxengine.node.ShippedNodes;
import com.thiru.wealthlens.taxengine.pipeline.AdvanceTaxPosition;
import com.thiru.wealthlens.taxengine.pipeline.BucketOutcome;
import com.thiru.wealthlens.taxengine.pipeline.CapitalGainsHead;
import com.thiru.wealthlens.taxengine.pipeline.InterestAndFees;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeRegistry;
import com.thiru.wealthlens.taxengine.pipeline.PipelineRunner;
import com.thiru.wealthlens.taxengine.result.ComputationId;
import com.thiru.wealthlens.taxengine.result.ComputationProjector;
import com.thiru.wealthlens.taxengine.result.ComputationReceipt;
import com.thiru.wealthlens.taxengine.result.TaxComputation;
import com.thiru.wealthlens.taxengine.rules.definition.LossCarryForward;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackProvider;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
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
                ((MoneyValue) run.valueOf(NodeIds.CREDITS)).amount(), advanceTax((AdvanceTaxPosition) run.valueOf(NodeIds.ADVANCE_TAX)),
                owed((InterestAndFees) run.valueOf(NodeIds.INTEREST_AND_FEES)),
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

    /**
     * The term of a lot with these facts, by the row the year's pack holds for it: what a sale on the facts' date would be (W-160g).
     * The term alone, since whether anything is charged turns on a sale that has not happened.
     *
     * @param inputs the year and regime, which choose the rows
     * @throws IllegalArgumentException where no row covers the lot
     */
    public HeldTerm termIfSold(EngineInputs inputs, CapitalGainsInput.BucketFacts facts) {
        RulePack pack = rulePacks.load(inputs.taxYear());
        NodeContext context = NodeContext.forRun(pack, spec(pack, inputs.regime()), inputs).forNode(new CapitalGainsNode());
        CapitalGainsInput.Bucket bucket = new CapitalGainsInput.Bucket(Money.ZERO, Money.ZERO, List.of(), facts, Optional.empty());
        BucketOutcome.TermDecision decision = CapitalGainsTerm.of(context, bucket, Money.ZERO, new ArrayList<>());
        return new HeldTerm(decision.term().name(), decision.row().value(), decision.months());
    }

    /**
     * A held lot's term.
     *
     * @param term long or short
     * @param row the {@code RS-CG-TERM} row it met
     * @param months that row's holding period
     */
    public record HeldTerm(String term, String row, int months) {
    }

    /** The regime the year's pack calls its default. */
    public RegimeCode defaultRegime(TaxYearId year) {
        return rulePacks.load(year).regimes().values().stream().filter(RegimeSpec::isDefault).map(RegimeSpec::code)
                .findFirst().orElseThrow(() -> new IllegalStateException("The pack for " + year.label() + " has no default"));
    }

    /**
     * The most tax years after the one a loss arose in that the year's pack lets it be carried forward, across every
     * {@link LossCarryForward} row and version — the period a filed computation may have to explain (D-447 C6a, W-137). Empty
     * where the application ships no pack for the year, or the pack carries no loss forward, so a caller can refuse rather
     * than guess.
     */
    public OptionalInt longestCarryForward(TaxYearId year) {
        if (!rulePacks.availableYears().contains(year)) {
            return OptionalInt.empty();
        }
        return rulePacks.load(year).rules().values().stream()
                .flatMap(row -> row.versions().stream())
                .flatMap(version -> version.definition().stream())
                .filter(LossCarryForward.class::isInstance)
                .map(LossCarryForward.class::cast)
                .mapToInt(carry -> carry.span().succeedingYears())
                .max();
    }

    /**
     * The year's law and exec layer versions, in the form {@link ComputedTax#lawAndExec()} pins them: a stored computation whose pins
     * differ was made under law that has since moved (W-138, {@code 13-edge-cases.md} R-8). Empty where no pack ships for the year.
     */
    public Optional<List<String>> packVersions(TaxYearId year) {
        if (!rulePacks.availableYears().contains(year)) {
            return Optional.empty();
        }
        return Optional.of(rulePacks.load(year).layers().values().stream().filter(ref -> ref.layer().affectsComputation())
                .map(Object::toString).sorted().toList());
    }

    /**
     * The current checksum of each of {@code ruleIds} the year's pack holds — a rule it no longer holds is left out, so a caller
     * comparing against what it pinned sees that rule as changed (W-138).
     */
    public Map<String, String> ruleChecksums(TaxYearId year, Set<String> ruleIds) {
        Map<String, String> current = new TreeMap<>();
        rulePacks.ruleChecksums(year).forEach((rule, checksum) -> {
            if (ruleIds.contains(rule.value())) {
                current.put(rule.value(), checksum);
            }
        });
        return current;
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
            return new ComputedTax.Bucket(outcome.facts(), outcome.gain(), outcome.charges().stream()
                    .map(c -> new ComputedTax.Bucket.Charge(c.side(), c.code(), c.amount(), c.limb())).toList(), decision.map(d -> d.term().name()).orElse(null),
                    decision.map(d -> d.row().value()).orElse(null), decision.map(BucketOutcome.TermDecision::months).orElse(null),
                    outcome.leftOutBy().map(RuleId::value).orElse(null));
        }).toList();
    }

    private static ComputedTax.AdvanceTax advanceTax(AdvanceTaxPosition position) {
        return new ComputedTax.AdvanceTax(position.liable(), position.amount(), position.instalments().stream()
                .map(i -> new ComputedTax.AdvanceTax.Instalment(i.dueOn(), i.dueByThen(), i.paidByThen(), i.stillOwed())).toList());
    }

    private static List<ComputedTax.Owed> owed(InterestAndFees owed) {
        return owed.items().stream().map(item -> new ComputedTax.Owed(item.rule().value(), item.kind().name(), item.amount()))
                .toList();
    }

    private static List<ComputedTax.Step> steps(TaxComputation computation) {
        return computation.trace().stream()
                .map(line -> new ComputedTax.Step(line.sequence(), line.node().value(), line.rule().value(), line.kind().name(),
                        line.limitedBy().orElse("")))
                .toList();
    }
}
