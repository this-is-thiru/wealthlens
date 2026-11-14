package com.thiru.wealthlens.taxengine.optimise;

import com.thiru.wealthlens.taxengine.api.DeductionRoom;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.FamilySnapshot;
import com.thiru.wealthlens.taxengine.input.HealthPayment;
import com.thiru.wealthlens.taxengine.input.HealthPaymentKind;
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
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The room left under a deduction's ceiling and what filling it saves (W-114a, D-666; W-114c, D-668).
 *
 * <p><b>Schedule XV</b> (2025 s.123; 1961 s.80C): the row's ceiling less what the run allowed, read off its deduction
 * line; priced by a second run with the room paid in the first contributor the row leaves without a sub-cap, so the row's
 * data, not this class, chooses it. A life insurance premium is passed over: it is entered with its policy (D-134).
 *
 * <p><b>Health</b> (s.126; 80D): every contributor has a sub-cap and the row turns on whom a payment covers and their age,
 * so the room is found by filling: one run with a premium as large as the largest ceiling those rows hold, covering the
 * assessee, and one covering every living parent the family lists. The rise in those rows' deduction lines is the room,
 * which is also what the premium takes, since within a cap a rupee paid is a rupee deducted; the fall in the bill is the
 * saving. The engine picks the row, the limb and the senior rate inside the run (D-252). Own NPS is not priced here: its
 * contribution counts under Schedule XV first, so the amount it takes is not its room (W-114c R13, W-114d).
 */
public final class DeductionRoomPricing {

    private static final List<RuleSetId> HEALTH = List.of(RuleSetIds.RS_DED_HEALTH_FAMILY,
            RuleSetIds.RS_DED_HEALTH_FAMILY_SENIOR, RuleSetIds.RS_DED_HEALTH_PARENTS, RuleSetIds.RS_DED_HEALTH_PARENTS_SENIOR);

    private DeductionRoomPricing() {
    }

    /** The runs a price is measured on: the inputs as they stand, and what is needed to run them again. */
    private record Pricing(NodeRegistry registry, RulePack pack, RegimeSpec regime, EngineInputs inputs,
            PipelineRunner.Result base) {

        PipelineRunner.Result run(EngineInputs filled) {
            return PipelineRunner.run(registry, pack, regime, filled);
        }

        EngineInputs with(EnteredFacts entered) {
            return new EngineInputs(inputs.taxYear(), inputs.regime(), inputs.temporal(), entered, inputs.assessee(),
                    inputs.family(), inputs.ledgers(), inputs.elections(), inputs.capitalGains(), inputs.filing());
        }
    }

    /**
     * Schedule XV's room, then the assessee's health cover and the parents': each where it has room.
     *
     * @throws IllegalStateException where a set holds more than one row and the run chose none, or no contributor can
     *     carry the room — a pack that changed under this
     */
    public static List<DeductionRoom> price(NodeRegistry registry, RulePack pack, RegimeSpec regime, EngineInputs inputs) {
        Pricing pricing = new Pricing(registry, pack, regime, inputs, PipelineRunner.run(registry, pack, regime, inputs));
        List<DeductionRoom> rooms = new ArrayList<>();
        scheduleXv(pricing).ifPresent(rooms::add);
        Money healthCeiling = HEALTH.stream().flatMap(set -> pack.ruleSet(set).members().stream())
                .map(id -> ceiling(pack, id, inputs)).reduce(Money.ZERO, Money::max);
        Set<RuleId> healthRows = pack.membersOf(HEALTH);
        byFilling(pricing, healthRows, pricing.with(inputs.entered().withHealthPayments(plus(inputs.entered().healthPayments(),
                new HealthPayment(HealthPaymentKind.INSURANCE_PREMIUM, true, List.of(), healthCeiling, false, 1)))))
                .ifPresent(rooms::add);
        List<Integer> parents = parents(inputs.family());
        if (!parents.isEmpty()) {
            byFilling(pricing, healthRows, pricing.with(inputs.entered().withHealthPayments(plus(
                    inputs.entered().healthPayments(),
                    new HealthPayment(HealthPaymentKind.INSURANCE_PREMIUM, false, parents, healthCeiling, false, 1)))))
                    .ifPresent(rooms::add);
        }
        return List.copyOf(rooms);
    }

