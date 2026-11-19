package com.thiru.wealthlens.taxengine.optimise;

import com.thiru.wealthlens.taxengine.api.AllocationSpace;
import com.thiru.wealthlens.taxengine.api.Optimisation;
import com.thiru.wealthlens.taxengine.api.ProvidentFundTerms;
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
import com.thiru.wealthlens.taxengine.node.FundHistoryRequiredException;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeRegistry;
import com.thiru.wealthlens.taxengine.pipeline.PipelineRunner;
import com.thiru.wealthlens.taxengine.rules.component.CashNature;
import com.thiru.wealthlens.taxengine.rules.component.ComponentDefinition;
import com.thiru.wealthlens.taxengine.rules.component.ComponentKind;
import com.thiru.wealthlens.taxengine.rules.component.EmployerFundDefinition;
import com.thiru.wealthlens.taxengine.rules.component.Liquidity;
import com.thiru.wealthlens.taxengine.rules.component.UnstatedTerms;
import com.thiru.wealthlens.taxengine.rules.component.WithdrawalTerm;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.EmployerFundCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final Following following;
    private final Map<Allocation, Priced> priced = new HashMap<>();
    private final Map<RegimeCode, Map<Allocation, Optional<Money>>> totalIncome = new HashMap<>();
    private final Set<Allocation> unpricedAllocations = new HashSet<>();
    private Optional<Optimisation.UnpricedNotice> unpriced = Optional.empty();
    private int pipelineRuns;

    private AllocationPricing(NodeRegistry registry, RulePack pack, RegimeSpec computing, EngineInputs base,
            int employment, Following following) {
        this.registry = registry;
        this.pack = pack;
        this.computing = computing;
        this.base = base;
        this.employment = employment;
        this.following = following;
    }

    /**
     * @throws IllegalArgumentException if a code is not in the year's pack, or its row's kind is not {@code SALARY}
     *     (W-238g), or the residual is not in exactly one employment, or a code appears twice in it, or a movable fund
     *     has two contributions entered for it, or basic cannot be moved with what follows it ({@link #following}), or a
     *     contribution is not one the employee can add to ({@link #requireContributions})
     */
    static AllocationPricing over(NodeRegistry registry, RulePack pack, RegimeSpec computing, AllocationSpace space) {
        List<ComponentCode> codes = new ArrayList<>(space.moves().movable());
        codes.add(space.residual());
        for (ComponentCode code : codes) {
            if (!pack.components().containsKey(code)) {
                throw new IllegalArgumentException(pack.taxYear().label() + " defines no component " + code.value());
            }
            ComponentKind kind = pack.components().get(code).kind();
            if (kind != ComponentKind.SALARY) {
                throw new IllegalArgumentException(code.value() + " is " + kind.name() + " in " + pack.taxYear().label()
                        + ", not pay: only a SALARY component is drawn from the cost to company, so only one can move"
                        + " or fund the moves (A2.1)" + (kind == ComponentKind.EMPLOYEE_CONTRIBUTION
                                ? "; name it under contributions, paid from take-home (W-238k)" : ""));
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
        codes.addAll(space.moves().contributions());
        codes.stream().filter(code -> count(held, code) > 1).findFirst().ifPresent(code -> {
            throw new IllegalArgumentException(code.value() + " appears more than once in the employment");
        });
        int employment = holding.get(0);
        space.moves().movableFunds().stream().filter(fund -> contributions(space.base(), employment, fund) > 1).findFirst()
                .ifPresent(fund -> {
                    throw new IllegalArgumentException("The employer's contribution to " + fund.name()
                            + " is entered more than once for the employment the residual is in");
                });
        Following following = following(space, held, employment);
        requireContributions(pack, space.moves().contributions());
        return new AllocationPricing(registry, pack, computing, space.base(), employment, following);
    }

    /**
     * Each contribution must be an employee's own ({@code EMPLOYEE_CONTRIBUTION} in the year's pack, W-238k C1a). A share
     * that follows basic may be one too: the addition rides on its following amount, since {@link #applied} writes the
     * two together (W-238k1).
     */
    private static void requireContributions(RulePack pack, Set<ComponentCode> contributions) {
        for (ComponentCode code : contributions) {
            if (!pack.components().containsKey(code)) {
                throw new IllegalArgumentException(pack.taxYear().label() + " defines no component " + code.value());
            }
            ComponentKind kind = pack.components().get(code).kind();
            if (kind != ComponentKind.EMPLOYEE_CONTRIBUTION) {
                throw new IllegalArgumentException(code.value() + " is " + kind.name() + " in " + pack.taxYear().label()
                        + ", not an employee's own contribution, so it cannot be added to from take-home (W-238k)");
            }
        }
    }

    /**
     * What follows the provident fund's base where a part of it may move (A2.2 C8; W-238d): the employer's provident fund
     * contribution and the employee's, as entered for the employment, each only where the terms say it follows (W-238i);
     * a share that does not is left out and so held as entered. The base is basic, and the dearness allowance where the
     * terms say the base includes it (W-238j); each part that may move leads, basic or the allowance (W-238j1), and the
     * rest is held. Refused where the facts cannot say how much follows: the base not stated for dearness allowance that
     * counts for retirement, the allowance movable but not entered where it may be in the base (W-238j1 C3a), or a
     * follower entered twice; and refused where a term changes nothing (W-238i, W-238j C3a).
     */
    private static Following following(AllocationSpace space, Employment held, int employment) {
        ProvidentFundTerms terms = space.providentFundTerms();
        Set<ComponentCode> movable = space.moves().movable();
        Money dearnessAllowance = enteredAmount(held, ComponentCodes.DEARNESS_ALLOWANCE);
        boolean dearnessAllowanceInBase = dearnessAllowance.isGreaterThan(Money.ZERO)
                && terms.baseIncludesDearnessAllowance().orElse(held.dearnessAllowanceCountsForRetirement());
        boolean anyLeads = movable.contains(ComponentCodes.BASIC)
                || dearnessAllowanceInBase && movable.contains(ComponentCodes.DEARNESS_ALLOWANCE);
        requireHeldShareEntered(terms.employerShareFollowsBasic(), "employerShareFollowsBasic", anyLeads,
                contributions(space.base(), employment, EmployerFund.RECOGNISED_PROVIDENT_FUND) > 0);
        requireHeldShareEntered(terms.employeeShareFollowsBasic(), "employeeShareFollowsBasic", anyLeads,
                count(held, ComponentCodes.EMPLOYEE_PF) > 0);
        SortedMap<Movable, Money> followers = sharesThatFollow(space, held, employment);
        requireBaseFactUsed(terms, anyLeads, !followers.isEmpty(), dearnessAllowance.isGreaterThan(Money.ZERO));
        requireAllowanceEntered(space, held, !followers.isEmpty(), dearnessAllowance);
        if (!anyLeads) {
            return Following.none();
        }
        if (count(held, ComponentCodes.EMPLOYEE_PF) > 1
                || contributions(space.base(), employment, EmployerFund.RECOGNISED_PROVIDENT_FUND) > 1) {
            throw new IllegalArgumentException("A provident fund contribution that follows BASIC is entered more than"
                    + " once for the employment, so it has no one ratio to basic (A2.2 C8)");
        }
        if (!followers.isEmpty() && dearnessAllowanceInBase && terms.baseIncludesDearnessAllowance().isEmpty()) {
            String moving = movable.contains(ComponentCodes.DEARNESS_ALLOWANCE) ? "DEARNESS_ALLOWANCE" : "BASIC";
            throw new IllegalArgumentException(moving + " cannot move here: the employment's dearness allowance counts"
                    + " for retirement, and whether the provident fund follows it too is not entered (A2.2 C8)");
        }
        return new Following(leaders(movable, held, dearnessAllowanceInBase), fixed(movable, held,
                dearnessAllowanceInBase), followers);
    }

    /** The parts of the base that may move, each at its amount as entered (W-238j1 C1a). */
    private static SortedMap<Movable, Money> leaders(Set<ComponentCode> movable, Employment held,
            boolean dearnessAllowanceInBase) {
        SortedMap<Movable, Money> leaders = new TreeMap<>(Movable.ORDER);
        if (movable.contains(ComponentCodes.BASIC)) {
            leaders.put(new Movable.Pay(ComponentCodes.BASIC), enteredAmount(held, ComponentCodes.BASIC));
        }
        if (dearnessAllowanceInBase && movable.contains(ComponentCodes.DEARNESS_ALLOWANCE)) {
            leaders.put(new Movable.Pay(ComponentCodes.DEARNESS_ALLOWANCE),
                    enteredAmount(held, ComponentCodes.DEARNESS_ALLOWANCE));
        }
        return leaders;
    }

    /** The parts of the base that are held: basic where it may not move, the allowance in the base where it may not. */
    private static Money fixed(Set<ComponentCode> movable, Employment held, boolean dearnessAllowanceInBase) {
        Money fixed = movable.contains(ComponentCodes.BASIC) ? Money.ZERO : enteredAmount(held, ComponentCodes.BASIC);
        return dearnessAllowanceInBase && !movable.contains(ComponentCodes.DEARNESS_ALLOWANCE)
                ? fixed.plus(enteredAmount(held, ComponentCodes.DEARNESS_ALLOWANCE))
                : fixed;
    }

    /**
     * A movable dearness allowance not entered, where a share follows and the allowance counts for retirement with the
     * base unstated, may be in the base, so whether moving it moves the fund cannot be told (W-238j1 C3a).
     */
    private static void requireAllowanceEntered(AllocationSpace space, Employment held, boolean anyFollows,
            Money dearnessAllowance) {
        if (anyFollows && space.moves().movable().contains(ComponentCodes.DEARNESS_ALLOWANCE)
                && !dearnessAllowance.isGreaterThan(Money.ZERO) && held.dearnessAllowanceCountsForRetirement()
                && space.providentFundTerms().baseIncludesDearnessAllowance().isEmpty()) {
            throw new IllegalArgumentException("DEARNESS_ALLOWANCE is not entered, so whether the provident fund follows"
                    + " it cannot be told: it counts for retirement, and whether the fund's base includes it is not"
                    + " entered (A2.2 C8; W-238j1)");
        }
    }

    /** The provident fund shares entered for the employment whose terms say they follow basic (W-238i). */
    private static SortedMap<Movable, Money> sharesThatFollow(AllocationSpace space, Employment held, int employment) {
        SortedMap<Movable, Money> followers = new TreeMap<>(Movable.ORDER);
        if (space.providentFundTerms().employeeShareFollowsBasic()) {
            held.components().stream().filter(component -> component.code().equals(ComponentCodes.EMPLOYEE_PF))
                    .forEach(component -> followers.put(new Movable.Pay(component.code()), component.annualAmount()));
        }
        if (space.providentFundTerms().employerShareFollowsBasic()) {
            space.base().entered().employerContributions().stream()
                    .filter(each -> each.employment() == employment
                            && each.fund() == EmployerFund.RECOGNISED_PROVIDENT_FUND)
                    .forEach(each -> followers.put(new Movable.Fund(each.fund()), each.amount()));
        }
        return followers;
    }

    /** The first entry of {@code code} in the employment, nil where it is not entered. */
    private static Money enteredAmount(Employment held, ComponentCode code) {
        return held.components().stream().filter(component -> component.code().equals(code))
                .map(SalaryComponent::annualAmount).findFirst().orElse(Money.ZERO);
    }

    /** A stated base must be one a basic move measures against (W-238j C3a). */
    private static void requireBaseFactUsed(ProvidentFundTerms terms, boolean anyLeads, boolean anyFollows,
            boolean dearnessAllowanceEntered) {
        if (terms.baseIncludesDearnessAllowance().isEmpty()) {
            return;
        }
        String reason = !anyLeads ? "BASIC is not movable, nor dearness allowance in the provident fund's base"
                : !anyFollows ? "no provident fund share follows basic"
                : !dearnessAllowanceEntered ? "no dearness allowance is entered for the employment" : "";
        if (!reason.isEmpty()) {
            throw new IllegalArgumentException("baseIncludesDearnessAllowance is stated, but " + reason
                    + ", so the base changes nothing");
        }
    }

    /** A share held by its term ({@code follows} false) must be one that would otherwise move with basic (W-238i C3a). */
    private static void requireHeldShareEntered(boolean follows, String term, boolean anyLeads, boolean entered) {
        if (follows) {
            return;
        }
        if (!anyLeads) {
            throw new IllegalArgumentException(term + " is false, but BASIC is not movable, nor dearness allowance in the"
                    + " provident fund's base, so nothing follows it to hold");
        }
        if (!entered) {
            throw new IllegalArgumentException(term + " is false, but that provident fund share is not entered for the"
                    + " employment the residual is in, so there is nothing to hold");
        }
    }

    /**
     * The movable pay and funds as entered, each contribution with nothing added; a movable code or fund not entered for
     * the employment starts at nil.
     */
    Allocation entered(AllocationSpace space) {
        SortedMap<Movable, Money> amounts = new TreeMap<>(Movable.ORDER);
        space.moves().movable().forEach(code -> amounts.put(new Movable.Pay(code), Money.ZERO));
        space.moves().movableFunds().forEach(fund -> amounts.put(new Movable.Fund(fund), Money.ZERO));
        space.moves().contributions().forEach(code -> amounts.put(new Movable.Contribution(code), Money.ZERO));
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
        amounts.putAll(following.followers());
        return new Allocation(amounts, residual, following);
    }

    Priced price(Allocation allocation) {
        return priced.computeIfAbsent(allocation, this::resolve);
    }

    /**
     * Whether the engine can price {@code allocation}. One it refuses for want of the funds' history is not priced, and
     * the refusal is kept for the answer (W-238h C1a); any other refusal propagates.
     */
    boolean isPriced(Allocation allocation) {
        if (priced.containsKey(allocation)) {
            return true;
        }
        if (unpricedAllocations.contains(allocation)) {
            return false;
        }
        try {
            price(allocation);
            return true;
        } catch (FundHistoryRequiredException refused) {
            note(refused);
            unpricedAllocations.add(allocation);
            return false;
        }
    }

    /** Why some allocations tried were not priced; empty where every one was. */
    Optional<Optimisation.UnpricedNotice> unpriced() {
        return unpriced;
    }

    private void note(FundHistoryRequiredException refused) {
        unpriced = Optional.of(new Optimisation.UnpricedNotice(FundHistoryRequiredException.MISSING_INPUT,
                refused.rule().value()));
    }

    /**
     * Total income before rounding under {@code regime} — the measure a candidate's saturation is found on (C1a); empty
     * where the engine cannot price the allocation for want of the funds' history (W-238h C1a).
     */
    Optional<Money> totalIncome(Allocation allocation, RegimeCode regime) {
        return totalIncome.computeIfAbsent(regime, code -> new HashMap<>()).computeIfAbsent(allocation, key -> {
            RegimeElection election = new RegimeElection();
            pipelineRuns++;
            try {
                PipelineRunner.Result run = PipelineRunner.run(registry, pack, election.regimeFor(regime, computing, pack),
                        election.choosing(regime, applied(key)));
                return Optional.of(((MoneyValue) run.valueOf(NodeIds.TOTALINCOME)).amount());
            } catch (FundHistoryRequiredException refused) {
                note(refused);
                return Optional.empty();
            }
        });
    }

    /** Cash in hand after tax: {@link #cashBeforeTax} less the bill (C3). */
    Money takeHome(Allocation allocation) {
        return cashBeforeTax(allocation).minus(price(allocation).bill());
    }

    /**
     * Every CASH component less every CASH_OUTFLOW one: the most an employee could pay from take-home before tax, and
     * so the ceiling of what a contribution can add (W-238k C3a).
     */
    Money cashBeforeTax(Allocation allocation) {
        Money cash = Money.ZERO;
        for (Employment each : applied(allocation).entered().employments()) {
            for (SalaryComponent component : each.components()) {
                cash = cash.plus(inHand(pack.components().get(component.code()).cashNature(), component.annualAmount()));
            }
        }
        return cash;
    }

    /** {@code contribution}'s amount as entered for the employment, nil where it is not entered (W-238k). */
    Money entered(Movable.Contribution contribution) {
        return enteredAmount(base.entered().employments().get(employment), contribution.code());
    }

    /** What the year's pack says {@code code} is (W-238b). */
    Optimisation.ComponentFacts facts(ComponentCode code) {
        ComponentDefinition row = pack.components().get(code);
        return new Optimisation.ComponentFacts(row.kind().name(), row.cashNature().name(),
                row.feedsInto().stream().map(RuleId::value).sorted().toList(), published(row.liquidity()));
    }

    /**
     * The rules {@code item} can feed, sorted: its component's row, or its fund's (W-238e) — the fund's own, because a
     * contribution is no component.
     *
     * @throws IllegalArgumentException if the year's pack has no row for the fund
     */
    List<String> feedsInto(Movable item) {
        return switch (item) {
            case Movable.Pay pay -> pack.components().get(pay.code()).feedsInto().stream().map(RuleId::value)
                    .sorted().toList();
            case Movable.Fund fund -> fundRow(fund).feedsInto().stream().map(RuleId::value).sorted().toList();
            case Movable.Contribution contribution -> pack.components().get(contribution.code()).feedsInto().stream()
                    .map(RuleId::value).sorted().toList();
        };
    }

    /**
     * When the money {@code item} puts into a fund can come out (W-238e1): its component's row, or its fund's.
     *
     * @throws IllegalArgumentException if the year's pack has no row for the fund
     */
    Optimisation.Liquidity liquidity(Movable item) {
        return published(switch (item) {
            case Movable.Pay pay -> pack.components().get(pay.code()).liquidity();
            case Movable.Fund fund -> fundRow(fund).liquidity();
            case Movable.Contribution contribution -> pack.components().get(contribution.code()).liquidity();
        });
    }

    private EmployerFundDefinition fundRow(Movable.Fund fund) {
        EmployerFundDefinition row = pack.funds().get(new EmployerFundCode(fund.name()));
        if (row == null) {
            throw new IllegalArgumentException(pack.taxYear().label() + " defines no employer fund " + fund.name());
        }
        return row;
    }

    private static Optimisation.Liquidity published(Liquidity liquidity) {
        return new Optimisation.Liquidity(
                liquidity.terms().stream()
                        .sorted(Comparator.comparing((WithdrawalTerm term) -> term.validity().from())
                                .thenComparing(WithdrawalTerm::event))
                        .map(term -> new Optimisation.WithdrawalTerm(term.event(), term.terms(), term.validity().from(),
                                term.validity().to(), term.provenance().sourceDescription()))
                        .toList(),
                liquidity.unstated().stream()
                        .sorted(Comparator.comparing((UnstatedTerms gap) -> gap.validity().from()))
                        .map(gap -> new Optimisation.UnstatedTerms(gap.validity().from(), gap.validity().to(),
                                gap.reason()))
                        .toList());
    }

    /**
     * The cash before tax that moving {@code item} from its amount in {@code from} to its amount in {@code to} adds, the
     * residual funding it, and its followers' moves with it (W-238d): the residual funds an employer's share, and an
     * employee's share withheld is cash out. A fund contribution is never in hand, which is what {@link #takeHome}
     * already reads: it counts components only (C11). What an employee adds to a contribution is paid from take-home,
     * so it is cash out and the residual plays no part (W-238k).
     */
    Money cashImpact(Movable item, Allocation from, Allocation to) {
        if (item instanceof Movable.Contribution) {
            return inHand(cashNature(item), to.amount(item).minus(from.amount(item)));
        }
        CashNature residual = cashNature(from.residual());
        Money impact = cashImpact(residual, cashNature(item), from.amount(item), to.amount(item));
        if (following.leads(item)) {
            for (Movable follower : following.followers().keySet()) {
                impact = impact.plus(Following.isFundedByResidual(follower)
                        ? cashImpact(residual, CashNature.NON_CASH, from.amount(follower), to.amount(follower))
                        : inHand(cashNature(follower), to.amount(follower).minus(from.amount(follower))));
            }
        }
        return impact;
    }

    private CashNature cashNature(Movable item) {
        return switch (item) {
            case Movable.Pay pay -> pack.components().get(pay.code()).cashNature();
            case Movable.Fund fund -> CashNature.NON_CASH;
            case Movable.Contribution contribution -> pack.components().get(contribution.code()).cashNature();
        };
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
            Money added = allocation.amounts().getOrDefault(new Movable.Contribution(component.code()), Money.ZERO);
            components.add(amount == null && added.equals(Money.ZERO) ? component
                    : new SalaryComponent(component.code(), (amount == null ? component.annualAmount() : amount).plus(added)));
        }
        allocation.amounts().forEach((item, amount) -> {
            if (item instanceof Movable.Pay pay && count(held, pay.code()) == 0) {
                components.add(new SalaryComponent(pay.code(), amount));
            }
            if (item instanceof Movable.Contribution contribution && count(held, contribution.code()) == 0
                    && amount.isGreaterThan(Money.ZERO)) {
                components.add(new SalaryComponent(contribution.code(), amount));
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
