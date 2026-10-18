package com.thiru.wealthlens.taxengine.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.election.ElectionResolver;
import com.thiru.wealthlens.taxengine.election.PricedAlternative;
import com.thiru.wealthlens.taxengine.input.HouseOccupancy;
import com.thiru.wealthlens.taxengine.input.HouseProperty;
import com.thiru.wealthlens.taxengine.input.HousingLoan;
import com.thiru.wealthlens.taxengine.input.LoanUse;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The house election over the real engine (D-230): TY 2026-27, the old regime, the harness taxpayer aged 41, with
 * ₹15,00,000 of other-sources interest. Fixture figures are an inference; each net payable is worked by hand from
 * {@code DR 08} T2-4's old-regime working (1,12,500 + 30% above ₹10,00,000; cess 4%).
 */
class HouseElectionPipelineTest {

    private static final GoldenCaseRunner RUNNER = new GoldenCaseRunner();
    private static final RegimeCode OLD = new RegimeCode("FIRST_SCHEDULE_RATES");

    private static HouseProperty house(HouseOccupancy occupancy, String lettingValue, List<HousingLoan> loans) {
        return new HouseProperty(occupancy, Money.of(lettingValue), Money.ZERO, Money.ZERO, false, Money.ZERO,
                Rate.percent("100"), Money.ZERO,
                loans.isEmpty() ? Optional.empty() : Optional.of(LocalDate.of(2024, 1, 15)), loans);
    }

    @Test
    void resolveHouses_whenAThirdHouseIsHeavilyMortgaged_leavesItOutAsR6Says() {
        // Given — DR 13 R-6: A and B letting value 3,00,000, no loan; C letting value 4,00,000, interest 6,00,000 on a
        // loan to acquire it (borrowed 1-5-2023, acquired 15-1-2024, certificate held). Entered {A, C}
        HousingLoan loan = new HousingLoan(LoanUse.ACQUISITION_OR_CONSTRUCTION, LocalDate.of(2023, 5, 1),
                Money.of("600000"), Money.ZERO, Money.ZERO, true, false);
        List<HouseProperty> houses = List.of(house(HouseOccupancy.OWN_RESIDENCE, "300000", List.of()),
                house(HouseOccupancy.NOT_LET, "300000", List.of()),
                house(HouseOccupancy.OWN_RESIDENCE, "400000", List.of(loan)));

        // When
        ElectionResolver.Resolution<List<Integer>> resolution =
                RUNNER.resolveHouses(TaxYearId.of(2026), OLD, houses, Money.of("1500000"));

        // Then — {A, B}: C at 4,00,000 − 1,20,000 − 6,00,000 = −3,20,000, 2,00,000 set off; total income 13,00,000;
        // (1,12,500 + 90,000) × 1.04 = 2,10,600. {A, C}: B +2,10,000, C −2,00,000 (capped); 15,10,000;
        // (1,12,500 + 1,53,000) × 1.04 = 2,76,120
        assertThat(resolution.outcome().chosen()).isEqualTo(List.of(0, 1));
        assertThat(resolution.outcome().alternatives())
                .contains(new PricedAlternative<>(List.of(0, 1), Money.of("210600")),
                        new PricedAlternative<>(List.of(0, 2), Money.of("276120")));
        assertThat(resolution.run().trace())
                .filteredOn(line -> line.rule().equals(RuleIds.SETOFF_HOUSE_PROPERTY) && line.kind() == StepKind.MEASURE)
                .extracting(line -> line.out().asMoney()).contains(Money.of("120000"));
    }

    @Test
    void resolveHouses_whenEveryPairCostsTheSame_keepsTheSpecificationAsEntered() {
        // Given — three identical houses, letting value 3,00,000, no loan; entered {B, C}
        List<HouseProperty> houses = List.of(house(HouseOccupancy.NOT_LET, "300000", List.of()),
                house(HouseOccupancy.OWN_RESIDENCE, "300000", List.of()),
                house(HouseOccupancy.OWN_RESIDENCE, "300000", List.of()));

        // When
        ElectionResolver.Resolution<List<Integer>> resolution =
                RUNNER.resolveHouses(TaxYearId.of(2026), OLD, houses, Money.of("1500000"));

        // Then — every pair leaves one house at 3,00,000 − 90,000 = 2,10,000, and beats any smaller set
        assertThat(resolution.outcome().chosen()).isEqualTo(List.of(1, 2));
    }
}
