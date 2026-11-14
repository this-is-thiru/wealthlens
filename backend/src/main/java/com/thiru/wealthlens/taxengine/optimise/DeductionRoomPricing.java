package com.thiru.wealthlens.taxengine.optimise;

import com.thiru.wealthlens.taxengine.api.DeductionRoom;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.QualifyingPayment;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeRegistry;
import com.thiru.wealthlens.taxengine.pipeline.PipelineRunner;
import com.thiru.wealthlens.taxengine.rules.cap.AggregateWithCeiling;
import com.thiru.wealthlens.taxengine.rules.component.ComponentDefinition;
import com.thiru.wealthlens.taxengine.rules.component.ComponentKind;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Schedule XV (2025 s.123; 1961 s.80C) room and what filling it saves (W-114a C1a, C2a; D-666). The room is the row's
 * ceiling less what the run allowed, read off its deduction line; the price is a second run with the room paid in the first
 * contributor the row leaves without a sub-cap, so the row's data, not this class, chooses it. A life insurance premium is
 * passed over: it is entered with its policy, never as a payment (D-134).
 */
public final class DeductionRoomPricing {

    private DeductionRoomPricing() {
    }

    /**
     * @throws IllegalStateException where the year's Schedule XV set holds more than one row and the run chose none, or
     *     no contributor can carry the room — a pack that changed under this
     */
    public static List<DeductionRoom> price(NodeRegistry registry, RulePack pack, RegimeSpec regime, EngineInputs inputs) {
        PipelineRunner.Result base = PipelineRunner.run(registry, pack, regime, inputs);
        List<RuleId> members = pack.ruleSet(RuleSetIds.RS_DED_SCHEDULE_XV).members();
        Optional<TraceLine> line = base.trace().stream()
                .filter(traced -> traced.kind() == StepKind.DEDUCTION && members.contains(traced.rule()))
                .findFirst();
        RuleRow row = pack.rule(line.map(TraceLine::rule).orElseGet(() -> onlyMember(members)));
        AggregateWithCeiling cap = (AggregateWithCeiling) TemporallyApportionedCap.governingVersion(row, inputs.temporal())
                .cap().orElseThrow();
        Money allowed = line.map(traced -> ((MoneyValue) traced.out()).amount()).orElse(Money.ZERO);
        Money room = cap.ceiling().minus(allowed);
        if (!room.isGreaterThan(Money.ZERO)) {
            return List.of();
        }
        List<QualifyingPayment> payments = new ArrayList<>(inputs.entered().qualifyingPayments());
        payments.add(new QualifyingPayment(carrier(pack.components(), inputs.taxYear(), cap, row.id()), room));
        EngineInputs filled = new EngineInputs(inputs.taxYear(), inputs.regime(), inputs.temporal(),
                inputs.entered().withQualifyingPayments(payments), inputs.assessee(), inputs.family(), inputs.ledgers(),
                inputs.elections(), inputs.capitalGains(), inputs.filing());
        Money saved = bill(base).minus(bill(PipelineRunner.run(registry, pack, regime, filled))).floorAtZero();
        return List.of(new DeductionRoom(row.id().value(), allowed, room, saved));
    }

    static RuleId onlyMember(List<RuleId> members) {
        if (members.size() != 1) {
            throw new IllegalStateException("Schedule XV's set holds " + members + " and the run deducted under none;"
                    + " a row cannot be chosen outside the run (D-252)");
        }
        return members.getFirst();
    }

    /** The first contributor with no sub-cap that is a qualifying payment live in the year. */
    static ComponentCode carrier(Map<ComponentCode, ComponentDefinition> components, TaxYearId year, AggregateWithCeiling cap,
            RuleId row) {
        return cap.contributors().stream()
                .filter(code -> !cap.subCaps().containsKey(code) && !code.equals(ComponentCodes.LIFE_INSURANCE_PREMIUM))
                .filter(code -> {
                    ComponentDefinition definition = components.get(code);
                    return definition != null && definition.kind() == ComponentKind.QUALIFYING_PAYMENT
                            && definition.lifecycle().isLiveIn(year);
                })
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(row + " has no contributor without a sub-cap that is a"
                        + " qualifying payment live in " + year.label()));
    }

    private static Money bill(PipelineRunner.Result run) {
        return ((MoneyValue) run.valueOf(NodeIds.TAX_NETPAYABLE)).amount();
    }
}
