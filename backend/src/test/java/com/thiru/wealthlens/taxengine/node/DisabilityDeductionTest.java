package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.Assessee.ResidentialStatus;
import com.thiru.wealthlens.taxengine.input.DisabledDependantSupport;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.FamilySnapshot;
import com.thiru.wealthlens.taxengine.input.FamilySnapshot.Member;
import com.thiru.wealthlens.taxengine.input.FamilySnapshot.Relationship;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.input.SpecifiedDiseaseTreatment;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.condition.DisabilitySeverity;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * s.154, s.127 and s.128 through {@link ChapterEightNode}, TY 2026-27 (S2; D-138). Figures by hand from the sections'
 * limits.
 */
class DisabilityDeductionTest {

    private static final RegimeCode OLD = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final RegimeCode NEW = new RegimeCode("NEW_TAX_REGIME");
    private static final TaxYearId TY_2026 = TaxYearId.of(2026);

    /**
     * 0 a dependant parent with a disability, 66 and resident; 1 a dependant sibling with a severe disability, 40;
     * 2 a sibling not dependant; 3 a dependant parent, 56, no disability.
     */
    private static final FamilySnapshot FAMILY = new FamilySnapshot(List.of(
            member(Relationship.PARENT, LocalDate.of(1960, 5, 1), true, DisabilitySeverity.DISABILITY),
            member(Relationship.SIBLING, LocalDate.of(1986, 5, 1), true, DisabilitySeverity.SEVERE_DISABILITY),
            member(Relationship.SIBLING, LocalDate.of(1988, 5, 1), false, DisabilitySeverity.DISABILITY),
            new Member(Relationship.PARENT, LocalDate.of(1970, 5, 1), true, Optional.empty(), Optional.empty(),
                    false, true)));

    private static Member member(Relationship relationship, LocalDate born, boolean dependant,
            DisabilitySeverity severity) {
        return new Member(relationship, born, dependant, Optional.of(severity), Optional.empty(), false, true);
    }

    private static Individual assessee(ResidentialStatus residence, Optional<DisabilitySeverity> disability) {
        return new Individual(LocalDate.of(1985, 6, 15), residence, disability, Optional.empty());
    }

    private static NodeResult run(RegimeCode regime, Individual assessee, List<DisabledDependantSupport> supports,
            List<SpecifiedDiseaseTreatment> treatments) {
        return run(TY_2026, regime, assessee, supports, treatments);
    }

    private static NodeResult run(TaxYearId year, RegimeCode regime, Individual assessee,
            List<DisabledDependantSupport> supports, List<SpecifiedDiseaseTreatment> treatments) {
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(year), new JsonPackAssembler(LockedInstruments.of(year)))
                .load(year);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(year, regime,
                TemporalContext.forTaxYear(year, year.startsOn().plusMonths(5)),
                new EnteredFacts(List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(),
                        supports, treatments, List.of(), List.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                assessee, FAMILY, plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());
        return NodeHarness.with(pack).regime(regime).inputs(inputs)
                .given(NodeIds.GTI, Money.of("1000000"))
                .given(NodeIds.INPUTS_COMPONENTS, new ComponentSet(Map.of()))
                .given(NodeIds.SALARY_BASE, new SalaryBases(Map.of()))
                .run(new ChapterEightNode());
    }

    private static Individual plain() {
        return assessee(ResidentialStatus.RESIDENT, Optional.empty());
    }

    private static List<DisabledDependantSupport> support(int member, String spent) {
        return List.of(new DisabledDependantSupport(member, Money.of(spent), false));
    }

    private static SpecifiedDiseaseTreatment treated(int member, String paid, String reimbursed) {
        return new SpecifiedDiseaseTreatment(false, Optional.of(member), Money.of(paid), Money.of(reimbursed));
    }

