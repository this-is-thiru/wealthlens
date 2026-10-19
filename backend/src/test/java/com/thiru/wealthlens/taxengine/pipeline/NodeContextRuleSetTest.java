package com.thiru.wealthlens.taxengine.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.AccommodationProvider;
import com.thiru.wealthlens.taxengine.input.Assessee.ResidentialStatus;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.input.RetirementEntry;
import com.thiru.wealthlens.taxengine.input.TransferredAssetKind;
import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFactIs;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RegimeApplicability;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.RuleSelection;
import com.thiru.wealthlens.taxengine.rules.pack.RuleSet;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A node reads a rule set, and the pack's data says which row applies — for the person, or for an item (W-088m1,
 * W-088m2; D-252, D-253).
 */
class NodeContextRuleSetTest {

    private static final TaxYearId YEAR = TaxYearId.of(2026);
    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");
    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");

    /** Declares sets and computes nothing: the context is what is under test. */
    private record Reading(Set<RuleSetId> ruleSets) implements ComputationNode {

        @Override
        public NodeId produces() {
            return new NodeId("test.reading");
        }

        @Override
        public Set<NodeId> requires() {
            return Set.of();
        }

        @Override
        public NodeResult evaluate(NodeContext ctx) {
            throw new UnsupportedOperationException("never evaluated");
        }
    }

    private static RulePack shipped() {
        return shipped(YEAR);
    }

    private static RulePack shipped(TaxYearId year) {
        return new ClasspathRulePackProvider("rulepacks", Set.of(year), new JsonPackAssembler(LockedInstruments.of(year)))
                .load(year);
    }

    /** The plain taxpayer, in the given tax year. */
    private static EngineInputs plainIn(TaxYearId year) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        LocalDate asOf = LocalDate.of(year.startYear(), 9, 21);
        return new EngineInputs(year, plain.regime(), TemporalContext.forTaxYear(year, asOf), plain.entered(),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
    }

    private static ItemFacts item(String fact, Enum<?> code) {
        return ItemFacts.of(new ItemFactId(fact), code.name());
    }

    /** A car that is not electric, of the given capacity — the facts MotorCarPerquisite supplies (D-257). */
    private static ItemFacts carOf(long cubicCapacityCc) {
        return new ItemFacts(Map.of(new ItemFactId("vehicle.engine.electric"), "false"),
                Map.of(new ItemFactId("vehicle.engine.cc"), cubicCapacityCc));
    }

    private static ItemFacts electricCar() {
        return new ItemFacts(Map.of(new ItemFactId("vehicle.engine.electric"), "true"), Map.of());
    }

    private static NodeContext reading(RulePack pack, RegimeCode regime, EngineInputs inputs, RuleSetId... sets) {
        return NodeContext.forRun(pack, pack.regimes().get(regime), inputs).forNode(new Reading(Set.of(sets)));
    }

