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

/** Each move carries the table's value (W-238f C1a; W-238g C4a). */
class DraftOptimisationTest {

    private static final Liquidity NONE = new Liquidity(List.of(), List.of());

    private static final HrSupportTable TABLE = new HrSupportTable(Map.of(
            "HRA", HrSupportLikelihood.HIGH,
            "CONVEYANCE_ALLOWANCE", HrSupportLikelihood.MEDIUM,
            "NOTIFIED_PENSION_SCHEME", HrSupportLikelihood.EMPLOYER_DISCRETION,
            "RECOGNISED_PROVIDENT_FUND", HrSupportLikelihood.LOW));

    private static Change change(String component) {
        return new Change(component, new ComponentFacts("SALARY", "CASH", List.of(), NONE), Money.ZERO, Money.TEN,
                Optional.empty(), Money.ZERO);
    }

    @Test
    void of_whenComponentsFundsAndAFollowerMove_givesComponentsThenFundsAndNoneForTheFollower() {
        // Given — the provident fund only follows basic
        Optimisation optimisation = new Optimisation("NEW_TAX_REGIME", Money.TEN, "NEW_TAX_REGIME", Money.ZERO,
                List.of(change("HRA"), change("CONVEYANCE_ALLOWANCE")),
                List.of(new FundChange("NOTIFIED_PENSION_SCHEME", List.of(), Money.ZERO, Money.TEN, Optional.empty(),
                        Money.ZERO, NONE)),
                List.of(new FollowingChange("RECOGNISED_PROVIDENT_FUND", List.of(), Money.ZERO, Money.TEN, "BASIC",
                        NONE)),
                Money.ZERO, List.of("HRA"), 1, Optional.empty(), Optional.empty());

        // When
        DraftOptimisation view = DraftOptimisation.of(optimisation, TABLE);

        // Then
        assertThat(view.optimisation()).isSameAs(optimisation);
        assertThat(view.hrSupport()).containsExactly(
                new HrSupport("HRA", HrSupportLikelihood.HIGH),
                new HrSupport("CONVEYANCE_ALLOWANCE", HrSupportLikelihood.MEDIUM),
                new HrSupport("NOTIFIED_PENSION_SCHEME", HrSupportLikelihood.EMPLOYER_DISCRETION));
    }
}
