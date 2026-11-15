package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.Journey;
import com.thiru.wealthlens.taxengine.input.JourneyMode;
import com.thiru.wealthlens.taxengine.input.JourneyTraveller;
import com.thiru.wealthlens.taxengine.input.QualifyingPayment;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
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
        return new EvidenceHeld(List.of(months), List.of(), Map.of());
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
        EvidenceHeld held = new EvidenceHeld(List.of(), List.of(), Map.of(TUITION_FEES, Money.of("40000")));

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
        EvidenceHeld held = new EvidenceHeld(List.of(), List.of(Money.of("50000")), Map.of());

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

    @Test
    void supported_whenAJourneyIsSplitByTraveller_cutsEachShareInProportionTheLastTakingTheRest() {
        // Given — 40,000 and 28,000 of a 68,000 journey; 34,000 evidenced, half
        Journey split = new Journey(LocalDate.of(2027, 2, 10), JourneyMode.AIR, Money.of("68000"),
                Optional.of(Money.of("80000")), Optional.empty(), List.of(
                        new JourneyTraveller(Optional.empty(), Money.of("40000"), Optional.of(Money.of("40000"))),
                        new JourneyTraveller(Optional.of(0), Money.of("28000"), Optional.of(Money.of("40000")))));
        EvidencedFacts facts = new EvidencedFacts(EnteredFacts.none().withLeaveTravel(List.of(split)),
                new EvidenceHeld(List.of(), List.of(Money.of("34000")), Map.of()), TY_2026_27);

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
