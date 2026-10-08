package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.AgniveerCorpusFund;
import com.thiru.wealthlens.taxengine.input.Assessee;
import com.thiru.wealthlens.taxengine.input.Assessee.ResidentialStatus;
import com.thiru.wealthlens.taxengine.input.Donation;
import com.thiru.wealthlens.taxengine.input.DonationClause;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.Huf;
import com.thiru.wealthlens.taxengine.input.RentDeclaration;
import com.thiru.wealthlens.taxengine.input.Tenancy;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * s.133 through {@link ChapterEightNode}, TY 2026-27, and its order against s.134 (S2; D-142). Figures by hand: with
 * gross total income 10,00,000 and nothing else deducted, adjusted gross total income is 10,00,000 and the qualifying
 * limit 1,00,000.
 */
class DonationDeductionTest {

    private static final RegimeCode OLD = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final RegimeCode NEW = new RegimeCode("NEW_TAX_REGIME");
    private static final TaxYearId TY_2026 = TaxYearId.of(2026);

    private record Case(RegimeCode regime, String grossTotal, String agniveer, List<Donation> donations,
            Optional<String> rent, Optional<Assessee> assessee, TaxYearId year) {

        static Case of(Donation... donations) {
            return new Case(OLD, "1000000", "0", List.of(donations), Optional.empty(), Optional.empty(), TY_2026);
        }

        Case regime(RegimeCode to) {
            return new Case(to, grossTotal, agniveer, donations, rent, assessee, year);
        }

        /** Gross total income 6,00,000 less an Agniveer subscription of 1,50,000, and rent for twelve months. */
        Case withRent(String rentPaid) {
            return new Case(regime, "600000", "150000", donations, Optional.of(rentPaid), assessee, year);
        }

        Case assessee(Assessee to) {
            return new Case(regime, grossTotal, agniveer, donations, rent, Optional.of(to), year);
        }

        Case year(TaxYearId to) {
            return new Case(regime, grossTotal, agniveer, donations, rent, assessee, to);
        }
    }

    private static Donation donation(DonationClause clause, String amount) {
        return new Donation(clause, Money.of(amount), false, true);
    }

