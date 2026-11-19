package com.thiru.wealthlens.taxengine.optimise;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeRegistry;
import com.thiru.wealthlens.taxengine.pipeline.PipelineRunner;
import com.thiru.wealthlens.taxengine.rules.catalogue.CompiledClaim;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * What each claim in a computation is worth (W-118 C1a): the inputs run again with the claim's whole entry — its rule
 * and the rules it covers — withheld, and the two runs set side by side. Withholding the entry, not one rule, is the
 * owner's ruling (session 99): a cap limits a claim rather than granting it, so only the entry as a whole is the claim.
 *
 * <p>A claim is in the computation where a line of one of its rules is above nil. An entry whose rules give no
 * deduction line of their own — the reported donation's, a bar — is never in it, and is served unpriced.
 */
public final class ClaimPricing {

    private ClaimPricing() {
    }

    /**
     * A claim and its two measures.
     *
     * @param allowed what the claim takes off total income
     * @param worth what the claim takes off the bill
     * @param finalAmount the amount finally payable, rounded, as the inputs stand
     * @param finalAmountWithout the same with the claim withheld (W-121 C1a)
     */
    public record Measured(CompiledClaim claim, Money allowed, Money worth, Money finalAmount, Money finalAmountWithout) {
    }

    /** Each claim of the year with a line above nil, by rule id as the pack holds them, measured. */
    public static List<Measured> price(NodeRegistry registry, RulePack pack, RegimeSpec regime, EngineInputs inputs) {
        PipelineRunner.Result base = PipelineRunner.run(registry, pack, regime, inputs);
        List<Measured> priced = new ArrayList<>();
        for (CompiledClaim claim : pack.claims()) {
            Set<RuleId> rules = Stream.concat(Stream.of(claim.entry().rule()), claim.entry().covers().stream())
                    .collect(Collectors.toUnmodifiableSet());
            if (isInTheComputation(base, rules)) {
                PipelineRunner.Result without = PipelineRunner.run(registry, pack, regime, inputs, rules);
                priced.add(new Measured(claim,
                        totalIncome(without).minus(totalIncome(base)).floorAtZero(),
                        DeductionRoomPricing.bill(without).minus(DeductionRoomPricing.bill(base)).floorAtZero(),
                        finalAmount(base), finalAmount(without)));
            }
        }
        return List.copyOf(priced);
    }

    private static boolean isInTheComputation(PipelineRunner.Result run, Set<RuleId> rules) {
        return run.trace().stream().anyMatch(line -> rules.contains(line.rule())
                && line.out() instanceof MoneyValue money && money.amount().isGreaterThan(Money.ZERO));
    }

    private static Money finalAmount(PipelineRunner.Result run) {
        return ((MoneyValue) run.valueOf(NodeIds.ROUND_NETPAYABLE)).amount();
    }

    private static Money totalIncome(PipelineRunner.Result run) {
        return ((MoneyValue) run.valueOf(NodeIds.TOTALINCOME)).amount();
    }
}
