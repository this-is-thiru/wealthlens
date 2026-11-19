package com.thiru.wealthlens.taxengine.optimise;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.input.EmployerFund;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Two leaders moving one fund (W-238j1 C1a): basic 10,00,000 and dearness allowance 2,00,000 both lead the employer's
 * 1,44,000, the ratio entered 1,44,000 ÷ 12,00,000 = 0.12 over their sum, funded from a special allowance of 14,00,000.
 */
class FollowingTest {

    private static final Movable BASIC = new Movable.Pay(ComponentCodes.BASIC);
    private static final Movable ALLOWANCE = new Movable.Pay(ComponentCodes.DEARNESS_ALLOWANCE);
    private static final Movable FUND = new Movable.Fund(EmployerFund.RECOGNISED_PROVIDENT_FUND);
    private static final Movable.Pay SPECIAL = new Movable.Pay(ComponentCodes.SPECIAL_ALLOWANCE);

    private static SortedMap<Movable, Money> amounts(Object... pairs) {
        SortedMap<Movable, Money> amounts = new TreeMap<>(Movable.ORDER);
        for (int i = 0; i < pairs.length; i += 2) {
            amounts.put((Movable) pairs[i], Money.of((String) pairs[i + 1]));
        }
        return amounts;
    }

    private static Allocation entered() {
        Following following = new Following(amounts(BASIC, "1000000", ALLOWANCE, "200000"), Money.ZERO,
                amounts(FUND, "144000"));
        return new Allocation(amounts(BASIC, "1000000", ALLOWANCE, "200000", FUND, "144000", SPECIAL, "1400000"),
                SPECIAL, following);
    }

    @Test
    void moved_whenBothLeadersMoveInTurn_followsTheirSumAsItNowStands() {
        // Given — basic to 12,00,000: 1,44,000 × 14,00,000 ÷ 12,00,000 = 1,68,000, the residual 14,00,000 − 2,00,000 −
        // 24,000 = 11,76,000; then DA to 4,00,000: 1,44,000 × 16,00,000 ÷ 12,00,000 = 1,92,000, the residual 11,76,000 −
        // 2,00,000 − 24,000 = 9,52,000
        Allocation basicMoved = entered().moved(BASIC, Money.of("1200000"));

        // When
        Allocation both = basicMoved.moved(ALLOWANCE, Money.of("400000"));

        // Then
        assertThat(basicMoved.amount(FUND)).isEqualTo(Money.of("168000"));
        assertThat(basicMoved.residualAmount()).isEqualTo(Money.of("1176000"));
        assertThat(both.amount(FUND)).isEqualTo(Money.of("192000"));
        assertThat(both.residualAmount()).isEqualTo(Money.of("952000"));
    }

    @Test
    void ceiling_whenTheAllowanceLeads_spendsTheResidualOnItAndTheFund() {
        // Given / When — 2,00,000 + 14,00,000 × 12,00,000 ÷ 13,44,000 = 2,00,000 + 12,50,000
        Money ceiling = entered().ceiling(ALLOWANCE);

        // Then
        assertThat(ceiling).isEqualTo(Money.of("1450000"));
    }
}
