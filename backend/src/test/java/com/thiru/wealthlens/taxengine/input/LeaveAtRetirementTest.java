package com.thiru.wealthlens.taxengine.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Leave encashment's facts as the employer states them (S2 s.19(1) Table Sl. 14; 1961 s.10(10AA)(ii); D-106). */
class LeaveAtRetirementTest {

    @Test
    void construct_whenTheFactsAreGiven_carriesThem() {
        // When
        LeaveAtRetirement leave = new LeaveAtRetirement(LocalDate.of(2025, 6, 30), Money.of("900000"),
                Money.of("100000"));

        // Then
        assertThat(leave.leaveSalaryAtCredit()).isEqualTo(Money.of("900000"));
        assertThat(leave.averageMonthlySalary()).isEqualTo(Money.of("100000"));
    }

    @Test
    void construct_whenAnAmountIsNegative_isRefused() {
        assertThatThrownBy(() -> new LeaveAtRetirement(LocalDate.of(2025, 6, 30), Money.of("-1"), Money.of("1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }
}
