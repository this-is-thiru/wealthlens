package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.FamilySnapshot;
import com.thiru.wealthlens.taxengine.input.FamilySnapshot.Member;
import com.thiru.wealthlens.taxengine.input.FamilySnapshot.Relationship;
import com.thiru.wealthlens.taxengine.input.HealthPayment;
import com.thiru.wealthlens.taxengine.input.HealthPaymentKind;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
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
 * s.126 through {@link ChapterEightNode}, TY 2026-27 (S2 s.126, s.2(100); D-136). Figures by hand from the section's
 * limits; the assessee (born 1985) is not a senior citizen.
 */
class HealthInsuranceDeductionTest {

    private static final RegimeCode OLD = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final RegimeCode NEW = new RegimeCode("NEW_TAX_REGIME");
    private static final TaxYearId TY_2026 = TaxYearId.of(2026);

    /** 0 father, 66 by 31-3-2027 and resident; 1 mother, 56; 2 a child not dependant; 3 father, 71, non-resident. */
    private static final FamilySnapshot FAMILY = new FamilySnapshot(List.of(
            member(Relationship.PARENT, LocalDate.of(1960, 5, 1), false, true),
            member(Relationship.PARENT, LocalDate.of(1970, 5, 1), false, true),
            member(Relationship.CHILD, LocalDate.of(2000, 5, 1), false, true),
            member(Relationship.PARENT, LocalDate.of(1955, 5, 1), false, false)));

    private static Member member(Relationship relationship, LocalDate born, boolean dependant, boolean resident) {
        return new Member(relationship, born, dependant, Optional.empty(), Optional.empty(), false, false, resident);
    }

    private static HealthPayment self(HealthPaymentKind kind, String amount) {
        return new HealthPayment(kind, true, List.of(), Money.of(amount), false, 1);
    }

    private static HealthPayment forMembers(HealthPaymentKind kind, String amount, Integer... members) {
        return new HealthPayment(kind, false, List.of(members), Money.of(amount), false, 1);
    }

    private static NodeResult run(RegimeCode regime, HealthPayment... payments) {
        return run(TY_2026, regime, payments);
    }

