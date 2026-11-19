package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.Journey;
import com.thiru.wealthlens.taxengine.input.JourneyMode;
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
import com.thiru.wealthlens.taxplanning.workspace.ChapterViiiEvidence;
import com.thiru.wealthlens.taxplanning.workspace.EvidenceHeld;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * {@code DR 12} C8's panel rows — T12-1, T12-3, T12-4, T12-13 — and {@code DR 08} T5-4's "no expenditure evidence" variant
 * as corrected (W-119 C6a), on the integrity panel (W-119d C1a); and the guard that every T12 id has a test or a stated
 * reason it is not run (C2a). TY 2026-27, the old regime.
 */
class T12ClaimIntegrityPanelTest {

    private static final TaxCalculator CALCULATOR = new TaxCalculator(
            ShippedRulePacks.compile("rulepacks", ClasspathRulePackProvider.shippedYears("rulepacks")));

    private static final DraftClaimIntegrityService PANEL =
            new DraftClaimIntegrityService(null, null, CALCULATOR, null, null);

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);

    private static final DateRange WHOLE_YEAR = DateRange.between(TY_2026_27.startsOn(), TY_2026_27.endsOn());

    /** T12-2, T12-11 and T12-12 are not run, each for the reason in the errata under C8 (W-119d C2a). */
    private static final Set<Integer> NOT_RUN = Set.of(2, 11, 12);

    private static EngineInputs inputs(EnteredFacts entered) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        return new EngineInputs(TY_2026_27, new RegimeCode("FIRST_SCHEDULE_RATES"), plain.temporal(), entered,
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());
    }

    private static EnteredFacts pay(List<SalaryComponent> components, boolean termsIncludeDearnessAllowance) {
        return EnteredFacts.none().withEmployments(List.of(new Employment(components, termsIncludeDearnessAllowance,
                EmployerKind.OTHER, false, Optional.empty())));
    }

    private static SalaryComponent component(ComponentCode code, String rupees) {
        return new SalaryComponent(code, Money.of(rupees));
    }

    /** T3-1: basic 12,00,000 and dearness allowance 1,20,000 counted in salary, HRA 6,00,000, rent in Bengaluru. */
    private static EnteredFacts t31(String rent) {
        return pay(List.of(component(ComponentCodes.BASIC, "1200000"), component(ComponentCodes.DEARNESS_ALLOWANCE, "120000"),
                component(ComponentCodes.HRA, "600000")), true)
                .withTenancies(List.of(new Tenancy(WHOLE_YEAR, "BENGALURU", false, Money.of(rent))));
    }

    @Test
    void t12_01_whenEightOfTwelveMonthsAreReceipted_showsAllowedEvidencedAndTheGap() {
        // When
        List<DraftClaimIntegrity.Row> rows = PANEL.rows(inputs(t31("360000")),
                new EvidenceHeld(List.of(8), List.of(), Map.of(), ChapterViiiEvidence.NONE), true);

        // Then — DR 12 C2: limb (b) 3,60,000 − 1,32,000 = 2,28,000; receipts for 8 months, 2,40,000 − 1,32,000 = 1,08,000
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.allowed()).isEqualTo(Money.of("228000"));
            assertThat(row.evidenced()).isEqualTo(Money.of("108000"));
            assertThat(row.gap()).isEqualTo(Money.of("120000"));
            assertThat(row.limitedBy()).hasValueSatisfying(why -> assertThat(why).contains("rent less one-tenth of salary"));
        });
    }

    @Test
    void t12_03_whenRentAboveTheThresholdHasNoLandlordOnFile_saysTheParticularsAreNotOnFile() {
        // When — 3,60,000, above SAL-HOUSE-RENT-LANDLORD-PAN's 1,00,000; no landlord named
        List<DraftClaimIntegrity.Row> rows = PANEL.rows(inputs(t31("360000")), EvidenceHeld.NONE, false);

        // Then — what the panel says; the engine's own request is a notice on the first HRA line (ExemptionNodeTest)
        assertThat(rows).singleElement().satisfies(row -> assertThat(row.says())
                .contains("Your landlord's name, address and PAN are not all on file for this draft."));
    }

    @Test
    void t12_04_whenRentIsBelowTheThreshold_saysNothingOfTheLandlordsParticulars() {
        // Given — basic 6,00,000, HRA 2,40,000, rent 96,000 (8,000 a month), below the threshold of 1,00,000:
        // exemption least of 2,40,000, 96,000 − 60,000 = 36,000, and half of salary 3,00,000 — so a row, at 36,000
        EnteredFacts entered = pay(List.of(component(ComponentCodes.BASIC, "600000"), component(ComponentCodes.HRA, "240000")),
                false).withTenancies(List.of(new Tenancy(WHOLE_YEAR, "BENGALURU", false, Money.of("96000"))));

        // When
        List<DraftClaimIntegrity.Row> rows = PANEL.rows(inputs(entered), EvidenceHeld.NONE, false);

        // Then — r.205(2) Table Sl. 1 asks for the landlord's particulars only above the threshold
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.allowed()).isEqualTo(Money.of("36000"));
            assertThat(row.says()).noneMatch(sentence -> sentence.contains("landlord"));
        });
    }

    @Test
    void t12_13_whenOneAndAHalfLakhIsPaidAndFortyThousandEvidenced_showsTheGapAndWhatClosesIt() {
        // Given — basic 10,00,000, tuition 1,50,000, 40,000 evidenced; the ceiling 1,50,000, so no headroom
        ComponentCode tuition = new ComponentCode("TUITION_FEES");
        EnteredFacts entered = pay(List.of(component(ComponentCodes.BASIC, "1000000")), false)
                .withQualifyingPayments(List.of(new QualifyingPayment(tuition, Money.of("150000"))));

        // When
        List<DraftClaimIntegrity.Row> rows = PANEL.rows(inputs(entered),
                new EvidenceHeld(List.of(), List.of(), Map.of(tuition, Money.of("40000")), ChapterViiiEvidence.NONE), false);

        // Then — 1,50,000 − 40,000 = 1,10,000, and the evidence that closes it
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.gap()).isEqualTo(Money.of("110000"));
            assertThat(row.says()).contains("Evidence of the ₹1,10,000 paid but not yet evidenced would evidence it.");
        });
    }

    @Test
    void t5_4_whenNoExpenditureIsEvidenced_exemptsWhatWasSpentAndShowsAllOfItAsTheGap() {
        // Given — T5-4: LTA received 1,20,000; flown, 68,000 spent for the family, the fare admitted 80,000; no evidence
        EnteredFacts entered = pay(List.of(component(ComponentCodes.BASIC, "1000000"), component(ComponentCodes.LTA, "120000")),
                false).withLeaveTravel(List.of(new Journey(LocalDate.of(2027, 2, 10), JourneyMode.AIR, Money.of("68000"),
                        Optional.of(Money.of("80000")), Optional.empty(), List.of())));

        // When
        List<DraftClaimIntegrity.Row> rows = PANEL.rows(inputs(entered), EvidenceHeld.NONE, false);

        // Then — the exemption stays 68,000 (so 52,000 of the 1,20,000 is taxable), none evidenced, the gap 68,000
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.rule()).isEqualTo("SAL-LEAVE-TRAVEL-CONCESSION");
            assertThat(row.allowed()).isEqualTo(Money.of("68000"));
            assertThat(row.evidenced()).isEqualTo(Money.ZERO);
            assertThat(row.gap()).isEqualTo(Money.of("68000"));
        });
    }

    /** C2a: every T12 id is a test method of the two T12 classes or the copy test, or is listed as not run. */
    @Test
    void ids_whenTheT12TestsAreCollected_accountForEveryRowOfDr12C8() throws ClassNotFoundException {
        // Given
        Pattern id = Pattern.compile("^t12_(\\d{2})_");
        Set<Integer> tested = new TreeSet<>();
        for (Class<?> type : List.of(T12ClaimIntegrityPanelTest.class,
                Class.forName("com.thiru.wealthlens.taxengine.golden.T12ClaimIntegrityTest"), ForbiddenWordingTest.class)) {
            for (Method method : type.getDeclaredMethods()) {
                Matcher found = id.matcher(method.getName());
                if (found.find()) {
                    tested.add(Integer.parseInt(found.group(1)));
                }
            }
        }

        // When
        Set<Integer> accounted = new TreeSet<>(tested);
        accounted.addAll(NOT_RUN);

        // Then — fourteen rows, none both tested and listed as not run
        assertThat(accounted).containsExactlyElementsOf(Stream.iterate(1, row -> row + 1).limit(14).toList());
        assertThat(tested).doesNotContainAnyElementsOf(NOT_RUN);
    }
}
