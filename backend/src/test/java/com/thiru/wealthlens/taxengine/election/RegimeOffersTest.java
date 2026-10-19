package com.thiru.wealthlens.taxengine.election;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.Assessee;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.Huf;
import com.thiru.wealthlens.taxengine.input.RegimeElectionSnapshot;
import com.thiru.wealthlens.taxengine.rules.condition.AgeAtLeast;
import com.thiru.wealthlens.taxengine.rules.condition.AgeSubject;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.pack.ConditionSet;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.regime.AfterWithdrawal;
import com.thiru.wealthlens.taxengine.rules.regime.BindingSpec;
import com.thiru.wealthlens.taxengine.rules.regime.ConditionSetId;
import com.thiru.wealthlens.taxengine.rules.regime.ElectionProtocol;
import com.thiru.wealthlens.taxengine.rules.regime.ElectionState;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeLifecycle;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * The regimes offered (D-222) — on the shipped TY 2026-27 pack, or with one field of the old regime changed.
 */
class RegimeOffersTest {

    private static final RegimeCode NEW = new RegimeCode("NEW_TAX_REGIME");
    private static final RegimeCode OLD = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final RulePack PACK = new ClasspathRulePackProvider("rulepacks", Set.of(TaxYearId.of(2026)),
            new JsonPackAssembler(LockedInstruments.of(TaxYearId.of(2026)))).load(TaxYearId.of(2026));

    /** The pack, with the old regime changed and any eligibility sets added. */
    private static RulePack withOld(UnaryOperator<RegimeSpec> change, Map<ConditionSetId, ConditionSet> sets) {
        Map<RegimeCode, RegimeSpec> regimes = new HashMap<>(PACK.regimes());
        regimes.put(OLD, change.apply(PACK.regimes().get(OLD)));
        Map<ConditionSetId, ConditionSet> conditionSets = new HashMap<>(PACK.conditionSets());
        conditionSets.putAll(sets);
        return new RulePack(PACK.taxYear(), PACK.layers(), PACK.instruments(), PACK.rules(), PACK.ruleSets(), PACK.components(),
                regimes, PACK.disallowanceSets(), conditionSets);
    }

    private static RegimeSpec copy(RegimeSpec r, Set<ConditionSetId> eligibility, ElectionProtocol election,
            RegimeLifecycle lifecycle) {
        return new RegimeSpec(r.code(), r.displayName(), r.provision1961(), r.provision2025(), r.appliesTo(),
                r.rateTable(), r.disallowances(), eligibility, election, lifecycle, r.isDefault(), r.pipeline(),
                r.behaviour(), r.provenance());
    }

    private static RegimeLifecycle closedToNewEntrantsFrom2026(RegimeLifecycle l) {
        return new RegimeLifecycle(DateRange.between(LocalDate.of(2024, 4, 1), LocalDate.of(2026, 3, 31)),
                l.inForceWindow(), l.onSunset(), l.onConditionFailure(), l.provenance());
    }

