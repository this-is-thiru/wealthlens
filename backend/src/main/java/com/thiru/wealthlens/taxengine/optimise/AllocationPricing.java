package com.thiru.wealthlens.taxengine.optimise;

import com.thiru.wealthlens.taxengine.api.AllocationSpace;
import com.thiru.wealthlens.taxengine.api.Optimisation;
import com.thiru.wealthlens.taxengine.election.ElectionOutcome;
import com.thiru.wealthlens.taxengine.election.ElectionResolver;
import com.thiru.wealthlens.taxengine.election.PricedAlternative;
import com.thiru.wealthlens.taxengine.election.RegimeElection;
import com.thiru.wealthlens.taxengine.input.EmployerFund;
import com.thiru.wealthlens.taxengine.input.EmployerFundContribution;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeRegistry;
import com.thiru.wealthlens.taxengine.pipeline.PipelineRunner;
import com.thiru.wealthlens.taxengine.rules.component.CashNature;
import com.thiru.wealthlens.taxengine.rules.component.ComponentDefinition;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.IntStream;

/**
 * Every allocation the optimiser tries, computed in full and remembered: its bill under the regime the rules choose
 * (A2.1 — the regime is inside the objective), its total income under one regime, and its cash take-home (C3, by each
 * component's {@code CashNature} in the year's pack, D-CASHFLAG). Nothing here models the law; it only runs the pipeline.
 */
final class AllocationPricing {

    /** The regime the rules choose for an allocation, its net payable, and every regime they offered. */
    record Priced(RegimeCode regime, Money bill, List<RegimeCode> offered) {
    }

    private final NodeRegistry registry;
    private final RulePack pack;
    private final RegimeSpec computing;
    private final EngineInputs base;
    private final int employment;
    private final Map<Allocation, Priced> priced = new HashMap<>();
    private final Map<RegimeCode, Map<Allocation, Money>> totalIncome = new HashMap<>();
    private int pipelineRuns;

    private AllocationPricing(NodeRegistry registry, RulePack pack, RegimeSpec computing, EngineInputs base,
            int employment) {
        this.registry = registry;
        this.pack = pack;
        this.computing = computing;
        this.base = base;
        this.employment = employment;
    }

    /**
     * @throws IllegalArgumentException if a code is not in the year's pack, or the residual is not in exactly one
     *     employment, or a code appears twice in it, or a movable fund has two contributions entered for it
     */
    static AllocationPricing over(NodeRegistry registry, RulePack pack, RegimeSpec computing, AllocationSpace space) {
        List<ComponentCode> codes = new ArrayList<>(space.movable());
        codes.add(space.residual());
        for (ComponentCode code : codes) {
            if (!pack.components().containsKey(code)) {
                throw new IllegalArgumentException(pack.taxYear().label() + " defines no component " + code.value());
            }
        }
        List<Employment> employments = space.base().entered().employments();
        List<Integer> holding = IntStream.range(0, employments.size())
                .filter(i -> count(employments.get(i), space.residual()) > 0).boxed().toList();
        if (holding.size() != 1) {
            throw new IllegalArgumentException("The residual component " + space.residual().value()
                    + " must be in exactly one employment, not " + holding.size());
        }
        Employment held = employments.get(holding.get(0));
        codes.stream().filter(code -> count(held, code) > 1).findFirst().ifPresent(code -> {
            throw new IllegalArgumentException(code.value() + " appears more than once in the employment");
        });
        int employment = holding.get(0);
        space.movableFunds().stream().filter(fund -> contributions(space.base(), employment, fund) > 1).findFirst()
                .ifPresent(fund -> {
                    throw new IllegalArgumentException("The employer's contribution to " + fund.name()
                            + " is entered more than once for the employment the residual is in");
                });
        return new AllocationPricing(registry, pack, computing, space.base(), employment);
    }

    /** The movable pay and funds as entered; a movable code or fund not entered for the employment starts at nil. */
    Allocation entered(AllocationSpace space) {
        SortedMap<Movable, Money> amounts = new TreeMap<>(Movable.ORDER);
        space.movable().forEach(code -> amounts.put(new Movable.Pay(code), Money.ZERO));
        space.movableFunds().forEach(fund -> amounts.put(new Movable.Fund(fund), Money.ZERO));
        Movable.Pay residual = new Movable.Pay(space.residual());
        amounts.put(residual, Money.ZERO);
        for (SalaryComponent component : base.entered().employments().get(employment).components()) {
            amounts.computeIfPresent(new Movable.Pay(component.code()), (code, nil) -> component.annualAmount());
        }
        for (EmployerFundContribution contribution : base.entered().employerContributions()) {
            if (contribution.employment() == employment) {
                amounts.computeIfPresent(new Movable.Fund(contribution.fund()), (fund, nil) -> contribution.amount());
            }
        }
        return new Allocation(amounts, residual);
    }

    Priced price(Allocation allocation) {
        return priced.computeIfAbsent(allocation, this::resolve);
    }

    /** Total income before rounding under {@code regime} — the measure a candidate's saturation is found on (C1a). */
    Money totalIncome(Allocation allocation, RegimeCode regime) {
        return totalIncome.computeIfAbsent(regime, code -> new HashMap<>()).computeIfAbsent(allocation, key -> {
            RegimeElection election = new RegimeElection();
            pipelineRuns++;
            PipelineRunner.Result run = PipelineRunner.run(registry, pack, election.regimeFor(regime, computing, pack),
                    election.choosing(regime, applied(key)));
            return ((MoneyValue) run.valueOf(NodeIds.TOTALINCOME)).amount();
        });
    }

