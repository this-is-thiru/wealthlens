package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.assemble.EngineInputsAssembler;
import com.thiru.wealthlens.taxplanning.workspace.DraftService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A draft's completeness, computed on every request and stored nowhere (W-115 C7a, D-670): the deduction rooms the
 * calendar prices and the rent room, each measured by the engine on its own run, ranked by the tax each would take off.
 * An item worth nothing — a regime that allows the row nothing — is dropped (C6a).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DraftCompletenessService {

    /** F33.3: the top five surfaced (PRD F33.3). */
    private static final int SURFACED = 5;

    /** Stated with every completeness beyond what every computation states (D-670 C1a, C2a, C4a, C6a). */
    private static final List<String> NOT_INCLUDED = List.of(
            "Income the draft leaves out, which would raise the tax: not measured until department data (AIS, Form 26AS)"
                    + " is read (W-324)",
            "Interest on a loan for an owned house, leave travel and education-loan interest: none has a bound at the"
                    + " draft's own facts (W-115 C1a)",
            "Where no home is entered, rent is measured for one home occupied the whole year (W-115 C4a)",
            "Items overlap: entering all of them need not save the sum of their worths");

    private final DraftService draftService;
    private final EngineInputsAssembler assembler;
    private final TaxCalculator taxCalculator;
    private final Clock clock;

    /**
     * @throws IllegalArgumentException if the draft is not this account's, or elects a regime the year does not have
     */
    public DraftCompleteness completeness(String email, String draftId) {
        DraftService.OwnedDraft owned = draftService.ownedDraft(email, draftId);
        LocalDate today = LocalDate.now(clock);
        EngineInputsAssembler.Assembled assembled = assembler.assemble(email, draftId,
                DraftForecastService.regimeOf(owned.draft(), taxCalculator), today);
        List<DraftCompleteness.Item> ranked = items(assembled.inputs());
        List<DraftCompleteness.Item> top = ranked.subList(0, Math.min(SURFACED, ranked.size()));
        return new DraftCompleteness(today, assembled.inputs().regime().value(), assembled.draft().getCurrentRevision(), top,
                ranked.subList(top.size(), ranked.size()),
                top.stream().map(DraftCompleteness.Item::worth).reduce(Money.ZERO, Money::plus),
                Stream.concat(DraftComputationService.NOT_INCLUDED.stream(), NOT_INCLUDED.stream()).toList());
    }

    /** Every item worth something, largest first, ties by rule; separate from {@link #completeness} so it is tested on the engine alone. */
    List<DraftCompleteness.Item> items(EngineInputs inputs) {
        List<DraftCompleteness.Item> items = new ArrayList<>();
        taxCalculator.priceDeductionRoom(inputs).forEach(room -> items.add(new DraftCompleteness.Item(room.rule(),
                DraftCompleteness.Direction.REDUCES, room.counted(), room.room(), Optional.empty(), Optional.empty(),
                room.taxSaved())));
        taxCalculator.priceRentRoom(inputs).ifPresent(rent -> items.add(new DraftCompleteness.Item(rent.rule(),
                DraftCompleteness.Direction.REDUCES, rent.counted(), rent.room(), Optional.of(rent.rentNeeded()),
                rent.location(), rent.taxSaved())));
        return items.stream()
                .filter(item -> item.worth().isGreaterThan(Money.ZERO))
                .sorted(Comparator.comparing(DraftCompleteness.Item::worth, Comparator.<Money>reverseOrder())
                        .thenComparing(DraftCompleteness.Item::rule))
                .toList();
    }
}
