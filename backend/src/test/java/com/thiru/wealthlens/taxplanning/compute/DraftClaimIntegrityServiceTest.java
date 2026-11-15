package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.AgniveerCorpusFund;
import com.thiru.wealthlens.taxengine.input.DisabledDependantSupport;
import com.thiru.wealthlens.taxengine.input.Donation;
import com.thiru.wealthlens.taxengine.input.DonationClause;
import com.thiru.wealthlens.taxengine.input.EducationLoanInterest;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.HealthPayment;
import com.thiru.wealthlens.taxengine.input.HealthPaymentKind;
import com.thiru.wealthlens.taxengine.input.Journey;
import com.thiru.wealthlens.taxengine.input.JourneyMode;
import com.thiru.wealthlens.taxengine.input.JourneyTraveller;
import com.thiru.wealthlens.taxengine.input.LoanPurpose;
import com.thiru.wealthlens.taxengine.input.PurposeLoanInterest;
import com.thiru.wealthlens.taxengine.input.QualifyingPayment;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.input.SpecifiedDiseaseTreatment;
import com.thiru.wealthlens.taxengine.input.Tenancy;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxplanning.workspace.ChapterViiiEvidence;
import com.thiru.wealthlens.taxplanning.workspace.EvidenceHeld;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The integrity panel on the engine alone (W-119c): the design's own hand computations ({@code DR 12} C2 and T12-13), and
 * what is and is not a claim evidence bears on. TY 2026-27, old regime.
 */
class DraftClaimIntegrityServiceTest {

    private static final TaxCalculator CALCULATOR = new TaxCalculator(
            ShippedRulePacks.compile("rulepacks", ClasspathRulePackProvider.shippedYears("rulepacks")));

