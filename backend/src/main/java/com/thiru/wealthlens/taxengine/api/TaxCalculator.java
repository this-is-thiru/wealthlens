package com.thiru.wealthlens.taxengine.api;

import com.thiru.wealthlens.taxengine.election.ElectionResolver;
import com.thiru.wealthlens.taxengine.election.RegimeElection;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.Declaration;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.node.CapitalGainsNode;
import com.thiru.wealthlens.taxengine.node.CapitalGainsTerm;
import com.thiru.wealthlens.taxengine.node.ShippedNodes;
import com.thiru.wealthlens.taxengine.optimise.ClaimPricing;
import com.thiru.wealthlens.taxengine.optimise.DeductionRoomPricing;
import com.thiru.wealthlens.taxengine.optimise.RentRoomPricing;
import com.thiru.wealthlens.taxengine.optimise.RoomFilling;
import com.thiru.wealthlens.taxengine.optimise.SalaryOptimiser;
import com.thiru.wealthlens.taxengine.pipeline.AdvanceTaxPosition;
import com.thiru.wealthlens.taxengine.pipeline.AskedDeclarations;
import com.thiru.wealthlens.taxengine.pipeline.BucketOutcome;
import com.thiru.wealthlens.taxengine.pipeline.CapitalGainsHead;
import com.thiru.wealthlens.taxengine.pipeline.HeadsAfterSetOff;
import com.thiru.wealthlens.taxengine.pipeline.HousePropertyHead;
import com.thiru.wealthlens.taxengine.pipeline.InterestAndFees;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeRegistry;
import com.thiru.wealthlens.taxengine.pipeline.OtherSourcesHead;
import com.thiru.wealthlens.taxengine.pipeline.PipelineRunner;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.result.ComputationId;
import com.thiru.wealthlens.taxengine.result.ComputationProjector;
import com.thiru.wealthlens.taxengine.result.ComputationReceipt;
import com.thiru.wealthlens.taxengine.result.TaxComputation;
import com.thiru.wealthlens.taxengine.rules.cap.Fixed;
import com.thiru.wealthlens.taxengine.rules.cap.LeastOf;
import com.thiru.wealthlens.taxengine.rules.cap.PercentOfBase;
import com.thiru.wealthlens.taxengine.rules.condition.ConditionContext;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.condition.ReturnFacts;
import com.thiru.wealthlens.taxengine.rules.definition.LossCarryForward;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource;
import com.thiru.wealthlens.taxengine.rules.filing.FilledCell;
import com.thiru.wealthlens.taxengine.rules.filing.ItrFormSelector;
import com.thiru.wealthlens.taxengine.rules.filing.ItrSelection;
import com.thiru.wealthlens.taxengine.rules.filing.ScheduleMapper;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.CertificateCode;
import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.ReturnKind;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackProvider;
import com.thiru.wealthlens.taxengine.rules.regime.DeadlineSpec;
import com.thiru.wealthlens.taxengine.rules.regime.ElectionKind;
import com.thiru.wealthlens.taxengine.rules.regime.FormRef;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import com.thiru.wealthlens.taxengine.rules.regime.VehicleRoute;
import com.thiru.wealthlens.taxengine.rules.registry.GoverningInstruments;
import com.thiru.wealthlens.taxengine.rules.temporal.Provision;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Runs the shipped pipeline. The engine reads no clock and fetches nothing: everything arrives in {@link EngineInputs}. */
@Component
@RequiredArgsConstructor
public class TaxCalculator {

    /** The rules a salary certificate's standard deduction and cess are checked against (W-128c). */
    private static final RuleSetId STANDARD_DEDUCTION = new RuleSetId("RS-SAL-STANDARD-DEDUCTION");
    private static final RuleSetId HEALTH_EDUCATION_CESS = new RuleSetId("RS-RATE-HEALTH-EDUCATION-CESS");

    private static final NodeRegistry REGISTRY = NodeRegistry.of(ShippedNodes.all());

    private final RulePackProvider rulePacks;

