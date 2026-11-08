package com.thiru.wealthlens.taxengine.test;

import com.thiru.wealthlens.taxengine.input.AllowanceUse;
import com.thiru.wealthlens.taxengine.input.Assessee;
import com.thiru.wealthlens.taxengine.input.Assessee.ResidentialStatus;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.SideVenue;
import com.thiru.wealthlens.taxengine.input.EmployerFund;
import com.thiru.wealthlens.taxengine.input.EmployerFundContribution;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.HealthPayment;
import com.thiru.wealthlens.taxengine.input.HealthPaymentKind;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.input.OtherSourceItem;
import com.thiru.wealthlens.taxengine.input.OtherSourceKind;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.input.Tenancy;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The four PRD personas ({@code 01-PRD.md} §3 PR-1 – PR-4) as engine inputs, so a test starts from a realistic
 * taxpayer instead of typing every component ({@code DR 19} E10, W-145).
 *
 * <p>Each is a resident individual in TY 2026-27 under the new tax regime, computed on the year's first day, when no
 * instalment or return is due. The facts are the owner's ({@code plans/W-145.md} C1a) and Priya's are {@code DR 08}
 * T8-1's; every figure they produce is worked by hand in {@code sources/2026-11-07-persona-figures.md}.
 */
public final class Fixtures {

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");

    private static final LocalDate LONG_HELD_SINCE = LocalDate.of(2024, 4, 1);

    private static final LocalDate SOLD = LocalDate.of(2026, 9, 1);

    private static final SideVenue SOLD_ON_NSE = new SideVenue(Optional.of("NSE"), Optional.empty());

    private Fixtures() {
    }

    /**
     * PR-1 Ravi: 31, Bengaluru, one employer, CTC ₹28,00,000 of which ₹1,34,400 is the employer's provident-fund
     * contribution; ₹1,34,400 of his own; rent of ₹42,000 a month all year; listed equity sold this year for a long-term
     * gain of ₹1,50,000 and a short-term gain of ₹40,000. His flexible benefit plan and ULIP ({@code DR 04} P1) are not
     * entered: neither changes a figure until he acts on them.
     */
    public static EngineInputs ravi() {
        Employment employment = employment(
                pay(ComponentCodes.BASIC, "1120000"),
                pay(ComponentCodes.HRA, "560000"),
                pay(ComponentCodes.SPECIAL_ALLOWANCE, "985600"),
                pay(ComponentCodes.EMPLOYEE_PF, "134400"));
        Tenancy rented = new Tenancy(DateRange.between(TY_2026_27.startsOn(), TY_2026_27.endsOn()), "BENGALURU",
                false, Money.of("504000"));
        EnteredFacts entered = entered(List.of(employment), List.of(rented),
                List.of(new EmployerFundContribution(0, EmployerFund.RECOGNISED_PROVIDENT_FUND, Money.of("134400"))),
                List.of(), List.of());
        CapitalGainsInput gains = CapitalGainsHarness.inputOf(
                listedEquity("400000", "250000", LONG_HELD_SINCE),
                listedEquity("140000", "100000", LocalDate.of(2026, 5, 1)));
        return inputs(entered, bornOn(LocalDate.of(1995, 9, 30)), gains);
    }

    /**
     * PR-2 Priya, {@code DR 08} T8-1: basic pay of ₹9,00,000 from employer A (April – September) and ₹12,00,000 from
     * employer B (October – March), neither knowing of the other. Her age is not part of the persona; the harness's is
     * used.
     */
    public static EngineInputs priya() {
        EnteredFacts entered = entered(List.of(
                employment(pay(ComponentCodes.BASIC, "900000")),
                employment(pay(ComponentCodes.BASIC, "1200000"))), List.of(), List.of(), List.of(), List.of());
        return inputs(entered, NodeHarness.plainTaxpayer().assessee(), noGains());
    }

    /**
     * PR-3 Anand: 62, retired, a pension of ₹6,00,000 from his former employer, interest of ₹14,000 on savings and
     * ₹90,000 on term deposits, and a health-insurance premium of ₹40,000 for himself. The pension is entered as basic
     * pay because the component vocabulary has no uncommuted pension (W-242); both are salary under s.16, so no figure
     * turns on it.
     */
    public static EngineInputs anand() {
        EnteredFacts entered = entered(List.of(employment(pay(ComponentCodes.BASIC, "600000"))), List.of(),
                List.of(),
                List.of(new HealthPayment(HealthPaymentKind.INSURANCE_PREMIUM, true, List.of(), Money.of("40000"),
                        false, 1)),
                List.of(interest(OtherSourceKind.SAVINGS_DEPOSIT_INTEREST, "14000"),
                        interest(OtherSourceKind.DEPOSIT_INTEREST, "90000")));
        return inputs(entered, bornOn(LocalDate.of(1964, 9, 30)), noGains());
    }

