package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** What a request must carry before it reaches the engine (W-238b): refused on the way in, never defaulted. */
class DraftOptimisationRequestTest {

    @Test
    void constructor_whenTheResidualIsMissing_isRefused() {
        // Given / When / Then
        assertThatThrownBy(() -> new DraftOptimisationRequest(List.of("HRA"), null, "0", Optional.empty()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("movable, residual and cashFloor");
    }

    @Test
    void constructor_whenTheBreadthIsNull_holdsItAsAbsent() {
        // Given / When
        DraftOptimisationRequest request = new DraftOptimisationRequest(List.of("HRA"), "SPECIAL_ALLOWANCE", "0", null);

        // Then
        assertThat(request.verificationBreadth()).isEmpty();
    }

    @Test
    void cashFloorRupees_whenNotAnAmount_isRefusedNamingIt() {
        // Given
        DraftOptimisationRequest request = new DraftOptimisationRequest(List.of("HRA"), "SPECIAL_ALLOWANCE", "lots",
                Optional.empty());

        // When / Then
        assertThatThrownBy(request::cashFloorRupees).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("\"lots\"");
    }

    @Test
    void codes_whenWellFormed_areTheEngineCodes() {
        // Given
        DraftOptimisationRequest request = new DraftOptimisationRequest(List.of("HRA", "UNIFORM_ALLOWANCE"),
                "SPECIAL_ALLOWANCE", "150000", Optional.of(2));

        // When / Then
        assertThat(request.movableCodes()).containsExactlyInAnyOrder(new ComponentCode("HRA"),
                new ComponentCode("UNIFORM_ALLOWANCE"));
        assertThat(request.residualCode()).isEqualTo(new ComponentCode("SPECIAL_ALLOWANCE"));
        assertThat(request.cashFloorRupees()).isEqualTo(Money.of("150000"));
    }
}
