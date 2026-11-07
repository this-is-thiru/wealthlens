package com.thiru.wealthlens.taxplanning.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PanTest {

    @Test
    void masked_whenWellFormed_keepsTheFirstThreeAndTheLastTwo() {
        // When / Then — DR 15 F1: the first three, five hidden, then from index 8
        assertThat(new Pan("ABCDE1234F").masked()).isEqualTo("ABCXXXXX4F");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "abcde1234f", "ABCDE1234", "ABCDE1234FG", "ABCD11234F", "ABCDE123AF"})
    void new_whenMalformed_refusesWithoutRepeatingIt(String value) {
        // When / Then
        assertThatThrownBy(() -> new Pan(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A PAN is five capital letters, four digits and a capital letter");
    }
}
