package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.AgniveerCorpusFund;
import com.thiru.wealthlens.taxengine.input.Assessee;
import com.thiru.wealthlens.taxengine.input.Assessee.ResidentialStatus;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.Huf;
import com.thiru.wealthlens.taxengine.input.RentDeclaration;
import com.thiru.wealthlens.taxengine.input.Tenancy;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
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
 * s.134 through {@link ChapterEightNode}, TY 2026-27 (S2; S4 r.65; D-141). Figures by hand: gross total income
 * 6,00,000 less an Agniveer subscription of 1,50,000 (s.125(1), the whole amount) leaves 4,50,000 before s.134, so
 * 10% is 45,000 and 25% is 1,12,500.
 */
class RentPaidDeductionTest {

    private static final RegimeCode OLD = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final RegimeCode NEW = new RegimeCode("NEW_TAX_REGIME");
    private static final TaxYearId TY_2026 = TaxYearId.of(2026);
    private static final DateRange WHOLE_YEAR = DateRange.between(LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31));

    private record Case(RegimeCode regime, String grossTotal, String otherDeduction, DateRange occupied, String rent,
            RentDeclaration declaration, String hra, Optional<Assessee> assessee) {

        static Case of(String rent, int months) {
            return new Case(OLD, "600000", "150000", WHOLE_YEAR, rent, new RentDeclaration(months, false, false), "0",
                    Optional.empty());
        }

        Case regime(RegimeCode to) {
            return new Case(to, grossTotal, otherDeduction, occupied, rent, declaration, hra, assessee);
        }

        Case income(String gross, String other) {
            return new Case(regime, gross, other, occupied, rent, declaration, hra, assessee);
        }

        Case occupied(DateRange to) {
            return new Case(regime, grossTotal, otherDeduction, to, rent, declaration, hra, assessee);
        }

        Case declaration(RentDeclaration to) {
            return new Case(regime, grossTotal, otherDeduction, occupied, rent, to, hra, assessee);
        }

        Case hra(String to) {
            return new Case(regime, grossTotal, otherDeduction, occupied, rent, declaration, to, assessee);
        }

        Case assessee(Assessee to) {
            return new Case(regime, grossTotal, otherDeduction, occupied, rent, declaration, hra, Optional.of(to));
        }
    }

    private static NodeResult run(Case c) {
        return run(c, TY_2026);
    }

    private static NodeResult run(Case c, TaxYearId year) {
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(year), new JsonPackAssembler(LockedInstruments.of(year)))
                .load(year);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        Optional<AgniveerCorpusFund> other = Money.of(c.otherDeduction()).isGreaterThan(Money.ZERO)
                ? Optional.of(new AgniveerCorpusFund(Money.of(c.otherDeduction()), Money.ZERO))
                : Optional.empty();
        EngineInputs inputs = new EngineInputs(year, c.regime(),
                TemporalContext.forTaxYear(year, year.startsOn().plusMonths(5)),
                new EnteredFacts(List.of(), List.of(new Tenancy(c.occupied(), "ANY_OTHER_PLACE", false,
                                Money.of(c.rent()))), List.of(), List.of(), Optional.empty(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), other, Optional.of(c.declaration()), List.of(), List.of(), List.of(), List.of()),
                c.assessee().orElse(plain.assessee()), plain.family(), plain.ledgers(), plain.elections(),
                plain.capitalGains(), plain.filing());
        ComponentSet pay = new ComponentSet(Money.of(c.hra()).isGreaterThan(Money.ZERO)
                ? Map.of(HeadCode.SALARIES, List.of(new ComponentSet.Line(0, ComponentCodes.HRA, Money.of(c.hra()))))
                : Map.of());
        return NodeHarness.with(pack).regime(c.regime()).inputs(inputs)
                .given(NodeIds.GTI, Money.of(c.grossTotal()))
                .given(NodeIds.INPUTS_COMPONENTS, pay)
                .given(NodeIds.SALARY_BASE, new SalaryBases(Map.of()))
                .run(new ChapterEightNode());
    }

    private static TraceLine rentLine(NodeResult result) {
        return result.trace().stream().filter(line -> line.rule().equals(RuleIds.DED_RENT_PAID)).findFirst()
                .orElseThrow();
    }

    @Test
    void evaluate_whenRentIsHigh_allowsFiveThousandAMonth() {
        // When — (a) 1,80,000 − 45,000 = 1,35,000; (b) 5,000 × 12 = 60,000; (c) 1,12,500
        NodeResult result = run(Case.of("180000", 12));

        // Then
        assertThat(rentLine(result).out()).isEqualTo(MoneyValue.of(Money.of("60000")));
        assertThat(rentLine(result).limitedBy().orElseThrow()).startsWith("5000 per month");
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("210000")));
    }

    @Test
    void evaluate_whenRentIsLow_allowsRentOverTenPercentOfTotalIncome() {
        // When — (a) 60,000 − 45,000 = 15,000; (b) 60,000; (c) 1,12,500
        NodeResult result = run(Case.of("60000", 12));

        // Then
        assertThat(rentLine(result).out()).isEqualTo(MoneyValue.of(Money.of("15000")));
        assertThat(rentLine(result).limitedBy().orElseThrow()).startsWith("rent less 10% of total income");
    }

    @Test
    void evaluate_whenSevenMonthsAreDeclared_allowsFiveThousandForEach() {
        // Given — a tenancy from 1 September 2026 touches seven calendar months
        Case c = Case.of("105000", 7).occupied(DateRange.between(LocalDate.of(2026, 9, 1), LocalDate.of(2027, 3, 31)));

        // When — (a) 1,05,000 − 45,000 = 60,000; (b) 5,000 × 7 = 35,000; (c) 1,12,500
        NodeResult result = run(c);

        // Then
        assertThat(rentLine(result).out()).isEqualTo(MoneyValue.of(Money.of("35000")));
    }

    @Test
    void evaluate_whenTotalIncomeIsSmall_allowsAQuarterOfIt() {
        // When — base 1,00,000: (a) 1,20,000 − 10,000 = 1,10,000; (b) 60,000; (c) 25,000
        NodeResult result = run(Case.of("120000", 12).income("100000", "0"));

        // Then
        assertThat(rentLine(result).out()).isEqualTo(MoneyValue.of(Money.of("25000")));
        assertThat(rentLine(result).limitedBy().orElseThrow()).startsWith("25% of total income");
    }

    @Test
    void evaluate_whenRentIsWithinTenPercent_allowsNil() {
        // When — (a) 45,000 − 45,000 = 0
        NodeResult result = run(Case.of("45000", 12));

        // Then
        assertThat(rentLine(result).out()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void evaluate_whenHraIsReceived_givesANilLineNamingScheduleThree() {
        // When — s.134(4)(b): any HRA, however small its exemption
        NodeResult result = run(Case.of("180000", 12).hra("1"));

        // Then
        assertThat(rentLine(result).out()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(rentLine(result).limitedBy().orElseThrow()).contains("s.134(4)(b)");
    }

    @Test
    void evaluate_whenAHomeIsOwnedAtThePlace_givesANilLineNamingTheBar() {
        // When
        NodeResult result = run(Case.of("180000", 12).declaration(new RentDeclaration(12, true, false)));

        // Then
        assertThat(rentLine(result).out()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(rentLine(result).limitedBy().orElseThrow()).contains("s.134(4)(a)(i)");
    }

    @Test
    void evaluate_whenAnOccupiedHomeIsOwnedElsewhere_givesANilLineNamingTheBar() {
        // When
        NodeResult result = run(Case.of("180000", 12).declaration(new RentDeclaration(12, false, true)));

        // Then
        assertThat(rentLine(result).out()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(rentLine(result).limitedBy().orElseThrow()).contains("s.134(4)(a)(ii)");
    }

    @Test
    void evaluate_whenTheRegimeIsNew_givesANilLineNamingTheRegime() {
        // When — s.202(2)(a)(xii)
        NodeResult result = run(Case.of("180000", 12).income("600000", "0").regime(NEW));

        // Then
        assertThat(rentLine(result).out()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(rentLine(result).limitedBy()).hasValueSatisfying(reason -> assertThat(reason).startsWith("disallowed in the NEW_TAX_REGIME regime: ")
                .endsWith("(" + RuleIds.REG_NEW_TAX_REGIME_NO_EXEMPTION + ")"));
    }

    @Test
    void evaluate_whenMoreMonthsAreDeclaredThanTheTenanciesOccupy_refuses() {
        // Given — 1 September 2026 onwards is seven calendar months, eight declared
        Case c = Case.of("105000", 8).occupied(DateRange.between(LocalDate.of(2026, 9, 1), LocalDate.of(2027, 3, 31)));

        // When / Then
        assertThatThrownBy(() -> run(c)).hasMessageContaining("8 months of stay")
                .hasMessageContaining("occupy 7 calendar months");
    }

    @Test
    void evaluate_whenAHinduUndividedFamilyDeclares_refuses() {
        // When / Then
        assertThatThrownBy(() -> run(Case.of("180000", 12).income("600000", "0")
                        .assessee(new Huf(ResidentialStatus.RESIDENT))))
                .hasMessageContaining("Hindu undivided family");
    }

    @Test
    void evaluate_whenDeclaredForTaxYear2025_allowsSection80ggsLeastOfThree() {
        // Given — 1961 s.80GG (D-148): base 4,50,000; rent 1,80,000 over 12 months of TY 2025-26
        Case c = Case.of("180000", 12)
                .occupied(DateRange.between(LocalDate.of(2025, 4, 1), LocalDate.of(2026, 3, 31)));

        // When — (a) 1,80,000 − 45,000 = 1,35,000; (b) 5,000 × 12 = 60,000; (c) 1,12,500
        NodeResult result = run(c, TaxYearId.of(2025));

        // Then
        assertThat(rentLine(result).out()).isEqualTo(MoneyValue.of(Money.of("60000")));
    }

    @Test
    void rentDeclaration_whenMonthsAreOutsideOneToTwelve_refuses() {
        // When / Then
        assertThatThrownBy(() -> new RentDeclaration(13, false, false)).hasMessageContaining("1 to 12");
        assertThatThrownBy(() -> new RentDeclaration(0, false, false)).hasMessageContaining("1 to 12");
    }
}
