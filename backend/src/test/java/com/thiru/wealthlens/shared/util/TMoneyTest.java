package com.thiru.wealthlens.shared.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thiru.wealthlens.shared.util.money.TMoney;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class TMoneyTest {

    @Test
    @DisplayName("a drifted amount canonicalises to the same double as the literal")
    void scale_whenTheAmountHasDrifted_matchesTheLiteralExactly() {
        // Given -- what summing one delivery sell's seven charge lines actually produces
        double drifted = 20.00 + 100.00 + 3.30 + 0.10 + 1.50 + 13.50 + 6.98;

        // When / Then -- exact equality, deliberately: that is the property being bought
        assertEquals(145.38, TMoney.scale(drifted));
    }

    @Test
    @DisplayName("adding ten times 0.10 gives exactly 1.00")
    void add_whenAccumulatingPaise_doesNotDrift() {
        // Given
        double running = 0;

        // When
        for (int i = 0; i < 10; i++) {
            running = TMoney.add(running, 0.10);
        }

        // Then
        assertEquals(1.00, running);
    }

    @Test
    @DisplayName("a long accumulation stays exact, which raw += does not")
    void add_over50000Amounts_staysExact() {
        // Given -- raw double drifts to 1.2432964242000148E8 over this many
        double running = 0;
        for (int i = 0; i < 50_000; i++) {
            running = TMoney.add(running, 12.34);
        }

        // When / Then
        assertEquals(617000.00, running);
    }

    @Test
    @DisplayName("a sum does not depend on the order the amounts arrive in")
    void sum_whenReordered_isIdentical() {
        // Given
        List<Double> amounts = new ArrayList<>(List.of(20.00, 100.00, 3.30, 0.10, 1.50, 13.50, 6.98));
        double forwards = TMoney.sum(amounts);
        List<Double> reversed = new ArrayList<>(amounts);
        java.util.Collections.reverse(reversed);

        // When
        double backwards = TMoney.sum(reversed);

        // Then
        assertEquals(forwards, backwards);
        assertEquals(145.38, forwards);
    }

    @Test
    @DisplayName("a null inside a collection is zero, not a crash")
    void sum_whenAnAmountIsNull_treatsItAsZero() {
        // Given
        List<Double> amounts = new ArrayList<>();
        amounts.add(10.50);
        amounts.add(null);
        amounts.add(0.25);

        // When / Then
        assertEquals(10.75, TMoney.sum(amounts));
    }

    @Test
    @DisplayName("half a paisa rounds up, matching how money rounds")
    void scale_whenExactlyHalfAPaisa_roundsUp() {
        // Given / When / Then
        assertEquals(1.24, TMoney.scale(1.235));
        assertEquals(0.01, TMoney.scale(0.005));
    }

    @Test
    @DisplayName("canonicalising is idempotent -- rounding an already-canonical amount changes nothing")
    void scale_whenAlreadyCanonical_isUnchanged() {
        // Given
        double once = TMoney.scale(145.37999999999997);

        // When / Then
        assertEquals(once, TMoney.scale(once));
        assertTrue(once == TMoney.scale(TMoney.scale(once)));
    }
}