    private static NodeResult run(TaxYearId year, RegimeCode regime, HealthPayment... payments) {
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(year), new JsonPackAssembler(LockedInstruments.of(year)))
                .load(year);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(year, regime,
                TemporalContext.forTaxYear(year, year.startsOn().plusMonths(5)),
                new EnteredFacts(List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(),
                        List.of(payments), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), List.of()),
                plain.assessee(), FAMILY, plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());
        return NodeHarness.with(pack).regime(regime).inputs(inputs)
                .given(NodeIds.GTI, Money.of("1000000"))
                .given(NodeIds.INPUTS_COMPONENTS, new ComponentSet(Map.of()))
                .given(NodeIds.SALARY_BASE, new SalaryBases(Map.of()))
                .run(new ChapterEightNode());
    }

    @Test
    void evaluate_whenHealthPaymentsAreEnteredForTaxYear2025_refusesNamingTheItemThatAddsThem() {
        // When / Then — D-144: 80D's TY 2025-26 rows are W-075d2's
        assertThatThrownBy(() -> run(TaxYearId.of(2025), OLD, self(HealthPaymentKind.INSURANCE_PREMIUM, "20000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("W-075d2");
    }

    @Test
    void evaluate_whenTheAssesseesPremiumExceedsItsLimit_allowsTwentyFiveThousand() {
        // When — s.126(2)(a)
        NodeResult result = run(OLD, self(HealthPaymentKind.INSURANCE_PREMIUM, "30000"));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("25000")));
        assertThat(result.trace()).extracting(TraceLine::rule).containsExactly(RuleIds.DED_HEALTH_FAMILY);
    }

    @Test
    void evaluate_whenPremiumAndMedicalExpenditureTogetherExceedFifty_capsTheFamilyAtFiftyThousand() {
        // When — 25,000 (premium 30,000 capped) + 40,000 = 65,000; s.126(4): 50,000
        NodeResult result = run(OLD, self(HealthPaymentKind.INSURANCE_PREMIUM, "30000"),
                self(HealthPaymentKind.MEDICAL_EXPENDITURE, "40000"));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("50000")));
    }

    @Test
    void evaluate_whenASeniorResidentParentIsInsured_allowsUpToFiftyThousand() {
        // When — s.126(8)(a): 45,000 within 50,000
        NodeResult result = run(OLD, forMembers(HealthPaymentKind.INSURANCE_PREMIUM, "45000", 0));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("45000")));
        assertThat(result.trace()).extracting(TraceLine::rule).containsExactly(RuleIds.DED_HEALTH_PARENTS_SENIOR);
    }

    @Test
    void evaluate_whenTheOlderParentIsNotResident_isNotASeniorCitizen() {
        // When — s.2(100): resident in India; 71 but non-resident, so 25,000
        NodeResult result = run(OLD, forMembers(HealthPaymentKind.INSURANCE_PREMIUM, "40000", 3));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("25000")));
    }

    @Test
    void evaluate_whenAnInsuredSeniorParentsMedicalExpenditureIsClaimed_isRefused() {
        // When / Then — s.126(7)
        assertThatThrownBy(() -> run(OLD, forMembers(HealthPaymentKind.INSURANCE_PREMIUM, "45000", 0),
                        forMembers(HealthPaymentKind.MEDICAL_EXPENDITURE, "10000", 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("s.126(7)");
    }

    @Test
    void evaluate_whenCheckupsExceedFiveThousand_splitsTheLimitInTheRatioClaimed() {
        // When — 4,000 for the assessee, 4,000 for the mother; s.126(3): 5,000, 2,500 each
        NodeResult result = run(OLD, self(HealthPaymentKind.PREVENTIVE_CHECKUP, "4000"),
                forMembers(HealthPaymentKind.PREVENTIVE_CHECKUP, "4000", 1));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("5000")));
        assertThat(result.trace()).filteredOn(line -> line.rule().equals(RuleIds.DED_HEALTH_FAMILY))
                .singleElement().satisfies(line -> assertThat(line.out()).isEqualTo(MoneyValue.of(Money.of("2500"))));
    }

    @Test
    void evaluate_whenAThreeYearPremiumIsPaidAtOnce_countsAThird() {
        // When — s.126(8)(b), (10): 60,000 × 1/3
        NodeResult result = run(OLD,
                new HealthPayment(HealthPaymentKind.INSURANCE_PREMIUM, true, List.of(), Money.of("60000"), false, 3));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("20000")));
    }

    @Test
    void evaluate_whenAPremiumIsPaidInCash_isRefused() {
        // When / Then — s.126(9)
        assertThatThrownBy(() -> run(OLD,
                        new HealthPayment(HealthPaymentKind.INSURANCE_PREMIUM, true, List.of(), Money.of("10000"), true,
                                1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("s.126(9)");
    }

    @Test
    void evaluate_whenOnePremiumCoversASeniorAndANonSeniorParent_isRefused() {
        // When / Then — OQ-18
        assertThatThrownBy(() -> run(OLD, forMembers(HealthPaymentKind.INSURANCE_PREMIUM, "40000", 0, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OQ-18");
    }

    @Test
    void evaluate_whenAChildWhoIsNotADependantIsCovered_isRefused() {
        // When / Then — s.126(10)(b)
        assertThatThrownBy(() -> run(OLD, forMembers(HealthPaymentKind.INSURANCE_PREMIUM, "10000", 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dependant");
    }

    @Test
    void evaluate_whenPaidInTheNewRegime_givesANilLine() {
        // When — s.202(2)(a)(xii) keeps no part of s.126
        NodeResult result = run(NEW, self(HealthPaymentKind.INSURANCE_PREMIUM, "30000"));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).singleElement()
                .satisfies(line -> assertThat(line.limitedBy()).contains("not in the NEW_TAX_REGIME regime"));
    }
}