    private static final DraftClaimIntegrityService SERVICE =
            new DraftClaimIntegrityService(null, null, CALCULATOR, null, null);

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);

    private static final ComponentCode TUITION_FEES = new ComponentCode("TUITION_FEES");

    private static EngineInputs inputs(EnteredFacts entered) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        return new EngineInputs(TY_2026_27, new RegimeCode("FIRST_SCHEDULE_RATES"), plain.temporal(), entered,
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());
    }

    /** T3-1: basic 12,00,000 and dearness allowance 1,20,000 counted in salary, HRA 6,00,000, rent 30,000 a month in Bengaluru. */
    private static EnteredFacts t31() {
        Employment pay = new Employment(List.of(new SalaryComponent(ComponentCodes.BASIC, Money.of("1200000")),
                new SalaryComponent(ComponentCodes.DEARNESS_ALLOWANCE, Money.of("120000")),
                new SalaryComponent(ComponentCodes.HRA, Money.of("600000"))), true, EmployerKind.OTHER, false,
                Optional.empty());
        return EnteredFacts.none().withEmployments(List.of(pay)).withTenancies(List.of(new Tenancy(
                DateRange.between(TY_2026_27.startsOn(), TY_2026_27.endsOn()), "BENGALURU", false, Money.of("360000"))));
    }

    private static EvidenceHeld receipted(int months) {
        return new EvidenceHeld(List.of(months), List.of(), Map.of(), ChapterViiiEvidence.NONE);
    }

    @Test
    void rows_whenEightOfTwelveMonthsAreReceipted_showsWhatTheReceiptsSupportAndTheGap() {
        // When — T12-1
        List<DraftClaimIntegrity.Row> rows = SERVICE.rows(inputs(t31()), receipted(8), false);

        // Then — DR 12 C2's hand computation: allowed is limb (b), 3,60,000 − 1,32,000 = 2,28,000; with 8 months'
        // receipts, rent 2,40,000, so 2,40,000 − 1,32,000 = 1,08,000; the gap 1,20,000. Four months are not receipted
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.rule()).isEqualTo("SAL-HOUSE-RENT-ALLOWANCE");
            assertThat(row.allowed()).isEqualTo(Money.of("228000"));
            assertThat(row.evidenced()).isEqualTo(Money.of("108000"));
            assertThat(row.gap()).isEqualTo(Money.of("120000"));
            assertThat(row.limitedBy()).hasValueSatisfying(why -> assertThat(why).contains("rent less one-tenth of salary"));
            assertThat(row.says()).containsExactly("₹1,20,000 of it is not yet evidenced.",
                    "Rent receipts for the 4 months not yet receipted would evidence it.",
                    "Your landlord's name, address and PAN are not all on file for this draft.");
        });
    }

    @Test
    void rows_whenEveryMonthIsReceipted_saysFullyEvidencedAndLeavesOutWhatEvidenceDoesNotBearOn() {
        // When
        List<DraftClaimIntegrity.Row> rows = SERVICE.rows(inputs(t31()), receipted(12), true);

        // Then — the standard deduction moves with no receipt, so it is no row
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.rule()).isEqualTo("SAL-HOUSE-RENT-ALLOWANCE");
            assertThat(row.gap()).isEqualTo(Money.ZERO);
            assertThat(row.says()).containsExactly("Fully evidenced.",
                    "Your landlord's name, address and PAN are on file for this draft.");
        });
        assertThat(rows).noneMatch(row -> row.rule().startsWith("SAL-STANDARD-DEDUCTION"));
    }

    @Test
    void rows_whenAPaymentIsPartlyEvidenced_showsTheRestNotYetEvidenced() {
        // Given — T12-13: 1,50,000 paid, 40,000 evidenced, on basic pay of 10,00,000
        Employment pay = new Employment(List.of(new SalaryComponent(ComponentCodes.BASIC, Money.of("1000000"))), false,
                EmployerKind.OTHER, false, Optional.empty());
        EnteredFacts entered = EnteredFacts.none().withEmployments(List.of(pay))
                .withQualifyingPayments(List.of(new QualifyingPayment(TUITION_FEES, Money.of("150000"))));
        EvidenceHeld held = new EvidenceHeld(List.of(), List.of(), Map.of(TUITION_FEES, Money.of("40000")), ChapterViiiEvidence.NONE);

        // When
        List<DraftClaimIntegrity.Row> rows = SERVICE.rows(inputs(entered), held, false);

        // Then — 1,50,000 − 40,000 = 1,10,000
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.rule()).isEqualTo("DED-SCHEDULE-XV");
            assertThat(row.allowed()).isEqualTo(Money.of("150000"));
            assertThat(row.evidenced()).isEqualTo(Money.of("40000"));
            assertThat(row.gap()).isEqualTo(Money.of("110000"));
            assertThat(row.says()).containsExactly("₹1,10,000 of it is not yet evidenced.",
                    "Evidence of the ₹1,10,000 paid but not yet evidenced would evidence it.");
        });
    }

    @Test
    void rows_whenNothingIsEnteredThatEvidenceBearsOn_isEmpty() {
        // Given — salary alone
        Employment pay = new Employment(List.of(new SalaryComponent(ComponentCodes.BASIC, Money.of("1000000"))), false,
                EmployerKind.OTHER, false, Optional.empty());

        // When / Then
        assertThat(SERVICE.rows(inputs(EnteredFacts.none().withEmployments(List.of(pay))), EvidenceHeld.NONE, false))
                .isEmpty();
    }

    @Test
    void rows_whenAJourneyIsPartlyEvidenced_showsTheTravelNotYetEvidenced() {
        // Given — T5-4a's journey: LTA 1,20,000 received, 68,000 flown, the fare admitted 80,000; 50,000 evidenced
        Employment pay = new Employment(List.of(new SalaryComponent(ComponentCodes.BASIC, Money.of("1000000")),
                new SalaryComponent(ComponentCodes.LTA, Money.of("120000"))), false, EmployerKind.OTHER, false,
                Optional.empty());
        EnteredFacts entered = EnteredFacts.none().withEmployments(List.of(pay)).withLeaveTravel(List.of(new Journey(
                LocalDate.of(2027, 2, 10), JourneyMode.AIR, Money.of("68000"), Optional.of(Money.of("80000")),
                Optional.empty(), List.of())));
        EvidenceHeld held = new EvidenceHeld(List.of(), List.of(Money.of("50000")), Map.of(), ChapterViiiEvidence.NONE);

        // When
        List<DraftClaimIntegrity.Row> rows = SERVICE.rows(inputs(entered), held, false);

        // Then — the amount incurred binds: 68,000 allowed, 50,000 evidenced, 18,000 the gap
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.rule()).isEqualTo("SAL-LEAVE-TRAVEL-CONCESSION");
            assertThat(row.allowed()).isEqualTo(Money.of("68000"));
            assertThat(row.evidenced()).isEqualTo(Money.of("50000"));
            assertThat(row.says()).containsExactly("₹18,000 of it is not yet evidenced.",
                    "Evidence of the ₹18,000 of travel spending not yet evidenced would evidence it.");
        });
    }

    /** Basic 10,00,000 only: income 9,50,000, every Chapter VIII rupee at 20 % with cess. */
    private static EnteredFacts basicPay() {
        Employment pay = new Employment(List.of(new SalaryComponent(ComponentCodes.BASIC, Money.of("1000000"))), false,
                EmployerKind.OTHER, false, Optional.empty());
        return EnteredFacts.none().withEmployments(List.of(pay));
    }

    private static EvidenceHeld chapterViii(ChapterViiiEvidence held) {
        return new EvidenceHeld(List.of(), List.of(), Map.of(), held);
    }

    /** W-119b1: a ₹25,000 premium for oneself, ₹10,000 of it evidenced — within the self limit, so all of it is allowed. */
    @Test
    void rows_whenAHealthPremiumIsPartlyEvidenced_showsTheCertificateNotYetHeld() {
        // Given
        EnteredFacts entered = basicPay().withHealthPayments(List.of(new HealthPayment(HealthPaymentKind.INSURANCE_PREMIUM,
                true, List.of(), Money.of("25000"), false, 1)));
        EvidenceHeld held = chapterViii(new ChapterViiiEvidence(List.of(Money.of("10000")), List.of(), List.of(), List.of(),
                List.of(), List.of(), Optional.empty()));

        // When
        List<DraftClaimIntegrity.Row> rows = SERVICE.rows(inputs(entered), held, false);

        // Then — 25,000 allowed, 10,000 evidenced, 15,000 the gap
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.rule()).isEqualTo("DED-HEALTH-FAMILY");
            assertThat(row.allowed()).isEqualTo(Money.of("25000"));
            assertThat(row.evidenced()).isEqualTo(Money.of("10000"));
            assertThat(row.says()).containsExactly("₹15,000 of it is not yet evidenced.",
                    "The insurer's certificate or the receipt for the ₹15,000 not yet evidenced would evidence it.");
        });
    }

    /** ₹10,000 to a named fund, deducted whole (s.133(1)(a)); a receipt for ₹4,000 held. */
    @Test
    void rows_whenADonationIsPartlyEvidenced_showsTheReceiptNotYetHeld() {
        // Given
        EnteredFacts entered = basicPay().withDonations(List.of(new Donation(DonationClause.NAMED_FUND,
                Money.of("10000"), false, false)));
        EvidenceHeld held = chapterViii(new ChapterViiiEvidence(List.of(), List.of(Money.of("4000")), List.of(), List.of(),
                List.of(), List.of(), Optional.empty()));

        // When
        List<DraftClaimIntegrity.Row> rows = SERVICE.rows(inputs(entered), held, false);

        // Then — 10,000 allowed, 4,000 evidenced, 6,000 the gap
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.rule()).isEqualTo("DED-DONATION-WHOLE");
            assertThat(row.allowed()).isEqualTo(Money.of("10000"));
            assertThat(row.evidenced()).isEqualTo(Money.of("4000"));
            assertThat(row.says()).containsExactly("₹6,000 of it is not yet evidenced.",
                    "The receipt for the ₹6,000 given but not yet evidenced would evidence it.");
        });
    }

    /** ₹30,000 of education-loan interest in the loan's first year, ₹20,000 certified — the whole interest is deducted. */
    @Test
    void rows_whenEducationLoanInterestIsPartlyEvidenced_showsTheLendersCertificateNotYetHeld() {
        // Given
        EnteredFacts entered = basicPay().withEducationLoanInterest(List.of(new EducationLoanInterest(Money.of("30000"),
                TY_2026_27)));
        EvidenceHeld held = chapterViii(new ChapterViiiEvidence(List.of(), List.of(), List.of(Money.of("20000")),
                List.of(), List.of(), List.of(), Optional.empty()));

        // When
        List<DraftClaimIntegrity.Row> rows = SERVICE.rows(inputs(entered), held, false);

        // Then — 30,000 allowed, 20,000 evidenced, 10,000 the gap
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.rule()).isEqualTo("DED-EDUCATION-LOAN");
            assertThat(row.allowed()).isEqualTo(Money.of("30000"));
            assertThat(row.evidenced()).isEqualTo(Money.of("20000"));
            assertThat(row.says()).containsExactly("₹10,000 of it is not yet evidenced.",
                    "The lender's certificate of interest paid for the ₹10,000 not yet evidenced would evidence it.");
        });
    }

    /** The four families the panel's persona cannot reach: each cut by position, and what is left unevidenced. */
    @Test
    void supported_whenTheOtherChapterViiiFamiliesArePartlyEvidenced_cutsEachEntryAndCountsTheGap() {
        // Given — a house loan's 1,50,000 interest, 90,000 certified; a dependant's 50,000, 20,000 billed; a treatment's
        // 60,000 paid and 10,000 reimbursed, 5,000 billed; the Agniveer subscription 30,000, 30,000 shown
        PurposeLoanInterest houseLoan = new PurposeLoanInterest(LoanPurpose.RESIDENTIAL_HOUSE, Money.of("150000"),
                LocalDate.of(2026, 5, 1), Money.of("3000000"), Optional.of(Money.of("4000000")), Optional.empty(), false);
        EnteredFacts entered = EnteredFacts.none().withPurposeLoanInterest(List.of(houseLoan))
                .withDisabledDependantSupport(List.of(new DisabledDependantSupport(0, Money.of("50000"), false)))
                .withSpecifiedDiseaseTreatment(List.of(new SpecifiedDiseaseTreatment(true, Optional.empty(),
                        Money.of("60000"), Money.of("10000"))))
                .withAgniveerCorpusFund(Optional.of(new AgniveerCorpusFund(Money.of("30000"), Money.of("30000"))));
        EvidencedFacts facts = new EvidencedFacts(entered, chapterViii(new ChapterViiiEvidence(List.of(), List.of(),
                List.of(), List.of(Money.of("90000")), List.of(Money.of("20000")), List.of(Money.of("5000")),
                Optional.of(Money.of("30000")))), TY_2026_27);

        // When
        EnteredFacts cut = facts.supported();

        // Then — each amount is the evidenced one; the reimbursement never above what is paid; the government's part kept
        assertThat(cut.purposeLoanInterest().getFirst().interestPayable()).isEqualTo(Money.of("90000"));
        assertThat(cut.disabledDependantSupport().getFirst().spentOrDeposited()).isEqualTo(Money.of("20000"));
        assertThat(cut.specifiedDiseaseTreatment().getFirst().paid()).isEqualTo(Money.of("5000"));
        assertThat(cut.specifiedDiseaseTreatment().getFirst().reimbursed()).isEqualTo(Money.of("5000"));
        assertThat(cut.agniveerCorpusFund().orElseThrow().governmentContribution()).isEqualTo(Money.of("30000"));
        assertThat(facts.chapterViiiNotEvidenced(EvidenceKind.PURPOSE_LOAN_INTEREST)).isEqualTo(Money.of("60000"));
        assertThat(facts.chapterViiiNotEvidenced(EvidenceKind.DEPENDANT_SUPPORT)).isEqualTo(Money.of("30000"));
        assertThat(facts.chapterViiiNotEvidenced(EvidenceKind.SPECIFIED_DISEASE_TREATMENT)).isEqualTo(Money.of("55000"));
        assertThat(facts.chapterViiiNotEvidenced(EvidenceKind.AGNIVEER_SUBSCRIPTION)).isEqualTo(Money.ZERO);
        assertThat(facts.kindsEntered()).contains(EvidenceKind.PURPOSE_LOAN_INTEREST, EvidenceKind.DEPENDANT_SUPPORT,
                EvidenceKind.SPECIFIED_DISEASE_TREATMENT, EvidenceKind.AGNIVEER_SUBSCRIPTION);
    }

    /** Nothing held for a family holds every entry of it to nil; a donation held to nil is left out. */
    @Test
    void nothingHeldFor_whenADonationFamilyHasNoEvidence_leavesTheDonationsOut() {
        // Given
        EvidencedFacts facts = new EvidencedFacts(EnteredFacts.none().withDonations(List.of(new Donation(
                DonationClause.NAMED_FUND, Money.of("10000"), false, false))), EvidenceHeld.NONE, TY_2026_27);

        // When / Then
        assertThat(facts.nothingHeldFor(EvidenceKind.DONATION).donations()).isEmpty();
        assertThat(facts.chapterViiiNotEvidenced(EvidenceKind.DONATION)).isEqualTo(Money.of("10000"));
    }

    @Test
    void supported_whenAJourneyIsSplitByTraveller_cutsEachShareInProportionTheLastTakingTheRest() {
        // Given — 40,000 and 28,000 of a 68,000 journey; 34,000 evidenced, half
        Journey split = new Journey(LocalDate.of(2027, 2, 10), JourneyMode.AIR, Money.of("68000"),
                Optional.of(Money.of("80000")), Optional.empty(), List.of(
                        new JourneyTraveller(Optional.empty(), Money.of("40000"), Optional.of(Money.of("40000"))),
                        new JourneyTraveller(Optional.of(0), Money.of("28000"), Optional.of(Money.of("40000")))));
        EvidencedFacts facts = new EvidencedFacts(EnteredFacts.none().withLeaveTravel(List.of(split)),
                new EvidenceHeld(List.of(), List.of(Money.of("34000")), Map.of(), ChapterViiiEvidence.NONE), TY_2026_27);

        // When
        Journey cut = facts.supported().leaveTravel().getFirst();

        // Then — 40,000 × 34,000 / 68,000 = 20,000; the last 34,000 − 20,000 = 14,000; the fares as entered
        assertThat(cut.amountIncurred()).isEqualTo(Money.of("34000"));
        assertThat(cut.travellers()).extracting(JourneyTraveller::amountIncurred)
                .containsExactly(Money.of("20000"), Money.of("14000"));
        assertThat(cut.travellers()).extracting(JourneyTraveller::fareAdmissible)
                .containsExactly(Optional.of(Money.of("40000")), Optional.of(Money.of("40000")));
        assertThat(facts.travelNotEvidenced()).isEqualTo(Money.of("34000"));
    }
}
