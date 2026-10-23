package com.thiru.wealthlens.taxengine.golden;

import com.thiru.wealthlens.taxengine.election.ElectionResolver;
import com.thiru.wealthlens.taxengine.election.HouseElection;
import com.thiru.wealthlens.taxengine.election.RegimeElection;
import com.thiru.wealthlens.taxengine.election.SetOffOrderElection;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.HouseProperty;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.input.LedgerSnapshot;
import com.thiru.wealthlens.taxengine.input.OtherSourceItem;
import com.thiru.wealthlens.taxengine.input.OtherSourceKind;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.node.CapitalGainsNode;
import com.thiru.wealthlens.taxengine.node.CessNode;
import com.thiru.wealthlens.taxengine.node.ChapterEightNode;
import com.thiru.wealthlens.taxengine.node.ComponentPartitionNode;
import com.thiru.wealthlens.taxengine.node.EmployerContributionNode;
import com.thiru.wealthlens.taxengine.node.ExemptionNode;
import com.thiru.wealthlens.taxengine.node.GrossSalaryNode;
import com.thiru.wealthlens.taxengine.node.GrossTotalIncomeNode;
import com.thiru.wealthlens.taxengine.node.HousePropertyNode;
import com.thiru.wealthlens.taxengine.node.MedicalTreatmentAbroadNode;
import com.thiru.wealthlens.taxengine.node.MonetarySalaryIncomeNode;
import com.thiru.wealthlens.taxengine.node.NetPayableNode;
import com.thiru.wealthlens.taxengine.node.OtherSourcesNode;
import com.thiru.wealthlens.taxengine.node.PerquisiteNode;
import com.thiru.wealthlens.taxengine.node.RebateNode;
import com.thiru.wealthlens.taxengine.node.RetirementNode;
import com.thiru.wealthlens.taxengine.node.RoundNetPayableNode;
import com.thiru.wealthlens.taxengine.node.RoundTotalIncomeNode;
import com.thiru.wealthlens.taxengine.node.SalaryBaseNode;
import com.thiru.wealthlens.taxengine.node.SalaryDeductionNode;
import com.thiru.wealthlens.taxengine.node.SalaryIncomeBeforeTravelNode;
import com.thiru.wealthlens.taxengine.node.SalaryIncomeNode;
import com.thiru.wealthlens.taxengine.node.SetOffNode;
import com.thiru.wealthlens.taxengine.node.SurchargeNode;
import com.thiru.wealthlens.taxengine.node.TaxByBasisNode;
import com.thiru.wealthlens.taxengine.node.TotalIncomeNode;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.NodeRegistry;
import com.thiru.wealthlens.taxengine.pipeline.PipelineRunner;
import com.thiru.wealthlens.taxengine.result.ComputationId;
import com.thiru.wealthlens.taxengine.result.ComputationProjector;
import com.thiru.wealthlens.taxengine.result.TaxComputation;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.regime.PipelineSpec;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs a golden case through the real engine: the shipped pack and the shipped regime pipeline, entered at
 * total income, then projected into a {@link TaxComputation} (D-083).
 *
 * <p>The pipeline is the regime's own, kept to the nodes this registry holds and in the shipped order.
 * {@code totalincome} is stood in for, because the heads between gross salary and it are not built yet; the
 * partition and gross salary run on what the case enters (nothing, for T1 and T2). {@link
 * #droppedFromShippedPipeline} names any shipped node left out, so a test can hold that to none (D-091).
 */
final class GoldenCaseRunner {

    /** Every node built so far, the real ones. */
    private static final List<ComputationNode> BUILT = List.of(
            new ComponentPartitionNode(),
            new EmployerContributionNode(),
            new GrossSalaryNode(),
            new SalaryBaseNode(),
            new ExemptionNode(),
            new MonetarySalaryIncomeNode(),
            new PerquisiteNode(),
            new RetirementNode(),
            new SalaryDeductionNode(),
            new SalaryIncomeBeforeTravelNode(),
            new MedicalTreatmentAbroadNode(),
            new SalaryIncomeNode(),
            new HousePropertyNode(),
            new OtherSourcesNode(),
            new CapitalGainsNode(),
            new SetOffNode(),
            new GrossTotalIncomeNode(),
            new ChapterEightNode(),
            new RoundTotalIncomeNode(),
            new TaxByBasisNode(),
            new RebateNode(),
            new SurchargeNode(),
            new CessNode(),
            new NetPayableNode(),
            new RoundNetPayableNode());

    /** Fixed, so two runs of one case project to computations that may differ in nothing (T23-4). */
    private static final ComputationId ID = new ComputationId("00000000-0000-4000-8000-0000000000a1");

    /** The years the application ships, from the manifest beside the locks (D-268). */
    private static final Set<TaxYearId> SHIPPED_YEARS = ClasspathRulePackProvider.shippedYears("rulepacks");

    private final Map<TaxYearId, RulePack> packs = new ConcurrentHashMap<>();

    /**
     * One run.
     *
     * @param provisional whether any line cites an {@code UNVERIFIED} rule version — reported, never skipped
     */
    record Run(PipelineRunner.Result result, TaxComputation computation, boolean provisional) {
    }

    Run run(GoldenCase golden, TaxYearId year) {
        RulePack pack = pack(year);
        NodeRegistry registry = registryWith(new GivenTotalIncome(golden.totalIncome()));
        RegimeSpec entered = enteredAtTotalIncome(pack.regimes().get(golden.regime()), registry);
        EngineInputs inputs = inputs(golden, year);
        PipelineRunner.Result result = PipelineRunner.run(registry, pack, entered, inputs);
        boolean provisional = result.trace().stream()
                .anyMatch(line -> line.provenance().verificationLevel() == VerificationLevel.UNVERIFIED);
        return new Run(result, ComputationProjector.project(result, inputs, ID), provisional);
    }

    /**
     * A run of the shipped pipeline up to a slot, for a golden case stated on one head's facts.
     *
     * @param provisional whether any line cites an {@code UNVERIFIED} rule version — reported, never skipped
     */
    record ShippedPipelineRun(PipelineRunner.Result result, boolean provisional) {
    }

    /**
     * A case stated as salary facts, run through the shipped pipeline to income under "Salaries" (D-104), or on to
     * Chapter VIII where it expects a deduction (D-153).
     */
    ShippedPipelineRun run(SalaryGoldenCase golden, TaxYearId year) {
        RulePack pack = pack(year);
        NodeRegistry registry = NodeRegistry.of(BUILT);
        RegimeSpec shipped = pack.regimes().get(golden.regime());
        List<NodeId> refs = shipped.pipeline().nodeRefs();
        NodeId last = golden.expectsDeduction() ? NodeIds.CHAPTERVIII : NodeIds.SALARY_INCOME;
        List<NodeId> toLast = refs.subList(0, refs.indexOf(last) + 1).stream()
                .filter(registry.registered()::contains)
                .toList();
        RegimeSpec salaryHead = new RegimeSpec(shipped.code(), shipped.displayName(), shipped.provision1961(),
                shipped.provision2025(), shipped.appliesTo(), shipped.rateTable(), shipped.disallowances(),
                shipped.eligibility(), shipped.election(), shipped.lifecycle(), shipped.isDefault(),
                new PipelineSpec(toLast), shipped.behaviour(), shipped.provenance());
        EngineInputs plain = NodeHarness.plainTaxpayer();
        SalaryGoldenCase.Salary salary = golden.salary();
        EngineInputs inputs = new EngineInputs(year, golden.regime(),
                TemporalContext.forTaxYear(year, year.startsOn().plusMonths(5)),
                new EnteredFacts(List.of(new Employment(salary.pay(), salary.termsIncludeDearnessAllowance(), EmployerKind.OTHER, false, Optional.empty())),
                        salary.tenancies(), salary.leaveTravel(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), salary.employerContributions(), salary.retirementFundHistory(), List.of(), salary.qualifyingPayments(), salary.lifeInsurancePolicies(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), salary.providentFundAccounts(), List.of(), List.of(), List.of(), List.of(), Optional.empty()),
                plain.assessee(), plain.family(), ledger(salary), plain.elections(), plain.capitalGains(),
                plain.filing());
        PipelineRunner.Result result = PipelineRunner.run(registry, pack, salaryHead, inputs);
        boolean provisional = result.trace().stream()
                .anyMatch(line -> line.provenance().verificationLevel() == VerificationLevel.UNVERIFIED);
        return new ShippedPipelineRun(result, provisional);
    }

    /**
     * A T6 case, run through the shipped pipeline to Chapter VIII on its houses and other-source items alone, the
     * person aged as {@link GoldenCase} ages them (D-211).
     */
    ShippedPipelineRun run(HouseAndOtherSourcesGoldenCase golden, TaxYearId year) {
        RulePack pack = pack(year);
        NodeRegistry registry = NodeRegistry.of(BUILT);
        RegimeSpec shipped = pack.regimes().get(golden.regime());
        List<NodeId> refs = shipped.pipeline().nodeRefs();
        List<NodeId> toChapterEight = refs.subList(0, refs.indexOf(NodeIds.CHAPTERVIII) + 1).stream()
                .filter(registry.registered()::contains)
                .toList();
        RegimeSpec heads = new RegimeSpec(shipped.code(), shipped.displayName(), shipped.provision1961(),
                shipped.provision2025(), shipped.appliesTo(), shipped.rateTable(), shipped.disallowances(),
                shipped.eligibility(), shipped.election(), shipped.lifecycle(), shipped.isDefault(),
                new PipelineSpec(toChapterEight), shipped.behaviour(), shipped.provenance());
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(year, golden.regime(),
                TemporalContext.forTaxYear(year, year.startsOn().plusMonths(5)),
                new EnteredFacts(List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), List.of(), golden.housePropertiesIn(year), golden.otherSources(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty()),
                aged(plain, year, golden.ageAtYearEnd()), plain.family(), plain.ledgers(), plain.elections(),
                plain.capitalGains(), plain.filing());
        PipelineRunner.Result result = PipelineRunner.run(registry, pack, heads, inputs);
        boolean provisional = result.trace().stream()
                .anyMatch(line -> line.provenance().verificationLevel() == VerificationLevel.UNVERIFIED);
        return new ShippedPipelineRun(result, provisional);
    }

    /**
     * A T6 case with one employment's basic pay added, its set-off order resolved by recomputation over the whole
     * shipped pipeline, through the real total-income node to net payable (D-221).
     */
    ElectionResolver.Resolution<List<HeadCode>> resolveSetOffOrder(HouseAndOtherSourcesGoldenCase golden,
            TaxYearId year, Money basicPay) {
        RulePack pack = pack(year);
        List<ComputationNode> nodes = new ArrayList<>(BUILT);
        nodes.add(new TotalIncomeNode());
        EngineInputs plain = NodeHarness.plainTaxpayer();
        Employment employment = new Employment(List.of(new SalaryComponent(ComponentCodes.BASIC, basicPay)), false,
                EmployerKind.OTHER, false, Optional.empty());
        EngineInputs inputs = new EngineInputs(year, golden.regime(),
                TemporalContext.forTaxYear(year, year.startsOn().plusMonths(5)),
                new EnteredFacts(List.of(employment), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), List.of(), golden.housePropertiesIn(year), golden.otherSources(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty()),
                aged(plain, year, golden.ageAtYearEnd()), plain.family(), plain.ledgers(), plain.elections(),
                plain.capitalGains(), plain.filing());
        return ElectionResolver.resolve(new SetOffOrderElection(), NodeRegistry.of(nodes), pack,
                pack.regimes().get(golden.regime()), inputs);
    }

    /** The regime resolved by recomputation over the whole shipped engine, other-sources interest the only income (D-222). */
    ElectionResolver.Resolution<RegimeCode> resolveRegime(TaxYearId year, Money interest) {
        RulePack pack = pack(year);
        List<ComputationNode> nodes = new ArrayList<>(BUILT);
        nodes.add(new TotalIncomeNode());
        EngineInputs plain = NodeHarness.plainTaxpayer();
        return ElectionResolver.resolve(new RegimeElection(), NodeRegistry.of(nodes), pack,
                pack.regimes().get(plain.regime()), interestOnly(year, interest));
    }

    /** The regime and the set-off order resolved together on {@link #resolveRegime}'s facts (D-236). */
    ElectionResolver.JointResolution resolveRegimeAndSetOffOrder(TaxYearId year, Money interest) {
        RulePack pack = pack(year);
        List<ComputationNode> nodes = new ArrayList<>(BUILT);
        nodes.add(new TotalIncomeNode());
        EngineInputs plain = NodeHarness.plainTaxpayer();
        return ElectionResolver.resolveTogether(List.of(new RegimeElection(), new SetOffOrderElection()),
                NodeRegistry.of(nodes), pack, pack.regimes().get(plain.regime()), interestOnly(year, interest));
    }

    /** The harness taxpayer with other-sources interest the only income. */
    private static EngineInputs interestOnly(TaxYearId year, Money interest) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        OtherSourceItem item = new OtherSourceItem(OtherSourceKind.OTHER_INTEREST, interest, Money.ZERO, Money.ZERO,
                Money.ZERO, false);
        return new EngineInputs(year, plain.regime(),
                TemporalContext.forTaxYear(year, year.startsOn().plusMonths(5)),
                new EnteredFacts(List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(item), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty()),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
    }

    /**
     * The houses' specification resolved by recomputation over the whole shipped engine (D-230), other-sources interest
     * the only other income, the harness taxpayer in the given regime.
     */
    ElectionResolver.Resolution<List<Integer>> resolveHouses(TaxYearId year, RegimeCode regime,
            List<HouseProperty> houses, Money interest) {
        RulePack pack = pack(year);
        List<ComputationNode> nodes = new ArrayList<>(BUILT);
        nodes.add(new TotalIncomeNode());
        EngineInputs plain = NodeHarness.plainTaxpayer();
        OtherSourceItem item = new OtherSourceItem(OtherSourceKind.OTHER_INTEREST, interest, Money.ZERO, Money.ZERO,
                Money.ZERO, false);
        EngineInputs inputs = new EngineInputs(year, regime,
                TemporalContext.forTaxYear(year, year.startsOn().plusMonths(5)),
                new EnteredFacts(List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), List.of(), houses, List.of(item), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty()),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
        return ElectionResolver.resolve(new HouseElection(), NodeRegistry.of(nodes), pack, pack.regimes().get(regime),
                inputs);
    }

    /** A first return, but for the journeys earlier years claimed in each LTA block. */
    private static LedgerSnapshot ledger(SalaryGoldenCase.Salary salary) {
        LedgerSnapshot empty = LedgerSnapshot.empty();
        return new LedgerSnapshot(salary.leaveTravelLedger(), empty.deductionAllowedIn(), empty.gratuityAlreadyAllowed(),
                empty.leaveEncashmentAlreadyAllowed(),
                empty.housePropertyLossCarriedForward(), empty.educationLoanInterestFirstYear(), Map.of());
    }

    RulePack pack(TaxYearId year) {
        return packs.computeIfAbsent(year, y -> new ClasspathRulePackProvider(
                        "rulepacks", SHIPPED_YEARS, new JsonPackAssembler(LockedInstruments.of(y)))
                .load(y));
    }

    NodeRegistry registry() {
        return registryWith(new GivenTotalIncome(Money.ZERO));
    }

    /** The nodes every shipped regime's pipeline names that this registry lacks — the same for all. */
    List<NodeId> droppedFromShippedPipeline() {
        Set<NodeId> registered = registry().registered();
        Set<List<NodeId>> dropped = new LinkedHashSet<>();
        SHIPPED_YEARS.forEach(year -> pack(year).regimes().values().forEach(regime -> dropped.add(
                regime.pipeline().nodeRefs().stream().filter(node -> !registered.contains(node)).toList())));
        if (dropped.size() != 1) {
            throw new IllegalStateException("Shipped pipelines differ in what a T1 run leaves out: " + dropped);
        }
        return dropped.iterator().next();
    }

    private static NodeRegistry registryWith(GivenTotalIncome totalIncome) {
        List<ComputationNode> nodes = new ArrayList<>(BUILT);
        nodes.add(totalIncome);
        return NodeRegistry.of(nodes);
    }

    private static RegimeSpec enteredAtTotalIncome(RegimeSpec shipped, NodeRegistry registry) {
        List<NodeId> kept = shipped.pipeline().nodeRefs().stream()
                .filter(registry.registered()::contains)
                .toList();
        return new RegimeSpec(shipped.code(), shipped.displayName(), shipped.provision1961(),
                shipped.provision2025(), shipped.appliesTo(), shipped.rateTable(), shipped.disallowances(),
                shipped.eligibility(), shipped.election(), shipped.lifecycle(), shipped.isDefault(),
                new PipelineSpec(kept), shipped.behaviour(), shipped.provenance());
    }

    private static EngineInputs inputs(GoldenCase golden, TaxYearId year) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        return new EngineInputs(year, golden.regime(),
                TemporalContext.forTaxYear(year, year.startsOn().plusMonths(5)),
                plain.entered(), aged(plain, year, golden.ageAtYearEnd()), plain.family(), plain.ledgers(),
                plain.elections(), plain.capitalGains(), plain.filing());
    }

    /**
     * The harness's person, or one of the case's age — six months short of a birthday, so the completed age at the
     * year's end is exactly the case's.
     */
    private static Individual aged(EngineInputs plain, TaxYearId year, Optional<Integer> ageAtYearEnd) {
        Individual harness = (Individual) plain.assessee();
        return ageAtYearEnd
                .map(age -> new Individual(year.endsOn().minusYears(age).minusMonths(6), harness.residence(),
                        Optional.empty(), Optional.empty()))
                .orElse(harness);
    }
}