    private static NodeResult run(Case c) {
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(c.year()), new JsonPackAssembler(LockedInstruments.of(c.year())))
                .load(c.year());
        EngineInputs plain = NodeHarness.plainTaxpayer();
        Optional<AgniveerCorpusFund> agniveer = Money.of(c.agniveer()).isGreaterThan(Money.ZERO)
                ? Optional.of(new AgniveerCorpusFund(Money.of(c.agniveer()), Money.ZERO))
                : Optional.empty();
        DateRange wholeYear = DateRange.between(c.year().startsOn(), c.year().endsOn());
        List<Tenancy> tenancies = c.rent().map(rent -> List.of(new Tenancy(wholeYear, "ANY_OTHER_PLACE", false,
                Money.of(rent)))).orElse(List.of());
        Optional<RentDeclaration> declaration = c.rent().map(rent -> new RentDeclaration(12, false, false));
        EngineInputs inputs = new EngineInputs(c.year(), c.regime(),
                TemporalContext.forTaxYear(c.year(), c.year().startsOn().plusMonths(5)),
                new EnteredFacts(List.of(), tenancies, List.of(), List.of(), Optional.empty(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), agniveer, declaration, c.donations()),
                c.assessee().orElse(plain.assessee()), plain.family(), plain.ledgers(), plain.elections(),
                plain.capitalGains(), plain.filing());
        return NodeHarness.with(pack).regime(c.regime()).inputs(inputs)
                .given(NodeIds.GTI, Money.of(c.grossTotal()))
                .given(NodeIds.INPUTS_COMPONENTS, new ComponentSet(Map.of()))
                .given(NodeIds.SALARY_BASE, new SalaryBases(Map.of()))
                .run(new ChapterEightNode());
    }

    private static List<TraceLine> lines(NodeResult result, RuleId rule) {
        return result.trace().stream().filter(line -> line.rule().equals(rule)).toList();
    }

    private static MoneyValue rupees(String amount) {
        return MoneyValue.of(Money.of(amount));
    }

    @Test
    void evaluate_whenANamedFundIsGiven_deductsTheWhole() {
        // When — s.133(1)(a): PM CARES 50,000
        NodeResult result = run(Case.of(donation(DonationClause.NAMED_FUND, "50000")));

        // Then
        assertThat(result.value()).isEqualTo(rupees("50000"));
    }

    @Test
    void evaluate_whenACharitableInstitutionExceedsTheLimit_countsTheLimitThenHalvesIt() {
        // When — s.133(2): 2,40,000 counts up to 1,00,000; half is 50,000
        NodeResult result = run(Case.of(donation(DonationClause.CHARITABLE_INSTITUTION, "240000")));

        // Then
        assertThat(result.value()).isEqualTo(rupees("50000"));
        assertThat(lines(result, RuleIds.DED_DONATION_QUALIFYING)).singleElement().satisfies(line -> {
            assertThat(line.kind()).isEqualTo(StepKind.MEASURE);
            assertThat(line.out()).isEqualTo(rupees("100000"));
        });
    }

    @Test
    void evaluate_whenTheDroughtReliefFundIsGiven_deductsHalfWithoutLimit() {
        // When — s.133(1)(b)(i): 20,000 at half
        NodeResult result = run(Case.of(donation(DonationClause.DROUGHT_RELIEF_FUND, "20000")));

        // Then
        assertThat(result.value()).isEqualTo(rupees("10000"));
        assertThat(lines(result, RuleIds.DED_DONATION_QUALIFYING)).isEmpty();
    }

    @Test
    void evaluate_whenCashIsOverTwoThousand_allowsNothingForThatDonation() {
        // When — s.133(5): 2,500 in cash stopped, 2,000 in cash allowed
        NodeResult result = run(Case.of(new Donation(DonationClause.NAMED_FUND, Money.of("2500"), true, true),
                new Donation(DonationClause.NAMED_FUND, Money.of("2000"), true, true)));

        // Then
        assertThat(result.value()).isEqualTo(rupees("2000"));
        assertThat(lines(result, RuleIds.DED_DONATION_CASH)).singleElement().satisfies(line -> {
            assertThat(line.in()).isEqualTo(rupees("2500"));
            assertThat(line.out()).isEqualTo(MoneyValue.of(Money.ZERO));
        });
    }

    @Test
    void evaluate_whenACharitableInstitutionHasNotReported_allowsNothing() {
        // When — s.133(6)
        NodeResult result = run(Case.of(new Donation(DonationClause.CHARITABLE_INSTITUTION, Money.of("50000"),
                false, false)));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(lines(result, RuleIds.DED_DONATION_HALF)).singleElement()
                .satisfies(line -> assertThat(line.limitedBy().orElseThrow()).contains("s.133(6)"));
    }

    @Test
    void evaluate_whenFamilyPlanningIsUnderTheLimit_deductsTheWhole() {
        // When — s.133(1)(a)(xxiii): 30,000, under 1,00,000
        NodeResult result = run(Case.of(donation(DonationClause.FAMILY_PLANNING, "30000")));

        // Then
        assertThat(result.value()).isEqualTo(rupees("30000"));
    }

    @Test
    void evaluate_whenRentIsClaimedAndTheLimitDoesNotBind_worksRentOnIncomeAfterTheDonation() {
        // Given — adjusted GTI 6,00,000 − 1,50,000 = 4,50,000, limit 45,000; charitable 40,000 → 20,000
        Case c = Case.of(donation(DonationClause.CHARITABLE_INSTITUTION, "40000")).withRent("60000");

        // When — s.134 base 4,50,000 − 20,000 = 4,30,000: (a) 60,000 − 43,000 = 17,000
        NodeResult result = run(c);

        // Then
        assertThat(lines(result, RuleIds.DED_DONATION_HALF)).singleElement()
                .satisfies(line -> assertThat(line.out()).isEqualTo(rupees("20000")));
        assertThat(lines(result, RuleIds.DED_RENT_PAID)).singleElement()
                .satisfies(line -> assertThat(line.out()).isEqualTo(rupees("17000")));
        assertThat(result.value()).isEqualTo(rupees("187000"));
    }

    @Test
    void evaluate_whenRentIsClaimedAndTheLimitBinds_refuses() {
        // Given — charitable 60,000 over the 45,000 limit
        Case c = Case.of(donation(DonationClause.CHARITABLE_INSTITUTION, "60000")).withRent("60000");

        // When / Then
        assertThatThrownBy(() -> run(c)).hasMessageContaining("OQ-23");
    }

    @Test
    void evaluate_whenWholeAndHalfSumsTogetherExceedTheLimit_refuses() {
        // When / Then — 60,000 + 60,000 over 1,00,000 (OQ-24)
        assertThatThrownBy(() -> run(Case.of(donation(DonationClause.FAMILY_PLANNING, "60000"),
                        donation(DonationClause.PUBLIC_BODY, "60000"))))
                .hasMessageContaining("OQ-24");
    }

    @Test
    void evaluate_whenTheRegimeIsNew_givesNilLinesNamingTheRegime() {
        // When — s.202(2)(a)(xii)
        NodeResult result = run(Case.of(donation(DonationClause.NAMED_FUND, "50000"),
                donation(DonationClause.CHARITABLE_INSTITUTION, "40000")).regime(NEW));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).extracting(TraceLine::rule)
                .containsExactly(RuleIds.DED_DONATION_WHOLE, RuleIds.DED_DONATION_HALF);
        assertThat(result.trace()).allSatisfy(line ->
                assertThat(line.limitedBy().orElseThrow()).isEqualTo("not in the NEW_TAX_REGIME regime"));
    }

    @Test
    void evaluate_whenAHinduUndividedFamilyDonates_refuses() {
        // When / Then
        assertThatThrownBy(() -> run(Case.of(donation(DonationClause.NAMED_FUND, "50000"))
                        .assessee(new Huf(ResidentialStatus.RESIDENT))))
                .hasMessageContaining("Hindu undivided family");
    }

    @Test
    void evaluate_whenDonatingInTaxYear2025_appliesSection80g() {
        // When — 1961 s.80G (D-148): PM CARES 50,000 (TOLA 2020, whole); a charitable institution 2,40,000 over the
        // 1,00,000 limit, half; the Drought Relief Fund 20,000, half; 2,500 in cash, nil
        NodeResult pmCares = run(Case.of(donation(DonationClause.NAMED_FUND, "50000")).year(TaxYearId.of(2025)));
        NodeResult charitable = run(Case.of(donation(DonationClause.CHARITABLE_INSTITUTION, "240000"))
                .year(TaxYearId.of(2025)));
        NodeResult drought = run(Case.of(donation(DonationClause.DROUGHT_RELIEF_FUND, "20000"))
                .year(TaxYearId.of(2025)));
        NodeResult cash = run(Case.of(new Donation(DonationClause.NAMED_FUND, Money.of("2500"), true, true))
                .year(TaxYearId.of(2025)));

        // Then
        assertThat(pmCares.value()).isEqualTo(rupees("50000"));
        assertThat(charitable.value()).isEqualTo(rupees("50000"));
        assertThat(drought.value()).isEqualTo(rupees("10000"));
        assertThat(cash.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void evaluate_whenTaxYear2025DonationsAndRentAreClaimed_ordersSection80gBeforeSection80gg() {
        // When — as TY 2026-27: 80G's 20,000 under a 45,000 limit, then 80GG on 4,30,000 → 17,000; over it, refused
        NodeResult within = run(Case.of(donation(DonationClause.CHARITABLE_INSTITUTION, "40000")).withRent("60000")
                .year(TaxYearId.of(2025)));

        // Then — 1,50,000 + 20,000 + 17,000
        assertThat(within.value()).isEqualTo(rupees("187000"));
        assertThatThrownBy(() -> run(Case.of(donation(DonationClause.CHARITABLE_INSTITUTION, "60000"))
                        .withRent("60000").year(TaxYearId.of(2025))))
                .hasMessageContaining("OQ-23");
    }

    @Test
    void donation_whenTheAmountIsNil_refuses() {
        // When / Then
        assertThatThrownBy(() -> new Donation(DonationClause.NAMED_FUND, Money.ZERO, false, true))
                .hasMessageContaining("more than nil");
    }
}