    /**
     * The inputs' year under their regime.
     *
     * @throws IllegalArgumentException if the pack has no such regime, or the inputs are refused
     */
    public ComputedTax compute(EngineInputs inputs) {
        RulePack pack = shipped(inputs.taxYear());
        PipelineRunner.Result run = PipelineRunner.run(REGISTRY, pack, spec(pack, inputs.regime()), inputs);
        ComputationId id = ComputationId.fresh();
        TaxComputation computation = ComputationProjector.project(run, inputs, id);
        InterestAndFees interestAndFees = (InterestAndFees) run.valueOf(NodeIds.INTEREST_AND_FEES);
        return new ComputedTax(
                new ComputedTax.Provenance(id.value(), ComputationReceipt.of(id, inputs.taxYear()).value(), inputs.taxYear().label(),
                        inputs.regime().value(), computation.inputs().sha256(),
                        computation.lawAndExec().values().stream().map(Object::toString).sorted().toList()),
                new ComputedTax.Figures(computation.totalIncome(), computation.netPayableBeforeFilingEffects(),
                        ((MoneyValue) run.valueOf(NodeIds.CREDITS)).amount(), ((MoneyValue) run.valueOf(NodeIds.ROUND_NETPAYABLE)).amount(),
                        ((MoneyValue) run.valueOf(NodeIds.CHAPTERVIII)).amount(),
                        ((HousePropertyHead) run.valueOf(NodeIds.HOUSEPROPERTY_INCOME)).total()
                                .plus(((OtherSourcesHead) run.valueOf(NodeIds.OTHERSOURCES_INCOME)).asMoney())),
                new ComputedTax.Dues(advanceTax((AdvanceTaxPosition) run.valueOf(NodeIds.ADVANCE_TAX), interestAndFees),
                        owed(interestAndFees, false), owed(interestAndFees, true)),
                new ComputedTax.Workings(steps(computation, pack), buckets((CapitalGainsHead) run.valueOf(NodeIds.CAPITALGAINS_INCOME)),
                        notices(run)));
    }

    private static List<ComputedTax.Notice> notices(PipelineRunner.Result run) {
        return run.annotations().stream().flatMap(annotation -> annotation.notices().stream()
                .map(notice -> new ComputedTax.Notice(annotation.sequence(), notice.severity().name(), notice.message(),
                        notice.relatedRules().stream().map(RuleId::value).toList()))).toList();
    }

    /**
     * The form the inputs' return of {@code kind} is furnished in, and every form's verdict (DR 21 I4; W-091, D-653). The
     * forms read the person's facts and answers, and the computed return: its node values, the heads it has income
     * under and its return facts.
     *
     * @throws IllegalArgumentException if no pack ships for the year, the regime is not the year's, the inputs are
     *     refused, or no form of the year serves {@code kind}
     */
    public ItrFormChoice chooseForm(EngineInputs inputs, ReturnKind kind) {
        RulePack pack = shipped(inputs.taxYear());
        Set<String> kinds = new TreeSet<>(pack.itrForms().values().stream()
                .flatMap(form -> form.returnKinds().stream()).map(ReturnKind::value).toList());
        if (!kinds.contains(kind.value())) {
            throw new IllegalArgumentException("No form for " + inputs.taxYear().label() + " serves the return kind "
                    + kind.value() + "; the year's kinds are " + kinds);
        }
        RegimeSpec regime = spec(pack, inputs.regime());
        PipelineRunner.Result run = PipelineRunner.run(REGISTRY, pack, regime, inputs);
        ReturnFacts returned = ComputationProjector.project(run, inputs, ComputationId.fresh()).returnFacts();
        ConditionContext facts = NodeContext.forRun(pack, regime, inputs).conditions()
                .reporting(returned)
                .having(computedAmounts(run), headsWithIncome(returned));
        ItrSelection selection = ItrFormSelector.select(pack.itrForms().values(), inputs.assessee().type(), kind, facts);
        List<FilledCell> cells = selection.chosen()
                .map(form -> ScheduleMapper.fill(pack.itrForms().get(form),
                        figures(run, returned, inputs)))
                .orElse(List.of());
        return ItrFormChoice.of(inputs.taxYear(), inputs.regime(), kind, selection, pack.itrForms(), cells);
    }

