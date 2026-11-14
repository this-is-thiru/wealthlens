package com.thiru.wealthlens.taxengine.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The separation a gratuity is measured on (S2 s.19(1) Table Sl. 6; 1961 s.10(10)(iii); D-106). Fixtures. */
class SeparationTest {

    private static final ComponentCode BASIC = new ComponentCode("BASIC");

    /** The ten calendar months before the month of {@code separatedOn}, each paying {@code basic}. */
    static List<MonthOfPay> tenMonthsBefore(LocalDate separatedOn, String basic) {
        List<MonthOfPay> months = new ArrayList<>();
        for (int back = 10; back >= 1; back--) {
            months.add(new MonthOfPay(YearMonth.from(separatedOn).minusMonths(back), Map.of(BASIC, Money.of(basic))));
        }
        return months;
    }

    @Test
    void completedYears_whenServiceRanTwentyFiveYearsAndThreeMonths_isTwentyFive() {
        // Given — 1-4-2000 to 30-6-2025; "each completed year of service"
        Separation separation = new Separation(LocalDate.of(2000, 4, 1), LocalDate.of(2025, 6, 30),
                tenMonthsBefore(LocalDate.of(2025, 6, 30), "100000"));

        // Then
        assertThat(separation.completedYears()).isEqualTo(25);
    }

    @Test
    void construct_whenAMonthIsTheEventsOwnMonth_isRefused() {
        // Given — the months "immediately preceding the month" of a 30-6-2025 event; these run Sep 2024 – Jun 2025
        List<MonthOfPay> shifted = tenMonthsBefore(LocalDate.of(2025, 7, 31), "100000");

        // When / Then — how many months the law averages is RET-GRATUITY-AVERAGING's, checked by the node (D-317)
        assertThatThrownBy(() -> new Separation(LocalDate.of(2000, 4, 1), LocalDate.of(2025, 6, 30), shifted))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("precede 2025-06");
    }

    @Test
    void construct_whenAMonthIsEnteredTwice_isRefused() {
        // Given
        List<MonthOfPay> twice = new ArrayList<>(tenMonthsBefore(LocalDate.of(2025, 6, 30), "100000"));
        twice.add(twice.getFirst());

        // When / Then
        assertThatThrownBy(() -> new Separation(LocalDate.of(2000, 4, 1), LocalDate.of(2025, 6, 30), twice))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entered once");
    }

    @Test
    void construct_whenNineMonthsAreEntered_isAccepted() {
        // Given — whether nine are enough is the averaging row's question, asked by the node (D-317)
        List<MonthOfPay> nine = tenMonthsBefore(LocalDate.of(2025, 6, 30), "100000").subList(1, 10);

        // When
        Separation separation = new Separation(LocalDate.of(2000, 4, 1), LocalDate.of(2025, 6, 30), nine);

        // Then
        assertThat(separation.monthsBefore()).hasSize(9);
    }

    @Test
    void construct_whenServiceBeginsAfterTheEvent_isRefused() {
        assertThatThrownBy(() -> new Separation(LocalDate.of(2026, 1, 1), LocalDate.of(2025, 6, 30),
                        tenMonthsBefore(LocalDate.of(2025, 6, 30), "100000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construct_whenAMonthPaysANegativeAmount_isRefused() {
        assertThatThrownBy(() -> new MonthOfPay(YearMonth.of(2025, 1), Map.of(BASIC, Money.of("-1"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }
}
