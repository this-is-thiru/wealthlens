package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.Optimisation;
import com.thiru.wealthlens.taxengine.api.Optimisation.Change;
import com.thiru.wealthlens.taxengine.api.Optimisation.ComponentFacts;
import com.thiru.wealthlens.taxengine.api.Optimisation.FollowingChange;
import com.thiru.wealthlens.taxengine.api.Optimisation.FundChange;
import com.thiru.wealthlens.taxengine.api.Optimisation.Liquidity;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.compute.DraftOptimisation.HrSupport;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Each move carries the table's value, and a gap stays a gap (W-238f C1a, C3a). */
class DraftOptimisationTest {

    private static final Liquidity NONE = new Liquidity(List.of(), List.of());

    private static final HrSupportTable TABLE = new HrSupportTable(Map.of(
            "HRA", HrSupportLikelihood.HIGH,
            "NOTIFIED_PENSION_SCHEME", HrSupportLikelihood.EMPLOYER_DISCRETION,
            "RECOGNISED_PROVIDENT_FUND", HrSupportLikelihood.LOW));

    private static Change change(String component) {
        return new Change(component, new ComponentFacts("SALARY", "CASH", List.of(), NONE), Money.ZERO, Money.TEN,
                Optional.empty(), Money.ZERO);
    }

    @Test
    void of_whenComponentsFundsAndAFollowerMove_givesComponentsThenFundsAndNoneForTheFollower() {
        // Given — EMPLOYEE_NPS has no value in the table; the provident fund only follows basic
        Optimisation optimisation = new Optimisation("NEW_TAX_REGIME", Money.TEN, "NEW_TAX_REGIME", Money.ZERO,
                List.of(change("HRA"), change("EMPLOYEE_NPS")),
                List.of(new FundChange("NOTIFIED_PENSION_SCHEME", List.of(), Money.ZERO, Money.TEN, Optional.empty(),
                        Money.ZERO, NONE)),
                List.of(new FollowingChange("RECOGNISED_PROVIDENT_FUND", List.of(), Money.ZERO, Money.TEN, "BASIC",
                        NONE)),
                Money.ZERO, List.of("HRA"), 1, Optional.empty());

        // When
        DraftOptimisation view = DraftOptimisation.of(optimisation, TABLE);

        // Then
        assertThat(view.optimisation()).isSameAs(optimisation);
        assertThat(view.hrSupport()).containsExactly(
                new HrSupport("HRA", Optional.of(HrSupportLikelihood.HIGH)),
                new HrSupport("EMPLOYEE_NPS", Optional.empty()),
                new HrSupport("NOTIFIED_PENSION_SCHEME", Optional.of(HrSupportLikelihood.EMPLOYER_DISCRETION)));
    }
}