    private static EngineInputs person(LocalDate dateOfBirth, ResidentialStatus residence) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        return new EngineInputs(plain.taxYear(), plain.regime(), plain.temporal(), plain.entered(),
                new Individual(dateOfBirth, residence, Optional.empty(), Optional.empty()),
                plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());
    }

    @Test
    void ruleOf_whenTheSetHasARowPerRegime_isTheRegimesRow() {
        // Given
        RulePack pack = shipped();

        // When
        RuleId inNew = reading(pack, NEW_TAX_REGIME, NodeHarness.plainTaxpayer(), RuleSetIds.RS_SAL_STANDARD_DEDUCTION)
                .ruleIdOf(RuleSetIds.RS_SAL_STANDARD_DEDUCTION);
        RuleId inOld = reading(pack, FIRST_SCHEDULE_RATES, NodeHarness.plainTaxpayer(),
                RuleSetIds.RS_SAL_STANDARD_DEDUCTION).ruleIdOf(RuleSetIds.RS_SAL_STANDARD_DEDUCTION);

        // Then
        assertThat(inNew).isEqualTo(RuleIds.SAL_STANDARD_DEDUCTION_NEW_TAX_REGIME);
        assertThat(inOld).isEqualTo(RuleIds.SAL_STANDARD_DEDUCTION_ANY_OTHER_CASE);
    }

    @Test
    void ruleOf_whenTheSetChoosesByConditions_isTheLadderThePersonsFactsSelect() {
        // Given — seventy at the year's end: resident, then not (the age ladders are for residents)
        RulePack pack = shipped();
        LocalDate born = LocalDate.of(1956, 6, 1);

        // When
        RuleId resident = reading(pack, FIRST_SCHEDULE_RATES, person(born, ResidentialStatus.RESIDENT),
                RuleSetIds.RS_RATE_SLAB).ruleIdOf(RuleSetIds.RS_RATE_SLAB);
        RuleId nonResident = reading(pack, FIRST_SCHEDULE_RATES, person(born, ResidentialStatus.NON_RESIDENT),
                RuleSetIds.RS_RATE_SLAB).ruleIdOf(RuleSetIds.RS_RATE_SLAB);

        // Then
        assertThat(resident).isEqualTo(RuleIds.RATE_SLAB_FIRST_SCHEDULE_AGE_60TO79);
        assertThat(nonResident).isEqualTo(RuleIds.RATE_SLAB_FIRST_SCHEDULE);
    }

    @Test
    void ruleOf_whenTwoRowsApply_isRefusedNamingBoth() {
        // Given — the other regimes' standard deduction rewritten to apply in every regime, so two do in the new one
        RulePack shipped = shipped();
        RuleRow other = shipped.rule(RuleIds.SAL_STANDARD_DEDUCTION_ANY_OTHER_CASE);
        List<RuleVersion> everywhere = other.versions().stream()
                .map(v -> new RuleVersion(v.validity(), new RegimeApplicability.AllRegimes(), v.cap(), v.relief(),
                        v.rounding(), v.definition(), v.conditions(), v.provision1961(), v.provision2025(),
                        v.introducedBy(), v.legalStatus(), v.publicationStatus(), v.provenance()))
                .toList();
        Map<RuleId, RuleRow> rules = new HashMap<>(shipped.rules());
        rules.put(other.id(), new RuleRow(other.id(), other.axis(), everywhere, other.midYearCombination(),
                other.midYearCombinationRule(), other.prorationBasis(), other.headProvenance()));
        RulePack overlapping = new RulePack(shipped.taxYear(), shipped.layers(), shipped.instruments(), rules,
                shipped.ruleSets(), shipped.components(), shipped.regimes(), shipped.disallowanceSets(),
                shipped.conditionSets());
        NodeContext ctx = reading(overlapping, NEW_TAX_REGIME, NodeHarness.plainTaxpayer(),
                RuleSetIds.RS_SAL_STANDARD_DEDUCTION);

        // When / Then
        assertThatThrownBy(() -> ctx.ruleOf(RuleSetIds.RS_SAL_STANDARD_DEDUCTION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2 of its rows")
                .hasMessageContaining(RuleIds.SAL_STANDARD_DEDUCTION_NEW_TAX_REGIME.value())
                .hasMessageContaining(RuleIds.SAL_STANDARD_DEDUCTION_ANY_OTHER_CASE.value());
    }

    @Test
    void ruleOf_whenTheSetTakesAtMostOneRow_isRefusedAsAMismatch() {
        // Given
        NodeContext ctx = reading(shipped(), NEW_TAX_REGIME, NodeHarness.plainTaxpayer(),
                RuleSetIds.RS_SAL_ENTMT_ALLOWANCE);

        // When / Then — the pack and the node must agree on how many rows the set may give
        assertThatThrownBy(() -> ctx.ruleOf(RuleSetIds.RS_SAL_ENTMT_ALLOWANCE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("takes AT_MOST_ONE");
    }

    @Test
    void optionalRuleOf_whenTheYearHoldsNoRow_isEmpty() {
        // Given — the entertainment allowance is a 1961 Act deduction only
        NodeContext ctx = reading(shipped(), FIRST_SCHEDULE_RATES, NodeHarness.plainTaxpayer(),
                RuleSetIds.RS_SAL_ENTMT_ALLOWANCE);

        // When / Then
        assertThat(ctx.optionalRuleOf(RuleSetIds.RS_SAL_ENTMT_ALLOWANCE)).isEmpty();
    }

    @Test
    void ruleOf_whenTheNodeDidNotDeclareTheSet_isRefused() {
        // Given
        NodeContext ctx = reading(shipped(), NEW_TAX_REGIME, NodeHarness.plainTaxpayer(), RuleSetIds.RS_REBATE);

        // When / Then — reachability (I3) is computed from the declared sets
        assertThatThrownBy(() -> ctx.ruleOf(RuleSetIds.RS_SAL_STANDARD_DEDUCTION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not declare rule set RS-SAL-STANDARD-DEDUCTION");
    }

    @Test
    void ruleOf_whenTwoRowsAreForTheSameItem_isRefusedNamingBoth() {
        // Given — the hotel's row rewritten to be for employer-owned accommodation too
        RulePack shipped = shipped();
        RuleRow hotel = shipped.rule(RuleIds.PERQ_ACCOM_HOTEL);
        Condition owned = new ItemFactIs(new ItemFactId("accommodation.provider"), "EMPLOYER_OWNED");
        List<RuleVersion> forOwned = hotel.versions().stream()
                .map(v -> new RuleVersion(v.validity(), v.regimes(), v.cap(), v.relief(), v.rounding(), v.definition(),
                        List.of(owned), v.provision1961(), v.provision2025(), v.introducedBy(), v.legalStatus(),
                        v.publicationStatus(), v.provenance()))
                .toList();
        Map<RuleId, RuleRow> rules = new HashMap<>(shipped.rules());
        rules.put(hotel.id(), new RuleRow(hotel.id(), hotel.axis(), forOwned, hotel.midYearCombination(),
                hotel.midYearCombinationRule(), hotel.prorationBasis(), hotel.headProvenance()));
        RulePack overlapping = new RulePack(shipped.taxYear(), shipped.layers(), shipped.instruments(), rules,
                shipped.ruleSets(), shipped.components(), shipped.regimes(), shipped.disallowanceSets(),
                shipped.conditionSets());
        NodeContext ctx = reading(overlapping, NEW_TAX_REGIME, NodeHarness.plainTaxpayer(), RuleSetIds.RS_PERQ_ACCOM);

        // When / Then
        assertThatThrownBy(() -> ctx.ruleOf(RuleSetIds.RS_PERQ_ACCOM,
                        item("accommodation.provider", AccommodationProvider.EMPLOYER_OWNED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2 of its rows")
                .hasMessageContaining(RuleIds.PERQ_ACCOM_OWNED.value())
                .hasMessageContaining(RuleIds.PERQ_ACCOM_HOTEL.value())
                .hasMessageContaining("EMPLOYER_OWNED");
    }

    @Test
    void optionalRuleOf_whenNoRowIsForTheItem_isEmpty() {
        // Given — a short-term gain other than on listed equity is charged at slab rates, by no special-rate row
        NodeContext ctx = reading(shipped(), NEW_TAX_REGIME, NodeHarness.plainTaxpayer(), RuleSetIds.RS_RATE_CG_SPECIAL);

        // When / Then
        assertThat(ctx.optionalRuleOf(RuleSetIds.RS_RATE_CG_SPECIAL,
                item("capitalgains.part", CapitalGainsPart.SHORT_TERM_SLAB))).isEmpty();
    }

    @Test
    void optionalRuleOf_whenTheSetsOnlyRowIsForAnotherItem_isEmptyNotThatRow() {
        // Given — Sl. 1(c)(i) cut down to its small-car row: a lone row is still tested against the item
        RulePack shipped = shipped();
        Map<RuleSetId, RuleSet> sets = new HashMap<>(shipped.ruleSets());
        RuleSet partly = sets.get(RuleSetIds.RS_PERQ_CAR_PARTLY);
        sets.put(partly.id(), new RuleSet(partly.id(), partly.selection(), partly.ground(),
                List.of(RuleIds.PERQ_CAR_PARTLY_SMALL)));
        RulePack small = new RulePack(shipped.taxYear(), shipped.layers(), shipped.instruments(), shipped.rules(),
                sets, shipped.components(), shipped.regimes(), shipped.disallowanceSets(), shipped.conditionSets());
        NodeContext ctx = reading(small, NEW_TAX_REGIME, NodeHarness.plainTaxpayer(), RuleSetIds.RS_PERQ_CAR_PARTLY);

        // When / Then
        assertThat(ctx.optionalRuleOf(RuleSetIds.RS_PERQ_CAR_PARTLY, electricCar())).isEmpty();
        assertThat(ctx.optionalRuleOf(RuleSetIds.RS_PERQ_CAR_PARTLY, carOf(1500)))
                .map(RuleRow::id).contains(RuleIds.PERQ_CAR_PARTLY_SMALL);
    }

    /**
     * Every value of each item fact the nodes supply, in both shipped years, chooses the row the Java maps chose before
     * W-088m2 — the expected rows copied by hand from those maps (MotorCarPerquisite PARTLY / OWN_RUNNING,
     * PerquisiteNode RULE_FOR_PROVIDER, RetirementNode RULE_FOR_ENTRY, NotifiedRetirementLimits AGGREGATE_FOR_ENTRY,
     * AssetPerquisite WEAR_AND_TEAR, TaxByBasisNode SPECIAL_RATES, ChapterEightNode employerRow).
     */
    @Nested
    class ItemPartitions {

        private static final List<TaxYearId> YEARS = List.of(TaxYearId.of(2025), TaxYearId.of(2026));

        private static Optional<RuleId> rowFor(TaxYearId year, RegimeCode regime, RuleSetId set, ItemFacts item) {
            RulePack pack = shipped(year);
            NodeContext ctx = reading(pack, regime, plainIn(year), set);
            return pack.ruleSet(set).selection() == RuleSelection.EXACTLY_ONE
                    ? Optional.of(ctx.ruleIdOf(set, item))
                    : ctx.optionalRuleOf(set, item).map(RuleRow::id);
        }

        private static void assertChooses(RuleSetId set, String fact, Map<? extends Enum<?>, Optional<RuleId>> expected,
                Enum<?>[] every) {
            assertThat(Set.<Enum<?>>copyOf(expected.keySet())).isEqualTo(Set.of(every));
            for (TaxYearId year : YEARS) {
                expected.forEach((code, row) -> assertThat(rowFor(year, NEW_TAX_REGIME, set, item(fact, code)))
                        .as(year.label() + " " + code).isEqualTo(row));
            }
        }

        @Test
        void accommodation_eachProvider_choosesItsTableIRow() {
            assertChooses(RuleSetIds.RS_PERQ_ACCOM, "accommodation.provider", Map.of(
                    AccommodationProvider.GOVERNMENT, Optional.of(RuleIds.PERQ_ACCOM_GOVERNMENT),
                    AccommodationProvider.EMPLOYER_OWNED, Optional.of(RuleIds.PERQ_ACCOM_OWNED),
                    AccommodationProvider.EMPLOYER_LEASED, Optional.of(RuleIds.PERQ_ACCOM_LEASED),
                    AccommodationProvider.HOTEL, Optional.of(RuleIds.PERQ_ACCOM_HOTEL)),
                    AccommodationProvider.values());
        }

        @Test
        void retirement_eachEntry_choosesItsRow() {
            Map<RetirementEntry, Optional<RuleId>> expected = new EnumMap<>(RetirementEntry.class);
            expected.put(RetirementEntry.GOVERNMENT_GRATUITY, Optional.of(RuleIds.RET_GRATUITY_GOVERNMENT));
            expected.put(RetirementEntry.DEFENCE_RETIRING_GRATUITY, Optional.of(RuleIds.RET_GRATUITY_GOVERNMENT));
            expected.put(RetirementEntry.GOVERNMENT_COMMUTATION, Optional.of(RuleIds.RET_COMMUTED_PENSION_GOVERNMENT));
            expected.put(RetirementEntry.OTHER_EMPLOYER_COMMUTATION, Optional.of(RuleIds.RET_COMMUTED_PENSION_OTHER));
            expected.put(RetirementEntry.FUND_COMMUTATION, Optional.of(RuleIds.RET_COMMUTED_PENSION_FUND));
            expected.put(RetirementEntry.APPROVED_SCHEME_RETRENCHMENT,
                    Optional.of(RuleIds.RET_RETRENCHMENT_APPROVED_SCHEME));
            expected.put(RetirementEntry.VOLUNTARY_RETIREMENT, Optional.of(RuleIds.RET_VOLUNTARY_RETIREMENT));
            expected.put(RetirementEntry.GOVERNMENT_LEAVE_ENCASHMENT,
                    Optional.of(RuleIds.RET_LEAVE_ENCASHMENT_GOVERNMENT));
            expected.put(RetirementEntry.OTHER_GRATUITY, Optional.of(RuleIds.RET_GRATUITY_OTHER));
            expected.put(RetirementEntry.OTHER_LEAVE_ENCASHMENT, Optional.of(RuleIds.RET_LEAVE_ENCASHMENT_OTHER));
            expected.put(RetirementEntry.STATUTORY_GRATUITY, Optional.of(RuleIds.RET_GRATUITY_STATUTORY));
            expected.put(RetirementEntry.RETRENCHMENT, Optional.of(RuleIds.RET_RETRENCHMENT));
            assertChooses(RuleSetIds.RS_RET_ENTRY, "retirement.entry", expected, RetirementEntry.values());
        }

        @Test
        void retirementAggregate_onlyTheNotifiedEntries_haveARow() {
            Map<RetirementEntry, Optional<RuleId>> expected = new EnumMap<>(RetirementEntry.class);
            for (RetirementEntry entry : RetirementEntry.values()) {
                expected.put(entry, Optional.empty());
            }
            expected.put(RetirementEntry.OTHER_GRATUITY, Optional.of(RuleIds.RET_GRATUITY_AGGREGATE));
            expected.put(RetirementEntry.OTHER_LEAVE_ENCASHMENT, Optional.of(RuleIds.RET_LEAVE_ENCASHMENT_AGGREGATE));
            assertChooses(RuleSetIds.RS_RET_AGGREGATE, "retirement.entry", expected, RetirementEntry.values());
        }

        @Test
        void assetTransfer_eachKind_choosesItsWearAndTearRow() {
            assertChooses(RuleSetIds.RS_PERQ_ASSET_TRANSFER, "asset.kind", Map.of(
                    TransferredAssetKind.COMPUTER_OR_ELECTRONIC, Optional.of(RuleIds.PERQ_ASSET_TRANSFER_ELECTRONIC),
                    TransferredAssetKind.MOTOR_CAR, Optional.of(RuleIds.PERQ_ASSET_TRANSFER_CAR),
                    TransferredAssetKind.OTHER, Optional.of(RuleIds.PERQ_ASSET_TRANSFER_OTHER)),
                    TransferredAssetKind.values());
        }

        @Test
        void specialRates_onlyTheThreeSpecialParts_haveARow() {
            assertChooses(RuleSetIds.RS_RATE_CG_SPECIAL, "capitalgains.part", Map.of(
                    CapitalGainsPart.SHORT_TERM_SLAB, Optional.empty(),
                    CapitalGainsPart.SHORT_TERM_LISTED_EQUITY, Optional.of(RuleIds.RATE_CG_SHORT_LISTED_EQUITY),
                    CapitalGainsPart.LONG_TERM_OTHER, Optional.of(RuleIds.RATE_CG_LONG_OTHER),
                    CapitalGainsPart.LONG_TERM_LISTED_EQUITY, Optional.of(RuleIds.RATE_CG_LONG_LISTED_EQUITY)),
                    CapitalGainsPart.values());
        }

        @Test
        void carColumns_eachEngine_choosesItsRow_electricOnlyWhereTheYearsTableNamesIt() {
            // Given — TY 2025-26's Table II has no electric column; TY 2026-27's does (D-113 C5a)
            TaxYearId earlier = TaxYearId.of(2025);
            TaxYearId later = TaxYearId.of(2026);
            ItemFacts small = carOf(1500);
            ItemFacts large = carOf(2000);
            ItemFacts electric = electricCar();

            // When / Then
            for (TaxYearId year : YEARS) {
                assertThat(rowFor(year, NEW_TAX_REGIME, RuleSetIds.RS_PERQ_CAR_PARTLY, small))
                        .contains(RuleIds.PERQ_CAR_PARTLY_SMALL);
                assertThat(rowFor(year, NEW_TAX_REGIME, RuleSetIds.RS_PERQ_CAR_PARTLY, large))
                        .contains(RuleIds.PERQ_CAR_PARTLY_LARGE);
                assertThat(rowFor(year, NEW_TAX_REGIME, RuleSetIds.RS_PERQ_CAR_OWNRUN, small))
                        .contains(RuleIds.PERQ_CAR_OWNRUN_SMALL);
                assertThat(rowFor(year, NEW_TAX_REGIME, RuleSetIds.RS_PERQ_CAR_OWNRUN, large))
                        .contains(RuleIds.PERQ_CAR_OWNRUN_LARGE);
            }
            assertThat(rowFor(earlier, NEW_TAX_REGIME, RuleSetIds.RS_PERQ_CAR_PARTLY, electric)).isEmpty();
            assertThat(rowFor(earlier, NEW_TAX_REGIME, RuleSetIds.RS_PERQ_CAR_OWNRUN, electric)).isEmpty();
            assertThat(rowFor(later, NEW_TAX_REGIME, RuleSetIds.RS_PERQ_CAR_PARTLY, electric))
                    .contains(RuleIds.PERQ_CAR_PARTLY_ELECTRIC);
            assertThat(rowFor(later, NEW_TAX_REGIME, RuleSetIds.RS_PERQ_CAR_OWNRUN, electric))
                    .contains(RuleIds.PERQ_CAR_OWNRUN_ELECTRIC);
        }

        @Test
        void employerPension_aGovernmentEmployerInEveryRegime_anyOtherByTheRegime() {
            for (TaxYearId year : YEARS) {
                for (RegimeCode regime : List.of(NEW_TAX_REGIME, FIRST_SCHEDULE_RATES)) {
                    assertThat(rowFor(year, regime, RuleSetIds.RS_DED_NPS_EMPLOYER,
                            item("employer.kind", EmployerKind.GOVERNMENT))).contains(RuleIds.DED_NPS_EMPLOYER_GOVT);
                }
                assertThat(rowFor(year, NEW_TAX_REGIME, RuleSetIds.RS_DED_NPS_EMPLOYER,
                        item("employer.kind", EmployerKind.OTHER))).contains(RuleIds.DED_NPS_EMPLOYER_NEW_TAX_REGIME);
                assertThat(rowFor(year, FIRST_SCHEDULE_RATES, RuleSetIds.RS_DED_NPS_EMPLOYER,
                        item("employer.kind", EmployerKind.OTHER))).contains(RuleIds.DED_NPS_EMPLOYER_OTHER);
            }
        }
    }

    @Test
    void traceLine_whenANodeWritesOne_isUnderItsNodeWithTheGoverningVersionsProvenance() {
        // Given
        RulePack pack = shipped();
        EngineInputs inputs = NodeHarness.plainTaxpayer();
        NodeContext ctx = reading(pack, NEW_TAX_REGIME, inputs, RuleSetIds.RS_REBATE);

        // When
        TraceLine line = ctx.traceLine(3, RuleIds.REBATE_NEW_TAX_REGIME, StepKind.RELIEF, Money.of("100"),
                Money.of("40"));

        // Then — the provenance as the run's governing version gives it (D-261)
        assertThat(line.sequence()).isEqualTo(3);
        assertThat(line.node()).isEqualTo(new NodeId("test.reading"));
        assertThat(line.rule()).isEqualTo(RuleIds.REBATE_NEW_TAX_REGIME);
        assertThat(line.kind()).isEqualTo(StepKind.RELIEF);
        assertThat(line.in()).isEqualTo(MoneyValue.of(Money.of("100")));
        assertThat(line.out()).isEqualTo(MoneyValue.of(Money.of("40")));
        assertThat(line.limitedBy()).isEmpty();
        assertThat(line.provenance()).isEqualTo(TemporallyApportionedCap.governingVersion(
                pack.rule(RuleIds.REBATE_NEW_TAX_REGIME), inputs.temporal()).provenance());
    }

    @Test
    void traceLine_whenTheContextIsTheRunsOwn_isRefused() {
        // Given
        RulePack pack = shipped();
        NodeContext run = NodeContext.forRun(pack, pack.regimes().get(NEW_TAX_REGIME), NodeHarness.plainTaxpayer());

        // When / Then — it serves no node, so no line can be under one
        assertThatThrownBy(() -> run.traceLine(1, RuleIds.REBATE_NEW_TAX_REGIME, StepKind.RELIEF, Money.ZERO,
                        Money.ZERO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("serves no node");
    }

    @Test
    void traceLine_whenTheNodeDeclaredNoSetHoldingTheRule_isRefused() {
        // Given
        NodeContext ctx = reading(shipped(), NEW_TAX_REGIME, NodeHarness.plainTaxpayer(), RuleSetIds.RS_REBATE);

        // When / Then — reachability (I3) is computed from the declared sets
        assertThatThrownBy(() -> ctx.traceLine(1, RuleIds.SAL_STANDARD_DEDUCTION_NEW_TAX_REGIME, StepKind.DEDUCTION,
                        Money.ZERO, Money.ZERO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("declared no rule set holding");
    }

    @Test
    void rulesOf_whenTheSetTakesEveryRow_keepsItsStatedOrder() {
        // Given
        NodeContext ctx = reading(shipped(), NEW_TAX_REGIME, NodeHarness.plainTaxpayer(), RuleSetIds.RS_REBATE);

        // When
        List<RuleId> rows = ctx.rulesOf(RuleSetIds.RS_REBATE).stream().map(RuleRow::id).toList();

        // Then
        assertThat(rows).containsExactly(RuleIds.REBATE_NEW_TAX_REGIME, RuleIds.REBATE_NEW_TAX_REGIME_EXCESS,
                RuleIds.REBATE_RESIDENT_INDIVIDUAL);
    }
}
