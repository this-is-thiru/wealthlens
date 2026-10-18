package com.thiru.wealthlens.taxengine.election;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import com.thiru.wealthlens.taxengine.rules.temporal.Presence;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Which regime the tax is computed under ({@code DR 15} C2; S2 s.202(1), (4); 1961 s.115BAC(1A), (6); D-222).
 *
 * <p>The choices are the regimes {@link RegimeOffers} makes available, the default first. Each is computed under its
 * own pipeline ({@code D-REGIMESTEP}), and its line cites the regime's rate table, which is what the choice changes.
 * Nothing is stated here: a person who has chosen is computed under that regime directly ({@code EngineInputs.regime}),
 * so this election is always priced.
 */
public final class RegimeElection implements Election<RegimeCode> {

    @Override
    public RuleId basis(RegimeCode choice, RulePack pack) {
        return regime(choice, pack).rateTable();
    }

    @Override
    public NodeId node() {
        return NodeIds.TAX_BYBASIS;
    }

    /**
     * @throws IllegalArgumentException if no regime is open to the assessee, with every reason
     */
    @Override
    public List<RegimeCode> candidates(EngineInputs inputs, RulePack pack) {
        List<RegimeOffer> offers = RegimeOffers.of(pack, inputs);
        List<RegimeCode> available = offers.stream()
                .filter(RegimeOffer.Available.class::isInstance)
                .map(RegimeOffer::code)
                .toList();
        if (available.isEmpty()) {
            throw new IllegalArgumentException("No regime is open to this assessee in " + inputs.taxYear().label()
                    + ": " + offers.stream().map(offer -> ((RegimeOffer.Barred) offer).reason())
                            .collect(Collectors.joining("; ")));
        }
        return available;
    }

    @Override
    public Optional<RegimeCode> stated(EngineInputs inputs) {
        return Optional.empty();
    }

    @Override
    public EngineInputs choosing(RegimeCode choice, EngineInputs inputs) {
        return new EngineInputs(inputs.taxYear(), choice, inputs.temporal(), inputs.entered(), inputs.assessee(),
                inputs.family(), inputs.ledgers(), inputs.elections(), inputs.capitalGains(), inputs.filing());
    }

    @Override
    public RegimeSpec regimeFor(RegimeCode choice, RegimeSpec computing, RulePack pack) {
        return regime(choice, pack);
    }

    @Override
    public String describe(RegimeCode choice, RulePack pack) {
        RegimeSpec regime = regime(choice, pack);
        return "regime " + choice + (regime.provision2025().presence() == Presence.PRESENT
                ? " (S2 " + regime.provision2025().reference() + ")" : "");
    }

    private static RegimeSpec regime(RegimeCode code, RulePack pack) {
        return Optional.ofNullable(pack.regimes().get(code)).orElseThrow(() ->
                new IllegalArgumentException(pack.taxYear().label() + " has no regime " + code));
    }
}
