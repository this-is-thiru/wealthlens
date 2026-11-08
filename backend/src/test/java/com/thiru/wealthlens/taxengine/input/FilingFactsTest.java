package com.thiru.wealthlens.taxengine.input;

import static com.thiru.wealthlens.taxengine.input.InputRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** What {@link FilingFacts} refuses, one bad field at a time; each message is the record's own (W-240b). */
class FilingFactsTest {

    private static final LocalDate DUE = LocalDate.of(2027, 7, 31);

    @Test
    void construct_whenEveryFieldIsValid_isAccepted() {
        // When
        FilingFacts valid = new FilingFacts(DUE, Optional.empty(), List.of(), Money.ZERO, List.of());

        // Then
        assertThat(valid.dueDate()).isEqualTo(DUE);
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                refusal("payment amount null", () -> new FilingFacts.Payment(DUE, null),
                        "An advance-tax payment must carry an amount"),
                refusal("dueDate null", () -> new FilingFacts(null, Optional.empty(), List.of(), Money.ZERO, List.of()),
                        "FilingFacts must carry a due date"),
                refusal("advanceTaxPaid null", () -> new FilingFacts(DUE, Optional.empty(), null, Money.ZERO, List.of()),
                        "FilingFacts.advanceTaxPaid may not be null"),
                refusal("taxAtSource null", () -> new FilingFacts(DUE, Optional.empty(), List.of(), null, List.of()),
                        "FilingFacts.taxAtSource must be nil or more"),
                refusal("taxAtSource negative", () -> new FilingFacts(DUE, Optional.empty(), List.of(), Money.of("-1"), List.of()),
                        "FilingFacts.taxAtSource must be nil or more"),
                refusal("selfAssessmentPaid null", () -> new FilingFacts(DUE, Optional.empty(), List.of(), Money.ZERO, null),
                        "FilingFacts.selfAssessmentPaid may not be null"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