    /** Cash in hand after tax: every CASH component less every CASH_OUTFLOW one, less the bill (C3). */
    Money takeHome(Allocation allocation) {
        Money cash = Money.ZERO;
        for (Employment each : applied(allocation).entered().employments()) {
            for (SalaryComponent component : each.components()) {
                cash = cash.plus(inHand(pack.components().get(component.code()).cashNature(), component.annualAmount()));
            }
        }
        return cash.minus(price(allocation).bill());
    }

    /** What the year's pack says {@code pay} is (W-238b). */
    Optimisation.ComponentFacts facts(Movable.Pay pay) {
        ComponentDefinition row = pack.components().get(pay.code());
        return new Optimisation.ComponentFacts(row.kind().name(), row.cashNature().name(),
                row.feedsInto().stream().map(RuleId::value).sorted().toList());
    }

    /**
     * The cash before tax that moving {@code item} from {@code current} to {@code suggested} adds, the residual funding it.
     * A fund contribution is never in hand, which is what {@link #takeHome} already reads: it counts components only (C11).
     */
    Money cashImpact(Movable item, Movable.Pay residual, Money current, Money suggested) {
        CashNature moved = switch (item) {
            case Movable.Pay pay -> pack.components().get(pay.code()).cashNature();
            case Movable.Fund fund -> CashNature.NON_CASH;
        };
        return cashImpact(pack.components().get(residual.code()).cashNature(), moved, current, suggested);
    }

    /** By the take-home rule (C3): the moved component gains what the residual gives up. */
    static Money cashImpact(CashNature residual, CashNature moved, Money current, Money suggested) {
        Money delta = suggested.minus(current);
        return inHand(moved, delta).minus(inHand(residual, delta));
    }

    private static Money inHand(CashNature nature, Money amount) {
        return switch (nature) {
            case CASH -> amount;
            case CASH_OUTFLOW -> Money.ZERO.minus(amount);
            case NON_CASH -> Money.ZERO;
        };
    }

    int pipelineRuns() {
        return pipelineRuns;
    }

    private Priced resolve(Allocation allocation) {
        ElectionOutcome<RegimeCode> outcome = ElectionResolver.resolve(new RegimeElection(), registry, pack, computing,
                applied(allocation)).outcome();
        pipelineRuns += outcome.alternatives().size();
        Money bill = outcome.alternatives().stream().filter(each -> each.choice().equals(outcome.chosen()))
                .findFirst().orElseThrow().netPayable();
        return new Priced(outcome.chosen(), bill,
                outcome.alternatives().stream().map(PricedAlternative::choice).toList());
    }

    private EngineInputs applied(Allocation allocation) {
        List<Employment> employments = new ArrayList<>(base.entered().employments());
        Employment held = employments.get(employment);
        List<SalaryComponent> components = new ArrayList<>();
        for (SalaryComponent component : held.components()) {
            Money amount = allocation.amounts().get(new Movable.Pay(component.code()));
            components.add(amount == null ? component : new SalaryComponent(component.code(), amount));
        }
        allocation.amounts().forEach((item, amount) -> {
            if (item instanceof Movable.Pay pay && count(held, pay.code()) == 0) {
                components.add(new SalaryComponent(pay.code(), amount));
            }
        });
        employments.set(employment, new Employment(components, held.termsIncludeDearnessAllowance(),
                held.employerKind(), held.dearnessAllowanceCountsForRetirement(), held.companyEmployer()));
        EnteredFacts entered = base.entered().withEmployments(employments)
                .withEmployerContributions(contributions(allocation));
        return new EngineInputs(base.taxYear(), base.regime(), base.temporal(), entered, base.assessee(), base.family(),
                base.ledgers(), base.elections(), base.capitalGains(), base.filing());
    }

    /** The contributions as entered, each movable fund's for this employment at its allocated amount. */
    private List<EmployerFundContribution> contributions(Allocation allocation) {
        List<EmployerFundContribution> contributions = new ArrayList<>();
        Set<EmployerFund> written = new HashSet<>();
        for (EmployerFundContribution contribution : base.entered().employerContributions()) {
            Money amount = contribution.employment() == employment
                    ? allocation.amounts().get(new Movable.Fund(contribution.fund()))
                    : null;
            if (amount == null) {
                contributions.add(contribution);
            } else {
                contributions.add(new EmployerFundContribution(employment, contribution.fund(), amount));
                written.add(contribution.fund());
            }
        }
        allocation.amounts().forEach((item, amount) -> {
            if (item instanceof Movable.Fund fund && !written.contains(fund.fund()) && amount.isGreaterThan(Money.ZERO)) {
                contributions.add(new EmployerFundContribution(employment, fund.fund(), amount));
            }
        });
        return contributions;
    }

    private static long contributions(EngineInputs inputs, int employment, EmployerFund fund) {
        return inputs.entered().employerContributions().stream()
                .filter(contribution -> contribution.employment() == employment && contribution.fund() == fund).count();
    }

    private static long count(Employment employment, ComponentCode code) {
        return employment.components().stream().filter(component -> component.code().equals(code)).count();
    }
}
