package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxplanning.workspace.OnboardingStep;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Each onboarding step with what answering it is measured to be worth (W-122a C3a), on {@link DraftCompletenessServiceTest}'s
 * persona: basic 10,00,000 and HRA 4,00,000, nothing else, the old regime. Its hand-worked worths at 31.2 %: rent 1,19,600
 * (step 2); Schedule XV 46,800, own NPS 15,600, the assessee's health cover 7,800 (step 4).
 */
class DraftOnboardingServiceTest {

    private static final TaxCalculator CALCULATOR = new TaxCalculator(
            ShippedRulePacks.compile("rulepacks", ClasspathRulePackProvider.shippedYears("rulepacks")));

    private static final DraftOnboardingService SERVICE = new DraftOnboardingService(null, null, CALCULATOR,
            new DraftCompletenessService(null, null, CALCULATOR, null), null);

    private static final DraftCompleteness.Item RENT = new DraftCompleteness.Item("SAL-HOUSE-RENT-ALLOWANCE",
            DraftCompleteness.Direction.REDUCES, Money.ZERO, Money.of("400000"), Optional.of(Money.of("500000")),
            Optional.of("ANY_OTHER_PLACE"), Money.of("119600"));

    private static final List<DraftCompleteness.Item> ROOMS = List.of(
            new DraftCompleteness.Item("DED-SCHEDULE-XV", DraftCompleteness.Direction.REDUCES, Money.ZERO,
                    Money.of("150000"), Optional.empty(), Optional.empty(), Money.of("46800")),
            new DraftCompleteness.Item("DED-NPS-OWN", DraftCompleteness.Direction.REDUCES, Money.ZERO,
                    Money.of("50000"), Optional.empty(), Optional.empty(), Money.of("15600")),
            new DraftCompleteness.Item("DED-HEALTH-FAMILY", DraftCompleteness.Direction.REDUCES, Money.ZERO,
                    Money.of("25000"), Optional.empty(), Optional.empty(), Money.of("7800")));

    @Test
    void steps_whenNoneIsAnswered_givesEachStepItsOwnItems() {
        // When
        List<DraftOnboarding.Step> steps = SERVICE.steps(Set.of(),
                DraftCompletenessServiceTest.inputs("FIRST_SCHEDULE_RATES"));

        // Then
        assertThat(steps).extracting(DraftOnboarding.Step::step).containsExactly(OnboardingStep.values());
        assertThat(steps).noneMatch(DraftOnboarding.Step::answered);
        assertThat(steps.get(0).items()).isEmpty();
        assertThat(steps.get(0).notMeasured()).isPresent();
        assertThat(steps.get(1).items()).containsExactly(RENT);
        assertThat(steps.get(1).notMeasured()).isEmpty();
        assertThat(steps.get(2).items()).isEmpty();
        assertThat(steps.get(2).notMeasured()).isPresent();
        assertThat(steps.get(3).items()).containsExactlyElementsOf(ROOMS);
        assertThat(steps.get(3).notMeasured()).isEmpty();
        assertThat(steps.get(4).items()).isEmpty();
        assertThat(steps.get(4).notMeasured()).hasValueSatisfying(says -> assertThat(says).contains("W-324"));
        assertThat(DraftOnboardingService.next(steps)).contains(OnboardingStep.PAY);
    }

    @Test
    void steps_whenInvestmentsAreAnswered_listsNothingForThemAndLeavesTheOthers() {
        // When — step 4 and step 1 answered: "nothing more to enter"
        List<DraftOnboarding.Step> steps = SERVICE.steps(
                EnumSet.of(OnboardingStep.PAY, OnboardingStep.INVESTMENTS_AND_INSURANCE),
                DraftCompletenessServiceTest.inputs("FIRST_SCHEDULE_RATES"));

        // Then
        assertThat(steps.get(3).answered()).isTrue();
        assertThat(steps.get(3).items()).isEmpty();
        assertThat(steps.get(3).notMeasured()).isEmpty();
        assertThat(steps.get(0).answered()).isTrue();
        assertThat(steps.get(0).notMeasured()).isEmpty();
        assertThat(steps.get(1).items()).containsExactly(RENT);
        assertThat(DraftOnboardingService.next(steps)).contains(OnboardingStep.PAY_STRUCTURE_AND_RENT);
    }

    @Test
    void next_whenEveryStepIsAnswered_isEmpty() {
        // When
        List<DraftOnboarding.Step> steps = SERVICE.steps(EnumSet.allOf(OnboardingStep.class),
                DraftCompletenessServiceTest.inputs("FIRST_SCHEDULE_RATES"));

        // Then
        assertThat(steps).allMatch(step -> step.answered() && step.items().isEmpty());
        assertThat(DraftOnboardingService.next(steps)).isEmpty();
    }

    @Test
    void steps_whenTheRegimeAllowsNoneOfIt_listsNoItems() {
        // When — the new regime allows no HRA, Schedule XV, own NPS or health cover, so every worth is nil and dropped
        List<DraftOnboarding.Step> steps = SERVICE.steps(Set.of(), DraftCompletenessServiceTest.inputs("NEW_TAX_REGIME"));

        // Then
        assertThat(steps).allMatch(step -> step.items().isEmpty());
    }
}