    private static ScheduleMapper.Figures figures(PipelineRunner.Result run, ReturnFacts returned, EngineInputs inputs) {
        Optional<TaxByBasis> byBasis = Optional.ofNullable(run.values().get(NodeIds.TAX_BYBASIS))
                .filter(TaxByBasis.class::isInstance).map(TaxByBasis.class::cast);
        Map<String, Money> special = new java.util.HashMap<>();
        byBasis.ifPresent(tax -> tax.atSpecialRates().forEach((rate, amount) -> special.put(rate.value(), amount)));
        Map<String, Money> otherSources = ((OtherSourcesHead) run.valueOf(NodeIds.OTHERSOURCES_INCOME)).grossByKind();
        return new ScheduleMapper.Figures(computedAmounts(run), tracedAmounts(run), returned,
                byBasis.map(TaxByBasis::atSlabRates), special, otherSources, items(run), tracedAmountsByKind(run),
                setOff(run), accruals(run, inputs), declaredAmounts(inputs));
    }

    /** Each amount the person declared, by question, for the cells that show one (W-258a, D-779). */
    private static Map<DeclarationCode, Money> declaredAmounts(EngineInputs inputs) {
        Map<DeclarationCode, Money> amounts = new java.util.HashMap<>();
        inputs.filing().declarations().forEach((code, answer) -> {
            if (answer instanceof Declaration.Amount amount) {
                amounts.put(code, amount.amount());
            }
        });
        return amounts;
    }

    /**
     * Each amount that arose in the year with its day, for ITR-2's accrual tables (W-323, D-662): a bucket's gain on its
     * transfer date under its part's kind, an item of other-source income's gross on the day it was received under its kind.
     */
    private static ScheduleMapper.Accruals accruals(PipelineRunner.Result run, EngineInputs inputs) {
        Map<String, List<ScheduleMapper.Accrued>> byKind = new java.util.HashMap<>();
        ((CapitalGainsHead) run.valueOf(NodeIds.CAPITALGAINS_INCOME)).bucketOutcomes().stream()
                .filter(outcome -> outcome.part().isPresent())
                .forEach(outcome -> byKind.computeIfAbsent(FieldSource.FromCapitalGainsBuckets.itemKindOf(outcome.part().get()),
                        kind -> new ArrayList<>()).add(new ScheduleMapper.Accrued(outcome.gain(),
                                Optional.of(outcome.facts().transferredOn()))));
        inputs.entered().otherSourceItems().forEach(item -> byKind.computeIfAbsent(item.kind().name(),
                kind -> new ArrayList<>()).add(new ScheduleMapper.Accrued(item.received(), item.receivedOn())));
        return new ScheduleMapper.Accruals(inputs.temporal().taxYearStart(), byKind);
    }

    /** Set-off row by row, each row's columns by name — Schedules CYLA, BFLA, CFL (W-093a Q4a, Q5a; D-658). */
    private static Map<String, Map<String, Money>> setOff(PipelineRunner.Result run) {
        HeadsAfterSetOff heads = (HeadsAfterSetOff) run.valueOf(NodeIds.SETOFF);
        CapitalGainsHead gains = (CapitalGainsHead) run.valueOf(NodeIds.CAPITALGAINS_INCOME);
        Map<String, Map<String, Money>> rows = new java.util.HashMap<>();
        heads.rows().rows().forEach(row -> rows.put(row.row(), Map.of("income", row.income(),
                "housePropertyLossSetOff", row.housePropertyLossSetOff(), "otherSourcesLossSetOff", row.otherSourcesLossSetOff(),
                "afterCurrentYear", row.afterCurrentYear(), "broughtForwardSetOff", row.broughtForwardSetOff(),
                "afterBroughtForward", row.afterBroughtForward())));
        rows.put("CURRENT_YEAR_LOSS", lossColumns(heads.rows().currentYearLoss()));
        rows.put("CURRENT_YEAR_LOSS_LEFT", lossColumns(heads.rows().currentYearLossLeft()));
        rows.put("CAPITAL_GAINS_LOSS_LEFT", Map.of("shortTerm", gains.shortTermLossLeft(), "longTerm", gains.longTermLossLeft()));
        return rows;
    }

    private static Map<String, Money> lossColumns(Map<HeadCode, Money> losses) {
        return Map.of("housePropertyLoss", losses.getOrDefault(HeadCode.HOUSE_PROPERTY, Money.ZERO),
                "otherSourcesLoss", losses.getOrDefault(HeadCode.OTHER_SOURCES, Money.ZERO));
    }