    private static Optional<DeductionRoom> scheduleXv(Pricing pricing) {
        RulePack pack = pricing.pack();
        EngineInputs inputs = pricing.inputs();
        List<RuleId> members = pack.ruleSet(RuleSetIds.RS_DED_SCHEDULE_XV).members();
        Optional<TraceLine> line = pricing.base().trace().stream()
                .filter(traced -> traced.kind() == StepKind.DEDUCTION && members.contains(traced.rule()))
                .findFirst();
        RuleRow row = pack.rule(line.map(TraceLine::rule).orElseGet(() -> onlyMember(members)));
        AggregateWithCeiling cap = cap(row, inputs);
        Money allowed = line.map(traced -> ((MoneyValue) traced.out()).amount()).orElse(Money.ZERO);
        Money room = cap.ceiling().minus(allowed);
        if (!room.isGreaterThan(Money.ZERO)) {
            return Optional.empty();
        }
        List<QualifyingPayment> payments = new ArrayList<>(inputs.entered().qualifyingPayments());
        payments.add(new QualifyingPayment(carrier(pack.components(), inputs.taxYear(), cap, row.id()), room));
        PipelineRunner.Result filled = pricing.run(pricing.with(inputs.entered().withQualifyingPayments(payments)));
        Money saved = bill(pricing.base()).minus(bill(filled)).floorAtZero();
        return Optional.of(new DeductionRoom(row.id().value(), allowed, room, saved));
    }

    /**
     * The rows among {@code rows} whose deduction lines rose from the inputs as they stand to {@code filled}: what they
     * counted before, by how much they rose, and the fall in the bill.
     */
    private static Optional<DeductionRoom> byFilling(Pricing pricing, Set<RuleId> rows, EngineInputs filled) {
        PipelineRunner.Result run = pricing.run(filled);
        Map<RuleId, Money> before = deducted(pricing.base(), rows);
        Map<RuleId, Money> after = deducted(run, rows);
        List<RuleId> rose = after.keySet().stream()
                .filter(row -> after.get(row).isGreaterThan(before.getOrDefault(row, Money.ZERO)))
                .toList();
        if (rose.isEmpty()) {
            return Optional.empty();
        }
        Money counted = rose.stream().map(row -> before.getOrDefault(row, Money.ZERO)).reduce(Money.ZERO, Money::plus);
        Money room = rose.stream().map(after::get).reduce(Money.ZERO, Money::plus).minus(counted);
        return Optional.of(new DeductionRoom(rose.stream().map(RuleId::value).collect(Collectors.joining(" + ")), counted,
                room, bill(pricing.base()).minus(bill(run)).floorAtZero()));
    }

    private static Map<RuleId, Money> deducted(PipelineRunner.Result run, Set<RuleId> rows) {
        Map<RuleId, Money> deducted = new TreeMap<>(Comparator.comparing(RuleId::value));
        run.trace().stream().filter(line -> line.kind() == StepKind.DEDUCTION && rows.contains(line.rule()))
                .forEach(line -> deducted.merge(line.rule(), ((MoneyValue) line.out()).amount(), Money::plus));
        return deducted;
    }

    /** The family's living parents, by position. */
    private static List<Integer> parents(FamilySnapshot family) {
        List<Integer> parents = new ArrayList<>();
        for (int member = 0; member < family.members().size(); member++) {
            FamilySnapshot.Member person = family.members().get(member);
            if (person.relationship() == FamilySnapshot.Relationship.PARENT && person.dateOfDeath().isEmpty()) {
                parents.add(member);
            }
        }
        return parents;
    }

    private static List<HealthPayment> plus(List<HealthPayment> payments, HealthPayment added) {
        List<HealthPayment> all = new ArrayList<>(payments);
        all.add(added);
        return all;
    }

    private static AggregateWithCeiling cap(RuleRow row, EngineInputs inputs) {
        return (AggregateWithCeiling) TemporallyApportionedCap.governingVersion(row, inputs.temporal()).cap().orElseThrow();
    }

    private static Money ceiling(RulePack pack, RuleId row, EngineInputs inputs) {
        return cap(pack.rule(row), inputs).ceiling();
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
