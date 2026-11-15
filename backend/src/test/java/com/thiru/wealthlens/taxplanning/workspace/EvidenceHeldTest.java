package com.thiru.wealthlens.taxplanning.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.Journey;
import com.thiru.wealthlens.taxengine.input.JourneyMode;
import com.thiru.wealthlens.taxengine.input.QualifyingPayment;
import com.thiru.wealthlens.taxengine.input.Tenancy;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/**
 * What a person can show for their claims (W-119b): read as nothing where a draft has none, and refused where it does
 * not fit the entries it is matched to. Bounds are the entries' own facts; TY 2026-27 runs April 2026 to March 2027.
 */
class EvidenceHeldTest {

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);

    private static final ComponentCode TUITION_FEES = new ComponentCode("TUITION_FEES");

    /** A home rented April to September 2026 — six calendar months — and one from the whole year. */
    private static final List<Tenancy> HOMES = List.of(
            new Tenancy(DateRange.between(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 9, 30)), "MUMBAI", false,
                    Money.of("180000")),
            new Tenancy(DateRange.between(TY_2026_27.startsOn(), TY_2026_27.endsOn()), "ANY_OTHER_PLACE", false,
                    Money.of("240000")));

    private static final Journey TRIP = new Journey(LocalDate.of(2026, 10, 2), JourneyMode.AIR, Money.of("40000"),
            Optional.of(Money.of("45000")), Optional.empty(), List.of());

    private static EnteredFacts entered() {
        EnteredFacts journeys = new EnteredFacts(List.of(), List.of(), List.of(TRIP), List.of(), Optional.empty(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of());
        return journeys.withTenancies(HOMES).withQualifyingPayments(List.of(
                new QualifyingPayment(TUITION_FEES, Money.of("30000")),
                new QualifyingPayment(TUITION_FEES, Money.of("20000"))));
    }

    private static EvidenceHeld evidence(List<Integer> months, List<Money> journeys, Map<ComponentCode, Money> payments) {
        return new EvidenceHeld(months, journeys, payments);
    }

    @Test
    void read_whenAStoredDraftHasNoEvidence_holdsNothing() {
        // Given — a document as stored before W-119b
        ObjectNode stored = (ObjectNode) DraftInputsMapper.tree(DraftInputsMapper.blank());
        stored.remove("evidence");

        // When
        DraftInputs read = DraftInputsMapper.read(DraftInputsMapper.text(stored));

        // Then
        assertThat(read.evidence()).isEqualTo(EvidenceHeld.NONE);
        assertThat(read.evidence().receiptedMonths()).isEmpty();
    }

    @Test
    void requireFits_whenEveryFigureIsWithinItsEntry_accepts() {
        // Given — six of six months, the whole year's twelve, the journey's 40,000, and 50,000 of the two tuition payments
        EvidenceHeld held = evidence(List.of(6, 12), List.of(Money.of("40000")), Map.of(TUITION_FEES, Money.of("50000")));

        // When / Then
        assertThatCode(() -> held.requireFits(entered(), TY_2026_27)).doesNotThrowAnyException();
        assertThatCode(() -> EvidenceHeld.NONE.requireFits(entered(), TY_2026_27)).doesNotThrowAnyException();
    }

    @Test
    void requireFits_whenMonthsExceedTheTenancysRun_isRefused() {
        // Given — seven months for a six-month tenancy
        EvidenceHeld held = evidence(List.of(7, 12), List.of(), Map.of());

        // When / Then
        assertThatThrownBy(() -> held.requireFits(entered(), TY_2026_27))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/evidence/receiptedMonths/0 must be from nil to the 6 months tenancy 0 runs");
    }

    @Test
    void requireFits_whenMonthsAreBelowNil_isRefused() {
        // When / Then
        assertThatThrownBy(() -> evidence(List.of(-1, 0), List.of(), Map.of()).requireFits(entered(), TY_2026_27))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/evidence/receiptedMonths/0");
    }

    @Test
    void requireFits_whenATenancyRunsPartlyOutsideTheYear_countsOnlyItsMonthsInTheYear() {
        // Given — January to June 2026: April, May and June fall in TY 2026-27; one wholly before it, none
        EnteredFacts entered = EnteredFacts.none().withTenancies(List.of(
                new Tenancy(DateRange.between(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)), "MUMBAI", false,
                        Money.of("90000")),
                new Tenancy(DateRange.between(LocalDate.of(2025, 4, 1), LocalDate.of(2025, 12, 31)), "MUMBAI", false,
                        Money.of("90000"))));

        // When / Then
        assertThatCode(() -> evidence(List.of(3, 0), List.of(), Map.of()).requireFits(entered, TY_2026_27))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> evidence(List.of(4, 0), List.of(), Map.of()).requireFits(entered, TY_2026_27))
                .hasMessageContaining("the 3 months tenancy 0 runs");
        assertThatThrownBy(() -> evidence(List.of(3, 1), List.of(), Map.of()).requireFits(entered, TY_2026_27))
                .hasMessageContaining("the 0 months tenancy 1 runs");
    }

    @Test
    void requireFits_whenAListIsNotAsLongAsItsEntries_isRefused() {
        // Given — one tenancy's months for two tenancies; two journeys' expenditure for one journey
        EvidenceHeld months = evidence(List.of(6), List.of(), Map.of());
        EvidenceHeld journeys = evidence(List.of(), List.of(Money.ZERO, Money.ZERO), Map.of());

        // When / Then
        assertThatThrownBy(() -> months.requireFits(entered(), TY_2026_27))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/evidence/receiptedMonths holds 1 but /entered/tenancies holds 2");
        assertThatThrownBy(() -> journeys.requireFits(entered(), TY_2026_27))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/evidence/journeyExpenditure holds 2 but /entered/leaveTravel holds 1");
    }

    @Test
    void requireFits_whenAJourneysEvidenceExceedsWhatItIncurred_isRefused() {
        // When / Then — 40,001 against 40,000 incurred
        assertThatThrownBy(() -> evidence(List.of(), List.of(Money.of("40001")), Map.of())
                .requireFits(entered(), TY_2026_27))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/evidence/journeyExpenditure/0 must be from nil to");
    }

    @Test
    void requireFits_whenAPaymentsEvidenceExceedsWhatWasPaidOrNamesACodeNotEntered_isRefused() {
        // Given — 50,001 against 30,000 + 20,000; and premiums, which no entry has
        EvidenceHeld above = evidence(List.of(), List.of(), Map.of(TUITION_FEES, Money.of("50001")));
        EvidenceHeld unknown = evidence(List.of(), List.of(),
                Map.of(new ComponentCode("LIFE_INSURANCE_PREMIUM"), Money.of("1000")));

        // When / Then
        assertThatThrownBy(() -> above.requireFits(entered(), TY_2026_27))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/evidence/qualifyingPayments/TUITION_FEES must be from nil to");
        assertThatThrownBy(() -> unknown.requireFits(entered(), TY_2026_27))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is for a payment code no /entered/qualifyingPayments entry has");
    }
}