    /** Each kind of item's figures, for the schedule groups that repeat over them (W-093a C2a, D-658). */
    private static Map<String, List<Map<String, Money>>> items(PipelineRunner.Result run) {
        Map<String, List<Map<String, Money>>> items = new java.util.HashMap<>();
        ((CapitalGainsHead) run.valueOf(NodeIds.CAPITALGAINS_INCOME)).bucketOutcomes().stream()
                .filter(outcome -> outcome.part().isPresent())
                .forEach(outcome -> items.computeIfAbsent(
                        FieldSource.FromCapitalGainsBuckets.itemKindOf(outcome.part().get()), kind -> new ArrayList<>())
                        .add(Map.of("consideration", outcome.consideration(), "costOfAcquisition", outcome.costOfAcquisition(),
                                "transferExpenditure", outcome.transferExpenditure(), "gain", outcome.gain())));
        HousePropertyHead head = (HousePropertyHead) run.valueOf(NodeIds.HOUSEPROPERTY_INCOME);
        items.put("HOUSE_PROPERTY", head.properties().stream().map(property -> Map.of(
                "gross", property.gross(), "unrealisedRent", property.unrealisedRent(),
                "localTaxes", property.localTaxes(), "annualValue", property.annualValue(),
                "ownShare", property.ownShare(), "standardDeduction", property.standardDeduction(),
                "interest", property.interest(), "arrears", property.arrears(), "income", property.income())).toList());
        return items;
    }

    /** Each rule's figures in the run's trace, summed by step kind — for a cell reading one kind of line (D-658). */
    private static Map<RuleId, Map<String, Money>> tracedAmountsByKind(PipelineRunner.Result run) {
        Map<RuleId, Map<String, Money>> byRule = new java.util.HashMap<>();
        run.trace().stream().filter(line -> line.out() instanceof MoneyValue)
                .forEach(line -> byRule.computeIfAbsent(line.rule(), rule -> new java.util.HashMap<>())
                        .merge(line.kind().name(), ((MoneyValue) line.out()).amount(), Money::plus));
        return byRule;
    }

    /** Each rule's figures in the run's trace, summed — a schedule cell's {@code FromRules} (W-093). */
    private static Map<RuleId, Money> tracedAmounts(PipelineRunner.Result run) {
        Map<RuleId, Money> byRule = new java.util.HashMap<>();
        run.trace().stream().filter(line -> line.out() instanceof MoneyValue)
                .forEach(line -> byRule.merge(line.rule(), ((MoneyValue) line.out()).amount(), Money::plus));
        return byRule;
    }