    /**
     * PR-4 Meera: basic pay of ₹45,75,000 and a long-term gain of ₹5,50,000 on listed equity — total income just above
     * ₹50,00,000 under either regime, so the surcharge applies and its marginal relief binds. Her age is not part of the
     * persona; the harness's is used.
     */
    public static EngineInputs meera() {
        EnteredFacts entered = entered(List.of(employment(pay(ComponentCodes.BASIC, "4575000"))), List.of(),
                List.of(), List.of(), List.of());
        return inputs(entered, NodeHarness.plainTaxpayer().assessee(),
                CapitalGainsHarness.inputOf(listedEquity("1550000", "1000000", LONG_HELD_SINCE)));
    }

    /**
     * A salaried renter in Mumbai under 60 (W-238a): basic pay and a special allowance from one employer, rent paid for the
     * whole of TY 2026-27, and any allowance spending given. No house rent allowance is entered: the optimiser's cases
     * start from none.
     */
    public static EngineInputs rentingInMumbai(String basic, String specialAllowance, String rent,
            List<AllowanceUse> spending) {
        Employment employment = employment(pay(ComponentCodes.BASIC, basic),
                pay(ComponentCodes.SPECIAL_ALLOWANCE, specialAllowance));
        Tenancy rented = new Tenancy(DateRange.between(TY_2026_27.startsOn(), TY_2026_27.endsOn()), "MUMBAI", false,
                Money.of(rent));
        EnteredFacts entered = new EnteredFacts(List.of(employment), List.of(rented), List.of(), spending,
                Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(),
                List.of());
        return inputs(entered, bornOn(LocalDate.of(1990, 6, 15)), noGains());
    }

    private static EngineInputs inputs(EnteredFacts entered, Assessee person,
            CapitalGainsInput gains) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        return new EngineInputs(TY_2026_27, NEW_TAX_REGIME,
                TemporalContext.forTaxYear(TY_2026_27, TY_2026_27.startsOn()), entered, person, plain.family(),
                plain.ledgers(), plain.elections(), gains, plain.filing());
    }

    private static Individual bornOn(LocalDate dateOfBirth) {
        return new Individual(dateOfBirth, ResidentialStatus.RESIDENT, Optional.empty(), Optional.empty());
    }

    private static SalaryComponent pay(ComponentCode code, String rupees) {
        return new SalaryComponent(code, Money.of(rupees));
    }

    private static Employment employment(SalaryComponent... components) {
        return new Employment(List.of(components), false, EmployerKind.OTHER, false, Optional.empty());
    }

    private static OtherSourceItem interest(OtherSourceKind kind, String rupees) {
        return new OtherSourceItem(kind, Money.of(rupees), Money.ZERO, Money.ZERO, Money.ZERO, false);
    }

    /** A lot of listed equity bought on {@code acquired}, sold on NSE on 2026-09-01, STT paid both ways. */
    private static CapitalGainsInput.Bucket listedEquity(String consideration, String cost, LocalDate acquired) {
        BucketFacts facts = new BucketFacts("EQUITY", "PAID", "PAID", "PURCHASE", Optional.empty(), acquired, SOLD,
                SideVenue.UNRECORDED, SOLD_ON_NSE);
        return new CapitalGainsInput.Bucket(Money.of(consideration), Money.of(cost), List.of(), facts,
                Optional.empty());
    }

    private static CapitalGainsInput noGains() {
        return NodeHarness.plainTaxpayer().capitalGains();
    }

    private static EnteredFacts entered(List<Employment> employments, List<Tenancy> tenancies,
            List<EmployerFundContribution> employerContributions, List<HealthPayment> healthPayments,
            List<OtherSourceItem> otherSourceItems) {
        return new EnteredFacts(employments, tenancies, List.of(), List.of(), Optional.empty(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), employerContributions, Optional.empty(), List.of(), List.of(), List.of(),
                healthPayments, List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(),
                List.of(), List.of(), otherSourceItems, List.of(), List.of(), List.of(), List.of(), List.of(),
                Optional.empty(), List.of());
    }
}
