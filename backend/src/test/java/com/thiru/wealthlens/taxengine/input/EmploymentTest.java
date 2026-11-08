package com.thiru.wealthlens.taxengine.input;

import static com.thiru.wealthlens.taxengine.input.InputRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** What {@link Employment} refuses, one bad field at a time; each message is the record's own (W-240a). */
class EmploymentTest {

    @Test
    void construct_whenANonCompanyEmployerWithNoComponents_isAccepted() {
        // When
        Employment employment = new Employment(List.of(), false, EmployerKind.OTHER, false, Optional.empty());

        // Then
        assertThat(employment.components()).isEmpty();
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                refusal("components null", () -> new Employment(null, false, EmployerKind.OTHER, false, Optional.empty()),
                        "An employment's components may not be null"),
                refusal("employerKind null", () -> new Employment(List.of(), false, null, false, Optional.empty()),
                        "An employment must state its employer type"),
                refusal("companyEmployer null", () -> new Employment(List.of(), false, EmployerKind.OTHER, false, null),
                        "Pass Optional.empty() where the employer is not a company"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