    private static Map<NodeId, Money> computedAmounts(PipelineRunner.Result run) {
        return run.values().entrySet().stream()
                .filter(entry -> entry.getValue() instanceof MoneyValue || entry.getValue() instanceof HousePropertyHead
                        || entry.getValue() instanceof OtherSourcesHead)
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().asMoney()));
    }

    /** The heads the return computed a figure under, income or loss — a loss is still income under the head. */
    private static Set<HeadCode> headsWithIncome(ReturnFacts returned) {
        return returned.incomeByHead().entrySet().stream()
                .filter(entry -> entry.getValue().isGreaterThan(Money.ZERO) || entry.getValue().isLessThan(Money.ZERO))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /** Every candidate regime priced on these inputs. */
    public RegimeComparison compareRegimes(EngineInputs inputs) {
        RulePack pack = shipped(inputs.taxYear());
        var outcome = ElectionResolver.resolve(new RegimeElection(), REGISTRY, pack, spec(pack, inputs.regime()), inputs)
                .outcome();
        return new RegimeComparison(outcome.chosen().value(), outcome.mode().name(), outcome.alternatives().stream()
                .map(priced -> new RegimeComparison.Priced(priced.choice().value(), priced.netPayable())).toList());
    }

    /**
     * Each regime reached by opting out of the year's default, priced against it, with its deadline from the year's pack
     * (W-114b, D-667). Empty where the comparison prices no such regime.
     *
     * @throws IllegalArgumentException where no pack ships for the year
     * @throws IllegalStateException where a deadline names a provision whose due date a draft does not hold
     */
    public List<RegimeElectionDue> regimeElection(EngineInputs inputs) {
        RulePack pack = shipped(inputs.taxYear());
        Map<String, Money> bills = compareRegimes(inputs).priced().stream()
                .collect(Collectors.toMap(RegimeComparison.Priced::regime, RegimeComparison.Priced::bill));
        Money defaultBill = bills.get(defaultRegime(inputs.taxYear()).value());
        if (defaultBill == null) {
            return List.of();
        }
        RegimeSpec computing = spec(pack, inputs.regime());
        PipelineRunner.Result run = PipelineRunner.run(REGISTRY, pack, computing, inputs);
        ReturnFacts returned = ComputationProjector.project(run, inputs, ComputationId.fresh()).returnFacts();
        ConditionContext facts = NodeContext.forRun(pack, computing, inputs).conditions().reporting(returned)
                .having(computedAmounts(run), headsWithIncome(returned));
        return pack.regimes().values().stream()
                .filter(regime -> regime.election().kind() == ElectionKind.OPT_OUT && bills.containsKey(regime.code().value()))
                .map(regime -> {
                    VehicleRoute route = regime.election().vehicleFor(facts);
                    return new RegimeElectionDue(regime.code().value(), dueBy(regime.election().deadline(), inputs),
                            route.vehicle().name(), route.form().map(FormRef::number), defaultBill,
                            bills.get(regime.code().value()));
                })
                .toList();
    }

    /**
     * A deadline as a date. {@code FilingFacts.dueDate} is the due date of the return, the one date s.263(1) and, before
     * TY 2026-27, s.139(1) name; a deadline under any other provision is refused, not read as that date.
     */
    static Optional<LocalDate> dueBy(DeadlineSpec deadline, EngineInputs inputs) {
        return switch (deadline) {
            case DeadlineSpec.DueDateUnder under when RETURN_DUE_DATE.contains(under.section()) ->
                    Optional.of(inputs.filing().dueDate());
            case DeadlineSpec.DueDateUnder under -> throw new IllegalStateException("An election's deadline names the"
                    + " due date under s." + under.section() + ", and a draft holds only the return's due date (s."
                    + String.join(", s.", RETURN_DUE_DATE) + ")");
            case DeadlineSpec.WithTheReturn ignored -> Optional.empty();
            case DeadlineSpec.NoDeadline ignored -> Optional.empty();
        };
    }

    /**
     * The allocation of the movable pay that leaves the least tax, the regime chosen on each (W-238a).
     *
     * @throws IllegalArgumentException if the space does not fit the year's pack or the inputs
     */
    public Optimisation optimise(AllocationSpace space) {
        RulePack pack = shipped(space.base().taxYear());
        return SalaryOptimiser.optimise(REGISTRY, pack, spec(pack, space.base().regime()), space);
    }

    /**
     * The room left under Schedule XV's ceiling and the tax filling it in the year would save; empty where none is left
     * (W-114a, D-666).
     *
     * @throws IllegalArgumentException where no pack ships for the year
     */
    public List<DeductionRoom> priceDeductionRoom(EngineInputs inputs) {
        RulePack pack = shipped(inputs.taxYear());
        return DeductionRoomPricing.price(REGISTRY, pack, spec(pack, inputs.regime()), inputs);
    }

    /**
     * The bill with every room of {@code families} filled in one run (W-122b C3a, D-720): the lower end of a coarse
     * figure's band, the rooms counted together so an overlap is counted once. With nothing to fill, the bill as the
     * inputs stand.
     *
     * @throws IllegalArgumentException where no pack ships for the year
     */
    public Money billWithRoomsFilled(EngineInputs inputs, Set<RoomFamily> families) {
        RulePack pack = shipped(inputs.taxYear());
        return RoomFilling.billWithRoomsFilled(REGISTRY, pack, spec(pack, inputs.regime()), inputs, families);
    }

    /**
     * The year's printed layout of {@code code}, read from the Gazette (W-126a): its parts by heading and its lines by label,
     * for a parser to find them by. Empty where the year's law prescribes no such certificate.
     *
     * @throws IllegalArgumentException where no pack ships for the year
     */
    public Optional<CertificateLayout> certificate(TaxYearId year, CertificateCode code) {
        return Optional.ofNullable(shipped(year).certificates().get(code)).map(CertificateLayout::of);
    }

    /**
     * How each claim of the year is made, by rule id as the pack holds them (W-118): every claim, made or not.
     *
     * @throws IllegalArgumentException where no pack ships for the year
     */
    public List<ClaimPlaybook> claimPlaybooks(TaxYearId year) {
        return shipped(year).claims().stream().map(ClaimPlaybook::of).toList();
    }

    /**
     * What a claim's evidence says in the year's words, by sentence code, each to be filled with {@code {amount}} or
     * {@code {months}} (W-119c C4a).
     *
     * @throws IllegalArgumentException where no pack ships for the year
     */
    public Map<String, String> evidenceSentences(TaxYearId year) {
        return shipped(year).explanations().sentences().evidence();
    }

    /**
     * What is said of a claim made, in the year's words, by sentence code (W-121 C3a).
     *
     * @throws IllegalArgumentException where no pack ships for the year
     */
    public Map<String, String> claimSentences(TaxYearId year) {
        return shipped(year).explanations().sentences().claims();
    }

    /**
     * Each claim the inputs' computation allows, with what it takes off total income and off the bill, each measured by
     * a second run with the claim withheld (W-118 C1a).
     *
     * @throws IllegalArgumentException where no pack ships for the year
     */
    public List<PricedClaim> priceClaims(EngineInputs inputs) {
        RulePack pack = shipped(inputs.taxYear());
        return ClaimPricing.price(REGISTRY, pack, spec(pack, inputs.regime()), inputs).stream()
                .map(measured -> new PricedClaim(ClaimPlaybook.of(measured.claim()), measured.allowed(), measured.worth(),
                        measured.finalAmount(), measured.finalAmountWithout()))
                .toList();
    }

    /**
     * What rent the draft does not yet reflect would take off the bill; empty where it would earn nothing more (W-115,
     * D-670).
     *
     * @throws IllegalArgumentException where no pack ships for the year
     */
    public Optional<RentRoom> priceRentRoom(EngineInputs inputs) {
        RulePack pack = shipped(inputs.taxYear());
        return RentRoomPricing.price(REGISTRY, pack, spec(pack, inputs.regime()), inputs);
    }

    /**
     * The term of a lot with these facts, by the row the year's pack holds for it: what a sale on the facts' date would be (W-160g).
     * The term alone, since whether anything is charged turns on a sale that has not happened.
     *
     * @param inputs the year and regime, which choose the rows
     * @throws IllegalArgumentException where no row covers the lot
     */
    public HeldTerm termIfSold(EngineInputs inputs, CapitalGainsInput.BucketFacts facts) {
        RulePack pack = shipped(inputs.taxYear());
        NodeContext context = NodeContext.forRun(pack, spec(pack, inputs.regime()), inputs).forNode(new CapitalGainsNode());
        CapitalGainsInput.Bucket bucket = new CapitalGainsInput.Bucket(Money.ZERO, Money.ZERO, List.of(), facts, Optional.empty());
        BucketOutcome.TermDecision decision = CapitalGainsTerm.of(context, bucket, Money.ZERO, new ArrayList<>());
        return new HeldTerm(decision.term().name(), decision.row().value(), decision.months(),
                CapitalGainsTerm.longTermFrom(facts.acquiredOn(), decision.months()));
    }

    /**
     * A held lot's term.
     *
     * @param term long or short
     * @param row the {@code RS-CG-TERM} row it met
     * @param months that row's holding period
     * @param longTermFrom the first day a sale of the lot is long-term under that row
     */
    public record HeldTerm(String term, String row, int months, LocalDate longTermFrom) {
    }

    /**
     * The year's pack. A year none ships for is the caller's to correct, not a pack defect, so it is refused as their
     * mistake and names the years they may ask for (W-263).
     */
    private RulePack shipped(TaxYearId year) {
        if (!rulePacks.availableYears().contains(year)) {
            throw new IllegalArgumentException("No rule pack ships for " + year.label() + "; the application ships "
                    + new TreeSet<>(rulePacks.availableYears().stream().map(TaxYearId::label).toList()));
        }
        return rulePacks.load(year);
    }

    /**
     * The components a pay line may carry in the year: those under the head Salaries, live in it, by name (W-133a). The head is
     * the declared partition a pay line must sit in (D-090), so a component offered here is one an employment accepts.
     *
     * @throws IllegalArgumentException where no pack ships for the year
     */
    public List<PayLineComponent> payLineComponents(TaxYearId year) {
        return shipped(year).components().values().stream()
                .filter(component -> component.head().filter(HeadCode.SALARIES::equals).isPresent() && component.lifecycle().isLiveIn(year))
                .map(component -> new PayLineComponent(component.code().value(), component.displayName()))
                .sorted(java.util.Comparator.comparing(PayLineComponent::displayName)).toList();
    }

    /**
     * The regime the year's pack calls its default.
     *
     * @throws IllegalArgumentException where no pack ships for the year
     */
    public RegimeCode defaultRegime(TaxYearId year) {
        return shipped(year).regimes().values().stream().filter(RegimeSpec::isDefault).map(RegimeSpec::code)
                .findFirst().orElseThrow(() -> new IllegalStateException("The pack for " + year.label() + " has no default"));
    }

    /**
     * The year's law a salary certificate is checked against (W-128c): the default regime, each regime's standard deduction
     * ceiling — the fixed limb of the rule that applies in it — and the cess rate.
     *
     * @throws IllegalArgumentException where no pack ships for the year
     * @throws IllegalStateException where the pack states either in a shape this reading does not know
     */
    public SalaryCertificateLaw salaryCertificateLaw(TaxYearId year) {
        RulePack pack = shipped(year);
        Map<RegimeCode, Money> ceilings = new LinkedHashMap<>();
        for (RegimeCode regime : pack.regimes().keySet()) {
            versionsInForce(pack, STANDARD_DEDUCTION, year).filter(version -> version.regimes().appliesIn(regime))
                    .flatMap(version -> version.cap().stream()).filter(LeastOf.class::isInstance)
                    .flatMap(cap -> ((LeastOf) cap).limbs().stream()).map(LeastOf.Limb::shape).filter(Fixed.class::isInstance)
                    .map(shape -> ((Fixed) shape).amount()).findFirst().ifPresent(amount -> ceilings.put(regime, amount));
        }
        Rate cess = versionsInForce(pack, HEALTH_EDUCATION_CESS, year).flatMap(version -> version.cap().stream())
                .filter(PercentOfBase.class::isInstance).map(cap -> ((PercentOfBase) cap).percent()).findFirst()
                .orElseThrow(() -> new IllegalStateException("The pack for " + year.label() + " states no cess rate"));
        return new SalaryCertificateLaw(defaultRegime(year), ceilings, cess);
    }

    private static Stream<RuleVersion> versionsInForce(RulePack pack, RuleSetId set, TaxYearId year) {
        return pack.membersOf(List.of(set)).stream().map(pack::rule).flatMap(row -> row.versions().stream())
                .filter(version -> version.validity().contains(year.startsOn()));
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
     * What a computation says of itself, by code (W-122b, D-720).
     *
     * @throws IllegalArgumentException where no pack ships for the year
     */
    public Map<String, String> computationSentences(TaxYearId year) {
        return shipped(year).explanations().sentences().computation();
    }

    /**
     * The computation report's sentences in the year's words, by code (W-123a C3a).
     *
     * @throws IllegalArgumentException where no pack ships for the year
     */
    public Map<String, String> reportSentences(TaxYearId year) {
        return shipped(year).explanations().sentences().report();
    }

    /**
     * How the year's governing instruments are cited (W-123a C3a).
     *
     * @throws IllegalArgumentException where no pack ships for the year
     */
    public InstrumentCitations instrumentCitations(TaxYearId year) {
        GoverningInstruments instruments = shipped(year).instruments();
        return new InstrumentCitations(instruments.act().citation(), instruments.financeAct().citation(),
                instruments.rules().citation());
    }

    /**
     * The verification levels each of {@code ruleIds} the year's pack holds states, across its versions' sources and
     * the instruments that introduced them, by name (W-123a C1a) — a rule it no longer holds is left out, as
     * {@link #ruleChecksums} leaves it.
     *
     * @throws IllegalArgumentException where no pack ships for the year
     */
    public Map<String, Set<String>> verificationLevels(TaxYearId year, Set<String> ruleIds) {
        Map<String, Set<String>> levels = new TreeMap<>();
        shipped(year).rules().forEach((id, row) -> {
            if (ruleIds.contains(id.value())) {
                levels.put(id.value(), row.versions().stream()
                        .flatMap(version -> Stream.of(version.provenance().verificationLevel(),
                                version.introducedBy().verificationLevel()))
                        .map(Enum::name)
                        .collect(Collectors.toCollection(TreeSet::new)));
            }
        });
        return levels;
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

    /**
     * The questions the year's return asks the person and the engine cannot compute, in code order (W-090a2). Empty where
     * the application ships no pack for the year, so a draft for such a year has nothing to answer.
     */
    public List<DeclarationQuestion> declarations(TaxYearId year) {
        if (!rulePacks.availableYears().contains(year)) {
            return List.of();
        }
        return rulePacks.load(year).declarations().values().stream()
                .sorted(Comparator.comparing(definition -> definition.code().value()))
                .map(definition -> new DeclarationQuestion(definition.code().value(), definition.question(),
                        definition.answerKind().name(), definition.provenance().sourceDescription()))
                .toList();
    }

    /**
     * Refuses answers the year's catalogue does not ask, or answers in the wrong kind — the check a run makes, made when
     * a draft is saved, so a wrong answer is never stored (D-520 C1a).
     *
     * @throws IllegalArgumentException naming the question, where an answer is not one the year asks
     */
    public void requireAsked(TaxYearId year, Map<DeclarationCode, Declaration> answers) {
        if (answers.isEmpty()) {
            return;
        }
        if (!rulePacks.availableYears().contains(year)) {
            throw new IllegalArgumentException("No questions are asked for " + year.label()
                    + ", since no rule pack ships for it; its declarations must be empty");
        }
        AskedDeclarations.of(answers, rulePacks.load(year));
    }

    /**
     * Every section of an Income-tax Act cited by any version of any rule in the packs the application ships, with the citing
     * rule — so a reader outside the engine can name the rules an instrument amending a section may touch (W-236).
     */
    public List<ProvisionCitation> provisionsCited() {
        Set<ProvisionCitation> cited = new LinkedHashSet<>();
        for (TaxYearId year : rulePacks.availableYears()) {
            rulePacks.load(year).rules().forEach((id, row) -> row.versions().forEach(version -> {
                for (Provision provision : List.of(version.provision1961(), version.provision2025())) {
                    CitedSections.actOf(provision).ifPresent(act -> CitedSections.of(provision.reference())
                            .forEach(section -> cited.add(new ProvisionCitation(act, section, id.value()))));
                }
            }));
        }
        return List.copyOf(cited);
    }

    /** The provisions whose due date is the return's: what {@code FilingFacts.dueDate} holds (W-114b C2a). */
    private static final Set<String> RETURN_DUE_DATE = new TreeSet<>(Set.of("263(1)", "139(1)"));

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

    private static ComputedTax.AdvanceTax advanceTax(AdvanceTaxPosition position, InterestAndFees owed) {
        return new ComputedTax.AdvanceTax(position.liable(), position.amount(), position.instalments().stream()
                .map(i -> new ComputedTax.AdvanceTax.Instalment(i.dueOn(), i.dueByThen(), i.paidByThen(), i.stillOwed())).toList(),
                owed.reliefPaidBy());
    }

    /** The items the person owes, or with {@code receivable} the interest added to a refund (W-112b C4a). */
    private static List<ComputedTax.Owed> owed(InterestAndFees owed, boolean receivable) {
        return owed.items().stream().filter(item -> (item.kind() == InterestAndFees.Kind.RECEIVABLE) == receivable)
                .map(item -> new ComputedTax.Owed(item.rule().value(), item.kind().name(), item.amount())).toList();
    }

    private static List<ComputedTax.Step> steps(TaxComputation computation, RulePack pack) {
        return computation.trace().stream()
                .map(line -> new ComputedTax.Step(line.sequence(), line.node().value(), line.rule().value(), line.kind().name(),
                        line.limitedBy().orElse(""), ExplanationRenderer.money(line.in()), ExplanationRenderer.money(line.out()),
                        ExplanationRenderer.explain(line, pack.explanations())))
                .toList();
    }
}
