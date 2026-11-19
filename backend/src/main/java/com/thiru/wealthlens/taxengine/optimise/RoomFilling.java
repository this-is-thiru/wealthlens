package com.thiru.wealthlens.taxengine.optimise;

import com.thiru.wealthlens.taxengine.api.RoomFamily;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.pipeline.NodeRegistry;
import com.thiru.wealthlens.taxengine.pipeline.PipelineRunner;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * The bill with every room of the chosen families filled at once (W-122b C3a, D-720): the lower end of a coarse
 * figure's band. One run over all the fills the room pricing measures one by one, so rooms that overlap — own NPS and
 * the investment ceiling, say — are counted once, as the engine counts them.
 */
public final class RoomFilling {

    private RoomFilling() {
    }

    public static Money billWithRoomsFilled(NodeRegistry registry, RulePack pack, RegimeSpec regime, EngineInputs inputs,
            Set<RoomFamily> families) {
        List<UnaryOperator<EnteredFacts>> fills = new ArrayList<>();
        if (families.contains(RoomFamily.DEDUCTIONS)) {
            DeductionRoomPricing.priced(registry, pack, regime, inputs).forEach(priced -> fills.add(priced.fill()));
        }
        if (families.contains(RoomFamily.RENT)) {
            RentRoomPricing.priced(registry, pack, regime, inputs).ifPresent(priced -> fills.add(priced.fill()));
        }
        EnteredFacts filled = inputs.entered();
        for (UnaryOperator<EnteredFacts> fill : fills) {
            filled = fill.apply(filled);
        }
        return DeductionRoomPricing.bill(PipelineRunner.run(registry, pack, regime, new EngineInputs(inputs.taxYear(),
                inputs.regime(), inputs.temporal(), filled, inputs.assessee(), inputs.family(), inputs.ledgers(),
                inputs.elections(), inputs.capitalGains(), inputs.filing())));
    }
}
