package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxplanning.assemble.EngineInputsAssembler;
import com.thiru.wealthlens.taxplanning.workspace.DraftService;
import com.thiru.wealthlens.taxplanning.workspace.OnboardingStep;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds {@link DraftOnboarding} from the steps the draft records as answered and the worths completeness measures
 * (W-122a C3a). Which step a measured item belongs to follows the item's source: the rent room to step 2, the Chapter VIII
 * rooms to step 4 — own NPS included, which F34 names nowhere; step 3's NPS is the employer's.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DraftOnboardingService {

    private static final String UNMEASURED = "Nothing measures what answering this step is worth yet";

    /** Why an open step's worth is not stated, for each step nothing measures (C3a); {@code ForbiddenWordingTest} reads it. */
    static final Map<OnboardingStep, String> NOT_MEASURED = Map.of(
            OnboardingStep.PAY, UNMEASURED,
            OnboardingStep.EMPLOYER_FUNDS, UNMEASURED,
            OnboardingStep.OTHER_INCOME_AND_PROPERTY, UNMEASURED + ": income left out waits on department data (W-324),"
                    + " and interest on a loan for an owned house has no bound at the draft's own facts (W-115 C1a)");

    private final DraftService draftService;
    private final EngineInputsAssembler assembler;
    private final TaxCalculator taxCalculator;
    private final DraftCompletenessService completenessService;
    private final Clock clock;

    /**
     * @throws IllegalArgumentException if the draft is not this account's, or elects a regime the year does not have
     */
    public DraftOnboarding onboarding(String email, String draftId) {
        DraftService.OwnedDraft owned = draftService.ownedDraft(email, draftId);
        LocalDate today = LocalDate.now(clock);
        EngineInputsAssembler.Assembled assembled = assembler.assemble(email, draftId,
                DraftForecastService.regimeOf(owned.draft(), taxCalculator), today);
        List<DraftOnboarding.Step> steps = steps(owned.inputs().onboarding().answered(), assembled.inputs());
        return new DraftOnboarding(today, assembled.inputs().regime().value(), assembled.draft().getCurrentRevision(), steps,
                next(steps));
    }

    /** Every step in order; an answered one is not priced. Separate from {@link #onboarding} so it is tested on the engine alone. */
    List<DraftOnboarding.Step> steps(Set<OnboardingStep> answered, EngineInputs inputs) {
        return Arrays.stream(OnboardingStep.values())
                .map(step -> answered.contains(step)
                        ? new DraftOnboarding.Step(step, true, List.of(), Optional.empty())
                        : open(step, inputs))
                .toList();
    }

    static Optional<OnboardingStep> next(List<DraftOnboarding.Step> steps) {
        return steps.stream().filter(step -> !step.answered()).map(DraftOnboarding.Step::step).findFirst();
    }

    private DraftOnboarding.Step open(OnboardingStep step, EngineInputs inputs) {
        return switch (step) {
            case PAY_STRUCTURE_AND_RENT ->
                new DraftOnboarding.Step(step, false, completenessService.rentRoom(inputs), Optional.empty());
            case INVESTMENTS_AND_INSURANCE ->
                new DraftOnboarding.Step(step, false, completenessService.deductionRooms(inputs), Optional.empty());
            case PAY, EMPLOYER_FUNDS, OTHER_INCOME_AND_PROPERTY ->
                new DraftOnboarding.Step(step, false, List.of(), Optional.of(NOT_MEASURED.get(step)));
        };
    }
}
