package com.thiru.wealthlens.taxengine.pipeline;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.SideVenue;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Term;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BucketOutcomeTest {

    private static final BucketFacts FACTS = new BucketFacts("EQUITY", "PAID", "PAID", "PURCHASE", Optional.empty(),
            LocalDate.of(2024, 4, 1), LocalDate.of(2026, 9, 1), SideVenue.UNRECORDED, SideVenue.UNRECORDED);

    @Test
    void new_whenBothATermAndALeavingRowAreGiven_refuses() {
        // Given
        BucketOutcome.TermDecision decision = new BucketOutcome.TermDecision(Term.LONG_TERM, RuleIds.CG_TERM_LISTED_SECURITY, 12);

        // When / Then
        assertThatThrownBy(() -> new BucketOutcome(FACTS, Money.ZERO, List.of(), Optional.of(decision), Optional.of(RuleIds.CG_DEPOSIT),
                Money.ZERO, Money.ZERO, Money.ZERO, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void new_whenNeitherIsGiven_refuses() {
        // When / Then
        assertThatThrownBy(() -> new BucketOutcome(FACTS, Money.ZERO, List.of(), Optional.empty(), Optional.empty(), Money.ZERO,
                Money.ZERO, Money.ZERO, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void new_whenAFigureOrThePartIsMissing_refuses() {
        // Given
        BucketOutcome.TermDecision decision = new BucketOutcome.TermDecision(Term.LONG_TERM, RuleIds.CG_TERM_LISTED_SECURITY, 12);
        String refusal = "A bucket's outcome states its consideration, cost and expenditure, and its part or none";

        // When / Then
        assertThatThrownBy(() -> new BucketOutcome(FACTS, Money.ZERO, List.of(), Optional.of(decision), Optional.empty(),
                null, Money.ZERO, Money.ZERO, Optional.empty())).hasMessage(refusal);
        assertThatThrownBy(() -> new BucketOutcome(FACTS, Money.ZERO, List.of(), Optional.of(decision), Optional.empty(),
                Money.ZERO, null, Money.ZERO, Optional.empty())).hasMessage(refusal);
        assertThatThrownBy(() -> new BucketOutcome(FACTS, Money.ZERO, List.of(), Optional.of(decision), Optional.empty(),
                Money.ZERO, Money.ZERO, null, Optional.empty())).hasMessage(refusal);
        assertThatThrownBy(() -> new BucketOutcome(FACTS, Money.ZERO, List.of(), Optional.of(decision), Optional.empty(),
                Money.ZERO, Money.ZERO, Money.ZERO, null)).hasMessage(refusal);
    }
}
