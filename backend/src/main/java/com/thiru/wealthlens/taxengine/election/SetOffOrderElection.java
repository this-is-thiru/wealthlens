package com.thiru.wealthlens.taxengine.election;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.definition.AbsorptionOrder;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The order in which the other heads absorb a head's loss (S2 s.109(1); 1961 s.71; D-221).
 *
 * <p>Neither Act prescribes one, and the Tribunal has held the choice the assessee's ("there is no order of precedence
 * prescribed … the choice in this respect should rest with the assessee" — <i>Eldorado India</i>, ITAT Kolkata, 1997;
 * <i>Nikhil Khandelwal</i>, ITAT Jaipur, 2025). It changes the tax where it changes what a later step reads — today
 * the dividend income the surcharge's excluding ladder takes out (D-200). The choices are every order of the heads in
 * {@code SETOFF-ACROSS-HEADS}'s declared {@link AbsorptionOrder}, that order first.
 */
public final class SetOffOrderElection implements Election<List<HeadCode>> {

    @Override
    public RuleId basis() {
        return RuleIds.SETOFF_ACROSS_HEADS;
    }

    @Override
    public NodeId node() {
        return NodeIds.SETOFF;
    }

    @Override
    public List<List<HeadCode>> candidates(EngineInputs inputs, RulePack pack) {
        AbsorptionOrder declared = RuleDefinitions.require(TemporallyApportionedCap.governingVersion(
                pack.rule(RuleIds.SETOFF_ACROSS_HEADS), inputs.temporal()).definition(), AbsorptionOrder.class,
                RuleIds.SETOFF_ACROSS_HEADS);
        List<List<HeadCode>> orders = new ArrayList<>();
        permute(List.of(), declared.heads(), orders);
        return List.copyOf(orders);
    }

    @Override
    public Optional<List<HeadCode>> stated(EngineInputs inputs) {
        return inputs.entered().absorptionOrder();
    }

    @Override
    public EngineInputs choosing(List<HeadCode> choice, EngineInputs inputs) {
        return new EngineInputs(inputs.taxYear(), inputs.regime(), inputs.temporal(),
                inputs.entered().withAbsorptionOrder(choice), inputs.assessee(), inputs.family(), inputs.ledgers(),
                inputs.elections(), inputs.capitalGains(), inputs.filing());
    }

    @Override
    public String describe(List<HeadCode> choice) {
        return "absorption order " + choice;
    }

    /** Every order of {@code rest} after {@code prefix}, taking the heads in their declared order — so it comes first. */
    private static void permute(List<HeadCode> prefix, List<HeadCode> rest, List<List<HeadCode>> orders) {
        if (rest.isEmpty()) {
            orders.add(prefix);
            return;
        }
        for (HeadCode head : rest) {
            List<HeadCode> longer = new ArrayList<>(prefix);
            longer.add(head);
            List<HeadCode> shorter = new ArrayList<>(rest);
            shorter.remove(head);
            permute(List.copyOf(longer), List.copyOf(shorter), orders);
        }
    }
}
