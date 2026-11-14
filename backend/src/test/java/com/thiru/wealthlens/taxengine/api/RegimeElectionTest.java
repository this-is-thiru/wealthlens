package com.thiru.wealthlens.taxengine.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EducationLoanInterest;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.regime.DeadlineSpec;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Opting out of the default regime: its deadline from the pack and its price (W-114b, D-667), worked by hand from the
 * TY 2026-27 rows. Basic pay 15,75,000.
 *
 * <p>The default, {@code NEW_TAX_REGIME}: standard deduction 75,000 ({@code SAL-STANDARD-DEDUCTION-NEW-TAX-REGIME}); the
 * education-loan interest disallowed ({@code DIS-NEW-TAX-REGIME-NO-EXEMPTION}); 15,00,000 at {@code RATE-SLAB-NEW-TAX-REGIME}
 * 5 % on 4 – 8 lakh, 10 % on 8 – 12, 15 % on 12 – 15: 20,000 + 40,000 + 45,000 = 1,05,000; no rebate, 15,00,000 being over
 * {@code REBATE-NEW-TAX-REGIME}'s 12,00,000 by more than the tax ({@code -EXCESS}); cess 4 % ({@code RATE-HEALTH-EDUCATION-CESS})
 * 4,200; 1,09,200.
 *
 * <p>{@code FIRST_SCHEDULE_RATES}: standard deduction 50,000; 6,00,000 of education-loan interest ({@code DED-EDUCATION-LOAN},
 * no ceiling); 9,25,000 at 5 % on 2.5 – 5 lakh and 20 % on 5 – 9.25: 12,500 + 85,000 = 97,500; no rebate over 5,00,000
 * ({@code REBATE-RESIDENT-INDIVIDUAL}); cess 3,900; 1,01,400. Opting out saves 7,800.
 */
class RegimeElectionTest {

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);

    private static final TaxCalculator CALCULATOR = new TaxCalculator(ShippedRulePacks.compile("rulepacks", Set.of(TY_2026_27)));

    private static EngineInputs inputs(String educationLoanInterest) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        Employment basicPay = new Employment(List.of(new SalaryComponent(new ComponentCode("BASIC"), Money.of("1575000"))),
                false, EmployerKind.OTHER, false, Optional.empty());
        List<EducationLoanInterest> loans = educationLoanInterest == null ? List.of()
                : List.of(new EducationLoanInterest(Money.of(educationLoanInterest), TY_2026_27));
        EnteredFacts entered = new EnteredFacts(List.of(basicPay), List.of(), List.of(), List.of(), Optional.empty(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), loans, List.of(), Optional.empty(), Optional.empty(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Optional.empty(), List.of());
        return new EngineInputs(TY_2026_27, new RegimeCode("NEW_TAX_REGIME"), plain.temporal(), entered, plain.assessee(),
                plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());
    }

    @Test
    void regimeElection_whenOptingOutCostsLess_namesTheReturnsDueDateAndBothBills() {
        // Given / When
        List<RegimeElectionDue> dues = CALCULATOR.regimeElection(inputs("600000"));

        // Then — the TY 2026-27 protocol: OPT_OUT, IN_THE_RETURN, DueDateUnder("263(1)"); the harness's due date 31-7-2027
        assertThat(dues).containsExactly(new RegimeElectionDue("FIRST_SCHEDULE_RATES", Optional.of(LocalDate.of(2027, 7, 31)),
                "IN_THE_RETURN", Optional.empty(), Money.of("109200"), Money.of("101400")));
    }

    @Test
    void regimeElection_whenOptingOutCostsMore_stillNamesItWithBothBills() {
        // Given — no loan: FIRST_SCHEDULE_RATES on 15,25,000 is 12,500 + 1,00,000 + 1,57,500 = 2,70,000 + 10,800 = 2,80,800
        List<RegimeElectionDue> dues = CALCULATOR.regimeElection(inputs(null));

        // Then — priced, but dearer: the calendar, not the engine, decides it is not worth an entry
        assertThat(dues).singleElement().satisfies(due -> {
            assertThat(due.defaultBill()).isEqualTo(Money.of("109200"));
            assertThat(due.bill()).isEqualTo(Money.of("280800"));
        });
    }

    @Test
    void dueBy_whenTheDeadlineNamesAnotherProvision_isRefused() {
        // Given — a draft holds only the return's due date
        DeadlineSpec other = new DeadlineSpec.DueDateUnder("44AB");

        // When / Then
        assertThatThrownBy(() -> TaxCalculator.dueBy(other, inputs(null))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("s.44AB");
    }

    @Test
    void dueBy_whenTheChoiceRidesWithTheReturnOrNeedsNone_hasNoDateOfItsOwn() {
        // Given / When / Then
        assertThat(TaxCalculator.dueBy(new DeadlineSpec.WithTheReturn(), inputs(null))).isEmpty();
        assertThat(TaxCalculator.dueBy(new DeadlineSpec.NoDeadline(), inputs(null))).isEmpty();
    }
}
