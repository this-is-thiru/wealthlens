package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.EmployerLoan;
import com.thiru.wealthlens.taxengine.input.EmployerLoan.InsuranceReimbursement;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A loan from the employer as a perquisite (S4 r.15(5) Table IV Sl. 1; Rules 1962 r.3(7)(i); D-118): one month's
 * interest at the entered State Bank of India rate per month-end balance, less interest paid; nil for petty loans
 * (₹2,00,000 / twenty thousand rupees, not exceeding) and for a listed disease save what insurance reimbursed. Each
 * figure is worked by hand.
 */
class LoanPerquisiteTest {

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");
    private static final Rate NINE_PERCENT = Rate.percent("9");

    private static List<Money> constant(String balance, int months) {
        return Collections.nCopies(months, Money.of(balance));
    }

    private static EmployerLoan loan(String lent, List<Money> balances, String paid) {
        return new EmployerLoan(0, Money.of(lent), NINE_PERCENT, balances, Money.of(paid), false, Optional.empty());
    }

    private static NodeResult run(int year, List<EmployerLoan> loans) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, NEW_TAX_REGIME,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                new EnteredFacts(List.of(new Employment(List.of(), true, EmployerKind.OTHER, false, Optional.empty())),
                        List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), loans, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
        return NodeHarness.with(pack)
                .regime(NEW_TAX_REGIME)
                .inputs(inputs)
                .given(NodeIds.SALARY_BASE, new SalaryBases(Map.of(RuleIds.SAL_SALARY_FOR_ACCOM, List.of(Money.ZERO))))
                .given(NodeIds.SALARY_EXEMPTIONS, MoneyValue.of(Money.ZERO))
                .given(NodeIds.SALARY_MONETARYINCOME, MoneyValue.of(Money.ZERO))
                .run(new PerquisiteNode());
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenALoanIsOutstandingAllYear_isAMonthsInterestPerBalanceLessInterestPaid(int year) {
        // When
        NodeResult result = run(year, List.of(loan("500000", constant("500000", 12), "10000")));

        // Then — 12 × 5,00,000 × 9% ÷ 12 = 45,000, less 10,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("35000")));
        assertThat(result.trace().getFirst().provenance().verificationLevel()).isEqualTo(VerificationLevel.SOURCE_CHECKED);
    }

    @Test
    void evaluate_whenRepaidMonthly_valuesEachMonthEndBalance() {
        // Given — 2,90,000 down to 1,80,000 by 10,000
        List<Money> balances = new ArrayList<>();
        for (int balance = 290000; balance >= 180000; balance -= 10000) {
            balances.add(Money.of(String.valueOf(balance)));
        }

        // When
        NodeResult result = run(2026, List.of(loan("300000", balances, "0")));

        // Then — 28,20,000 × 9% ÷ 12 = 21,150
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("21150")));
    }

    @Test
    void evaluate_whenInterestPaidExceedsTheBankRateFigure_isNil() {
        // When — 45,000 at the Bank's rate, 50,000 paid
        NodeResult result = run(2026, List.of(loan("500000", constant("500000", 12), "50000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void evaluate_whenLoansLentAreExactlyThePettyLimitInTaxYear2026_areNil() {
        // When — "not exceeding ₹2,00,000": inclusive
        NodeResult result = run(2026, List.of(loan("200000", constant("200000", 12), "0")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace().getFirst().rule()).isEqualTo(RuleIds.PERQ_LOAN_PETTY);
    }

    @Test
    void evaluate_whenLoansLentExceedThePettyLimitByOneRupee_areValued() {
        // When — 12 × 2,00,001 × 9% ÷ 12 = 18,000.09
        NodeResult result = run(2026, List.of(loan("200001", constant("200001", 12), "0")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("18000.09")));
    }

    @Test
    void evaluate_whenTwoLoansAddUpBeyondThePettyLimit_bothAreValued() {
        // When — 1,50,000 + 1,00,000 lent = 2,50,000 exceeds 2,00,000; each valued
        NodeResult result = run(2026, List.of(loan("150000", constant("150000", 12), "0"),
                loan("100000", constant("100000", 12), "0")));

        // Then — 13,500 + 9,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("22500")));
    }

    @Test
    void evaluate_whenTaxYear2025_usesTheTwentyThousandRupeeLimit() {
        // When
        NodeResult within = run(2025, List.of(loan("20000", constant("20000", 12), "0")));
        NodeResult beyond = run(2025, List.of(loan("25000", constant("25000", 12), "0")));

        // Then — 25,000 × 9% = 2,250
        assertThat(within.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(beyond.value()).isEqualTo(MoneyValue.of(Money.of("2250")));
    }

    @Test
    void evaluate_whenForAListedDiseaseAndNothingReimbursed_isNil() {
        // When
        NodeResult result = run(2026, List.of(new EmployerLoan(0, Money.of("400000"), NINE_PERCENT,
                constant("400000", 12), Money.ZERO, true, Optional.empty())));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void evaluate_whenInsuranceReimbursedPartOfAMedicalLoan_valuesThatPartAfterwards() {
        // Given — 1,00,000 reimbursed in the sixth month (position 5)
        EmployerLoan medical = new EmployerLoan(0, Money.of("400000"), NINE_PERCENT, constant("400000", 12),
                Money.ZERO, true, Optional.of(new InsuranceReimbursement(Money.of("100000"), 5)));

        // When
        NodeResult result = run(2026, List.of(medical));

        // Then — 1,00,000 × 9% ÷ 12 × 6 months = 4,500
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("4500")));
    }

    @Test
    void construct_whenThirteenBalances_isRefused() {
        // When / Then
        assertThatThrownBy(() -> loan("500000", constant("500000", 13), "0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("13");
    }

    @Test
    void construct_whenAReimbursementIsEnteredForANonMedicalLoan_isRefused() {
        // When / Then
        assertThatThrownBy(() -> new EmployerLoan(0, Money.of("400000"), NINE_PERCENT, constant("400000", 12),
                        Money.ZERO, false, Optional.of(new InsuranceReimbursement(Money.of("100000"), 5))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("listed disease");
    }
}
