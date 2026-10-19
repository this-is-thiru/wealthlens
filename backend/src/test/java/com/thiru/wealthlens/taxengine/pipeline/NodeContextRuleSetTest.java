package com.thiru.wealthlens.taxengine.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.Assessee.ResidentialStatus;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.pack.RegimeApplicability;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A node reads a rule set, and the pack's data says which row applies (W-088m1, D-252). */
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
        return new ClasspathRulePackProvider("rulepacks", Set.of(YEAR), new JsonPackAssembler(LockedInstruments.of(YEAR)))
                .load(YEAR);
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