    @Test
    void evaluate_whenTaxYear2025SupportIsLessThanTheSum_allowsSection80ddsFixedSum() {
        // When — 1961 s.80DD(1) (D-146): "a deduction of a sum of seventy-five thousand rupees" — 30,000 spent
        NodeResult result = run(TaxYearId.of(2025), OLD, plain(), support(0, "30000"), List.of());
        NodeResult severe = run(TaxYearId.of(2025), OLD, plain(), support(1, "30000"), List.of());

        // Then — 75,000 and 1,25,000, whatever was spent
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("75000")));
        assertThat(severe.value()).isEqualTo(MoneyValue.of(Money.of("125000")));
    }

    @Test
    void evaluate_whenNothingIsSpentOnADisabledDependant_allowsNothingInEitherYear() {
        // When — the deduction is for having incurred expenditure or paid or deposited (s.80DD(1); s.127(1))
        NodeResult earlier = run(TaxYearId.of(2025), OLD, plain(), support(0, "0"), List.of());
        NodeResult later = run(OLD, plain(), support(0, "0"), List.of());

        // Then
        assertThat(earlier.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(later.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(earlier.trace()).singleElement()
                .satisfies(line -> assertThat(line.limitedBy()).contains("nothing spent or deposited"));
    }

    @Test
    void evaluate_whenTaxYear2025TreatmentIsEntered_allowsTheLesserOfTheNetAndTheLimit() {
        // When — 1961 s.80DDB: the assessee's 60,000 less 10,000 → 40,000; the senior parent's 1,50,000 → 1,00,000
        NodeResult own = run(TaxYearId.of(2025), OLD, plain(), List.of(),
                List.of(new SpecifiedDiseaseTreatment(true, Optional.empty(), Money.of("60000"), Money.of("10000"))));
        NodeResult senior = run(TaxYearId.of(2025), OLD, plain(), List.of(), List.of(treated(0, "150000", "0")));

        // Then
        assertThat(own.value()).isEqualTo(MoneyValue.of(Money.of("40000")));
        assertThat(senior.value()).isEqualTo(MoneyValue.of(Money.of("100000")));
    }

    @Test
    void evaluate_whenTheAssesseeIsCertifiedInTaxYear2025_allowsSection80usFixedSums() {
        // When — 1961 s.80U(1): 75,000; severe, 1,25,000
        NodeResult result = run(TaxYearId.of(2025), OLD,
                assessee(ResidentialStatus.RESIDENT, Optional.of(DisabilitySeverity.DISABILITY)), List.of(), List.of());
        NodeResult severe = run(TaxYearId.of(2025), OLD,
                assessee(ResidentialStatus.RESIDENT, Optional.of(DisabilitySeverity.SEVERE_DISABILITY)), List.of(),
                List.of());

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("75000")));
        assertThat(severe.value()).isEqualTo(MoneyValue.of(Money.of("125000")));
    }

    @Test
    void evaluate_whenTheAssesseeIsCertifiedSevere_allowsOneLakhTwentyFiveThousand() {
        // When — s.154(1)
        NodeResult result = run(OLD, assessee(ResidentialStatus.RESIDENT, Optional.of(
                DisabilitySeverity.SEVERE_DISABILITY)), List.of(), List.of());

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("125000")));
        assertThat(result.trace()).extracting(TraceLine::rule).containsExactly(RuleIds.DED_DISABILITY_SEVERE);
    }

    @Test
    void evaluate_whenACertifiedAssesseeIsNotResident_allowsNothingAndSaysWhy() {
        // When — s.154(1): "being resident in India"
        NodeResult result = run(OLD, assessee(ResidentialStatus.NON_RESIDENT, Optional.of(
                DisabilitySeverity.DISABILITY)), List.of(), List.of());

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).singleElement().satisfies(line -> assertThat(line.limitedBy()).isPresent());
    }

    @Test
    void evaluate_whenLessIsSpentOnADisabledParentThanTheLimit_allowsWhatWasSpent() {
        // When — s.127(1), "up to ₹ 75000" as a ceiling (OQ-19)
        NodeResult result = run(OLD, plain(), support(0, "60000"), List.of());

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("60000")));
    }

    @Test
    void evaluate_whenMoreIsSpentOnADisabledParentThanTheLimit_allowsSeventyFiveThousand() {
        // When
        NodeResult result = run(OLD, plain(), support(0, "90000"), List.of());

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("75000")));
    }

    @Test
    void evaluate_whenTheDependantIsSeverelyDisabled_allowsUpToOneLakhTwentyFiveThousand() {
        // When — s.127(3): 90,000 within 1,25,000
        NodeResult result = run(OLD, plain(), support(1, "90000"), List.of());

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("90000")));
        assertThat(result.trace()).extracting(TraceLine::rule)
                .containsExactly(RuleIds.DED_DISABLED_DEPENDANT_SEVERE);
    }

    @Test
    void evaluate_whenTwoDisabledDependantsAreSupported_isRefused() {
        // When / Then — OQ-19
        assertThatThrownBy(() -> run(OLD, plain(), List.of(new DisabledDependantSupport(0, Money.of("10000"), false),
                        new DisabledDependantSupport(1, Money.of("10000"), false)), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OQ-19");
    }

    @Test
    void evaluate_whenTheDependantClaimsSection154_isRefused() {
        // When / Then — s.127(8)
        assertThatThrownBy(() -> run(OLD, plain(), List.of(new DisabledDependantSupport(0, Money.of("10000"), true)),
                        List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("s.127(8)");
    }

    @Test
    void evaluate_whenTheSiblingIsNotDependant_isRefused() {
        // When / Then — s.127(9)(b)
        assertThatThrownBy(() -> run(OLD, plain(), support(2, "10000"), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("s.127(9)(b)");
    }

    @Test
    void evaluate_whenTheAssesseesTreatmentWasPartlyReimbursed_allowsTheRestWithinFortyThousand() {
        // When — s.128(1), (3): 55,000 − 10,000 = 45,000 → 40,000
        NodeResult result = run(OLD, plain(), List.of(), List.of(new SpecifiedDiseaseTreatment(true, Optional.empty(),
                Money.of("55000"), Money.of("10000"))));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("40000")));
    }

    @Test
    void evaluate_whenTreatmentWithinTheLimitWasPartlyReimbursed_allowsOnlyWhatWasNotReimbursed() {
        // When — s.128(3): 30,000 − 10,000 = 20,000, within 40,000
        NodeResult result = run(OLD, plain(), List.of(), List.of(new SpecifiedDiseaseTreatment(true, Optional.empty(),
                Money.of("30000"), Money.of("10000"))));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("20000")));
    }

    @Test
    void evaluate_whenASeniorParentIsTreated_allowsUpToOneLakh() {
        // When — s.128(4): 1,20,000 → 1,00,000
        NodeResult result = run(OLD, plain(), List.of(), List.of(treated(0, "120000", "0")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("100000")));
        assertThat(result.trace()).extracting(TraceLine::rule).containsExactly(RuleIds.DED_SPECIFIED_DISEASE_SENIOR);
    }

    @Test
    void evaluate_whenASeniorAndANonSeniorAreTreated_isRefused() {
        // When / Then — OQ-20
        assertThatThrownBy(() -> run(OLD, plain(), List.of(), List.of(treated(0, "50000", "0"),
                        treated(3, "20000", "0"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OQ-20");
    }

    @Test
    void evaluate_whenClaimedInTheNewRegime_givesANilLine() {
        // When — s.202(2)(a)(xii) keeps none of these
        NodeResult result = run(NEW, plain(), support(0, "60000"), List.of());

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).singleElement()
                .satisfies(line -> assertThat(line.limitedBy()).hasValueSatisfying(reason -> assertThat(reason).startsWith("disallowed in the NEW_TAX_REGIME regime: ")
                        .endsWith("(" + RuleIds.REG_NEW_TAX_REGIME_NO_EXEMPTION + ")")));
    }
}
