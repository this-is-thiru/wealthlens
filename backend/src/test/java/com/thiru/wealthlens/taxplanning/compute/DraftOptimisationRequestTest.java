package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.taxengine.api.AllocationSpace;
import com.thiru.wealthlens.taxengine.api.ProvidentFundTerms;
import com.thiru.wealthlens.taxengine.input.EmployerFund;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** What a request must carry before it reaches the engine (W-238b): refused on the way in, never defaulted. */
class DraftOptimisationRequestTest {

    @Test
    void constructor_whenTheResidualIsMissing_isRefused() {
        // Given / When / Then
        assertThatThrownBy(() -> new DraftOptimisationRequest(List.of("HRA"), Optional.empty(), Optional.empty(), null, "0", Optional.empty(),
                Optional.empty()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("movable, residual and cashFloor");
    }

    @Test
    void constructor_whenTheBreadthFundsAndContributionsAreNull_holdsThemAsAbsent() {
        // Given / When
        DraftOptimisationRequest request = new DraftOptimisationRequest(List.of("HRA"), null, null, "SPECIAL_ALLOWANCE", "0",
                null, null);

        // Then
        assertThat(request.verificationBreadth()).isEmpty();
        assertThat(request.movableFundNames()).isEmpty();
        assertThat(request.contributions()).isEmpty();
    }

    @Test
    void movableFundNames_whenANameIsNotAFund_isRefusedNamingIt() {
        // Given
        DraftOptimisationRequest request = new DraftOptimisationRequest(List.of(), Optional.of(List.of("NPS")),
                Optional.empty(), "SPECIAL_ALLOWANCE", "0", Optional.empty(), Optional.empty());

        // When / Then
        assertThatThrownBy(request::movableFundNames).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("\"NPS\"").hasMessageContaining("NOTIFIED_PENSION_SCHEME");
    }

    @Test
    void cashFloorRupees_whenNotAnAmount_isRefusedNamingIt() {
        // Given
        DraftOptimisationRequest request = new DraftOptimisationRequest(List.of("HRA"), Optional.empty(), Optional.empty(), "SPECIAL_ALLOWANCE",
                "lots",
                Optional.empty(), Optional.empty());

        // When / Then
        assertThatThrownBy(request::cashFloorRupees).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("\"lots\"");
    }

    @Test
    void codes_whenWellFormed_areTheEngineCodes() {
        // Given
        DraftOptimisationRequest request = new DraftOptimisationRequest(List.of("HRA", "UNIFORM_ALLOWANCE"),
                Optional.of(List.of("NOTIFIED_PENSION_SCHEME")), Optional.of(List.of("EMPLOYEE_NPS")), "SPECIAL_ALLOWANCE",
                "150000", Optional.of(2), Optional.empty());

        // When / Then
        assertThat(request.moves()).isEqualTo(new AllocationSpace.Moves(
                Set.of(new ComponentCode("HRA"), new ComponentCode("UNIFORM_ALLOWANCE")),
                Set.of(EmployerFund.NOTIFIED_PENSION_SCHEME), Set.of(new ComponentCode("EMPLOYEE_NPS"))));
        assertThat(request.residualCode()).isEqualTo(new ComponentCode("SPECIAL_ALLOWANCE"));
        assertThat(request.cashFloorRupees()).isEqualTo(Money.of("150000"));
        assertThat(request.movableFundNames()).containsExactly(EmployerFund.NOTIFIED_PENSION_SCHEME);
    }

    @Test
    void providentFundTerms_whenAbsent_bothSharesFollowBasic() {
        // Given
        DraftOptimisationRequest request = new DraftOptimisationRequest(List.of("BASIC"), null, null, "SPECIAL_ALLOWANCE",
                "0", null, null);

        // When / Then
        assertThat(request.providentFundTerms()).isEqualTo(ProvidentFundTerms.FOLLOW_BASIC);
    }

    @Test
    void providentFundTerms_whenOneShareIsHeld_holdsOnlyThatShare() {
        // Given
        DraftOptimisationRequest request = new DraftOptimisationRequest(List.of("BASIC"), Optional.empty(),
                Optional.empty(), "SPECIAL_ALLOWANCE", "0", Optional.empty(), Optional.of(new DraftProvidentFundTerms(Optional.of(true),
                        Optional.of(false), Optional.empty())));

        // When / Then
        assertThat(request.providentFundTerms()).isEqualTo(new ProvidentFundTerms(true, false, Optional.empty()));
    }

    @Test
    void providentFundTerms_whenOnlyTheBaseIsStated_bothSharesFollowOverIt() {
        // Given
        DraftOptimisationRequest request = new DraftOptimisationRequest(List.of("BASIC"), Optional.empty(),
                Optional.empty(), "SPECIAL_ALLOWANCE", "0", Optional.empty(), Optional.of(new DraftProvidentFundTerms(null, null,
                        Optional.of(true))));

        // When / Then
        assertThat(request.providentFundTerms()).isEqualTo(new ProvidentFundTerms(true, true, Optional.of(true)));
    }
}