    private static EngineInputs withElections(RegimeElectionSnapshot elections) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        return new EngineInputs(plain.taxYear(), plain.regime(), plain.temporal(), plain.entered(), plain.assessee(),
                plain.family(), plain.ledgers(), elections, plain.capitalGains(), plain.filing());
    }

    @Test
    void of_whenAnIndividualHasNoBusinessIncome_offersBothRegimesTheDefaultFirst() {
        // When
        List<RegimeOffer> offers = RegimeOffers.of(PACK, NodeHarness.plainTaxpayer());

        // Then
        assertThat(offers).allMatch(RegimeOffer.Available.class::isInstance);
        assertThat(offers).extracting(RegimeOffer::code).containsExactly(NEW, OLD);
    }

    @Test
    void of_whenTheAssesseeIsAHuf_barsBothNamingThePersonType() {
        // Given — both shipped regimes say INDIVIDUAL only
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs huf = new EngineInputs(plain.taxYear(), plain.regime(), plain.temporal(), plain.entered(),
                new Huf(Assessee.ResidentialStatus.RESIDENT), plain.family(), plain.ledgers(), plain.elections(),
                plain.capitalGains(), plain.filing());

        // When
        List<RegimeOffer> offers = RegimeOffers.of(PACK, huf);

        // Then
        assertThat(offers).allSatisfy(offer -> assertThat(offer).isInstanceOfSatisfying(RegimeOffer.Barred.class,
                barred -> assertThat(barred.reason()).contains("not to HUF")));
    }

    @Test
    void of_whenARegimeIsNotInForceInTheYear_barsIt() {
        // Given — the old regime in force only to 31-3-2026
        RulePack pack = withOld(r -> copy(r, r.eligibility(), r.election(), new RegimeLifecycle(
                DateRange.between(LocalDate.of(2024, 4, 1), LocalDate.of(2026, 3, 31)),
                DateRange.between(LocalDate.of(2024, 4, 1), LocalDate.of(2026, 3, 31)),
                r.lifecycle().onSunset(), r.lifecycle().onConditionFailure(), r.lifecycle().provenance())), Map.of());

        // When / Then
        assertThat(RegimeOffers.of(pack, NodeHarness.plainTaxpayer()).get(1))
                .isInstanceOfSatisfying(RegimeOffer.Barred.class,
                        barred -> assertThat(barred.reason()).contains("not in force in 2026-27"));
    }

    @Test
    void of_whenARegimeIsClosedToNewEntrantsAndTheAssesseeNeverElectedIt_barsIt() {
        // Given
        RulePack pack = withOld(r -> copy(r, r.eligibility(), r.election(),
                closedToNewEntrantsFrom2026(r.lifecycle())), Map.of());

        // When / Then
        assertThat(RegimeOffers.of(pack, NodeHarness.plainTaxpayer()).get(1))
                .isInstanceOfSatisfying(RegimeOffer.Barred.class,
                        barred -> assertThat(barred.reason()).contains("closed to new entrants"));
    }

    @Test
    void of_whenARegimeIsClosedToNewEntrantsButTheAssesseeIsInIt_offersIt() {
        // Given — grandfathered: entry closed, still in force, the election in effect
        RulePack pack = withOld(r -> copy(r, r.eligibility(), r.election(),
                closedToNewEntrantsFrom2026(r.lifecycle())), Map.of());
        EngineInputs inEffect = withElections(new RegimeElectionSnapshot(Map.of(OLD, ElectionState.IN_EFFECT), Map.of()));

        // When / Then
        assertThat(RegimeOffers.of(pack, inEffect).get(1)).isInstanceOf(RegimeOffer.Available.class);
    }

    @Test
    void of_whenAnEligibilitySetIsUnmet_barsItNamingTheCondition() {
        // Given — a set requiring age 60; the harness taxpayer is 41 at the year's end
        ConditionSetId id = new ConditionSetId("ELIG-SENIOR");
        ConditionSet senior = new ConditionSet(id, "a senior citizen", List.of(new AgeAtLeast(60, AgeSubject.SELF)),
                PACK.regimes().get(OLD).rateTable(), PACK.regimes().get(OLD).provenance());
        RulePack pack = withOld(r -> copy(r, Set.of(id), r.election(), r.lifecycle()), Map.of(id, senior));

        // When / Then
        assertThat(RegimeOffers.of(pack, NodeHarness.plainTaxpayer()).get(1))
                .isInstanceOfSatisfying(RegimeOffer.Barred.class,
                        barred -> assertThat(barred.reason()).contains("a senior citizen").contains("does not hold"));
    }

    @Test
    void of_whenARegimesBindingAppliesToTheAssessee_isRefusedNamingW082e() {
        // Given — the old regime's s.202(4)(a) binding, applying to everyone
        RulePack pack = withOld(r -> copy(r, r.eligibility(), new ElectionProtocol(r.election().kind(),
                r.election().vehicle(), r.election().form(), r.election().vehicleWhen(), r.election().deadline(),
                new BindingSpec(true, 1, new AfterWithdrawal.MayReElect(), false, List.of()),
                r.election().provenance()), r.lifecycle()), Map.of());

        // When / Then
        assertThatThrownBy(() -> RegimeOffers.of(pack, NodeHarness.plainTaxpayer()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("W-082e");
    }
}
