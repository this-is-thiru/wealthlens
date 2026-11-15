package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxengine.api.ClaimPlaybook;
import com.thiru.wealthlens.taxengine.api.PricedClaim;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxplanning.assemble.EngineInputsAssembler;
import com.thiru.wealthlens.taxplanning.workspace.DraftService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A draft's claims, computed on every request and stored nowhere (W-118): the engine states and measures each claim;
 * this adds the draft's own dates to its steps (C5a).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DraftClaimsService {

    /** Stated with every draft's claims beyond what every computation states (W-118 C3a, C4a; owner, session 99). */
    private static final List<String> NOT_INCLUDED = List.of(
            "No Form 16 is compared yet: each claim says what its absence from Form 16 would mean (W-118 C4a)",
            "A claim's worth is measured only where the computation allows it; one not made is priced by completeness (F33)",
            "A reported donation is listed as not claimed, without a worth: its catalogue entry names the reporting bar,"
                    + " not the rules that deduct it (W-117e)",
            "Claims overlap: each worth is measured with every other claim as it stands, so worths need not add up");

    private final DraftService draftService;
    private final EngineInputsAssembler assembler;
    private final TaxCalculator taxCalculator;
    private final Clock clock;

    /**
     * @throws IllegalArgumentException if the draft is not this account's, states no due date, or elects a regime the
     *     year does not have
     */
    public DraftClaims claims(String email, String draftId) {
        DraftService.OwnedDraft owned = draftService.ownedDraft(email, draftId);
        LocalDate today = LocalDate.now(clock);
        EngineInputsAssembler.Assembled assembled = assembler.assemble(email, draftId,
                DraftForecastService.regimeOf(owned.draft(), taxCalculator), today);
        EngineInputs inputs = assembled.inputs();
        List<DraftClaims.Claim> claimed = claimed(inputs);
        return new DraftClaims(today, inputs.regime().value(), assembled.draft().getCurrentRevision(), claimed,
                notClaimed(inputs, claimed), Stream.concat(DraftComputationService.NOT_INCLUDED.stream(),
                        NOT_INCLUDED.stream()).toList());
    }

    /** The claims the computation allows, measured; separate from {@link #claims} so it is tested on the engine alone. */
    List<DraftClaims.Claim> claimed(EngineInputs inputs) {
        return taxCalculator.priceClaims(inputs).stream()
                .map(priced -> claim(priced.playbook(), inputs, Optional.of(priced)))
                .toList();
    }

    /** The year's other claims, by rule id, unmeasured. */
    List<DraftClaims.Claim> notClaimed(EngineInputs inputs, List<DraftClaims.Claim> claimed) {
        Set<String> made = claimed.stream().map(claim -> claim.playbook().rule()).collect(Collectors.toSet());
        return taxCalculator.claimPlaybooks(inputs.taxYear()).stream()
                .filter(playbook -> !made.contains(playbook.rule()))
                .map(playbook -> claim(playbook, inputs, Optional.empty()))
                .toList();
    }

    private static DraftClaims.Claim claim(ClaimPlaybook playbook, EngineInputs inputs, Optional<PricedClaim> priced) {
        Map<String, LocalDate> dated = Map.of("TAX_YEAR_END", inputs.temporal().taxYearEnd(),
                "RETURN_DUE", inputs.filing().dueDate());
        List<DraftClaims.StepDate> dates = playbook.guidance().steps().stream()
                .filter(step -> dated.containsKey(step.by()))
                .map(step -> new DraftClaims.StepDate(step.order(), dated.get(step.by())))
                .toList();
        return new DraftClaims.Claim(playbook, dates, priced.map(PricedClaim::allowed), priced.map(PricedClaim::worth));
    }
}
