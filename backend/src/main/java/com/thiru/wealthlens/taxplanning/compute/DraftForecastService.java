package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxengine.api.ComputedTax;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.assemble.EngineInputsAssembler;
import com.thiru.wealthlens.taxplanning.workspace.DraftService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A draft's forecast, computed on every request and stored nowhere (W-113, D-664 C2a): it moves with each input and with
 * the portfolio as it stands, so a stored copy would only be one more thing to keep fresh and to erase.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DraftForecastService {

    /** Stated with every forecast beyond what every computation states (D-664 C1a, C4a). */
    private static final List<String> NOT_INCLUDED = List.of(
            "A projection of what the employer will deduct: the employer's part is the figure stated in the draft",
            "The three rules that most drive the result (W-113a)");

    private final DraftService draftService;
    private final EngineInputsAssembler assembler;
    private final TaxCalculator taxCalculator;
    private final Clock clock;

    /**
     * @throws IllegalArgumentException if the draft is not this account's, states no due date, or elects a regime the
     *     year does not have
     */
    public DraftForecast forecast(String email, String draftId) {
        DraftService.OwnedDraft owned = draftService.ownedDraft(email, draftId);
        String election = owned.draft().getRegimeElection();
        RegimeCode regime = election == null || election.isBlank()
                ? taxCalculator.defaultRegime(TaxYearId.of(owned.draft().getTaxYearStart()))
                : new RegimeCode(election);
        LocalDate asOf = LocalDate.now(clock);
        EngineInputsAssembler.Assembled assembled = assembler.assemble(email, draftId, regime, asOf);
        ComputedTax tax = taxCalculator.compute(assembled.inputs());
        Optional<Money> byEmployer = owned.inputs().filing().byEmployer();
        return forecastOf(tax, byEmployer, assembled.inputs().filing().dueDate(), asOf,
                assembled.draft().getCurrentRevision(), concat(DraftComputationService.NOT_INCLUDED, NOT_INCLUDED));
    }

    /** The figures from a computation; separate from {@link #forecast} so the arithmetic is tested without a pipeline. */
    static DraftForecast forecastOf(ComputedTax tax, Optional<Money> byEmployer, LocalDate dueDate, LocalDate asOf,
            int revision, List<String> notIncluded) {
        Money liability = tax.interestAndFees().stream().map(ComputedTax.Owed::amount).reduce(tax.bill(), Money::plus);
        Money otherCredits = tax.credits().minus(byEmployer.orElse(Money.ZERO));
        Money unrounded = liability.minus(byEmployer.orElse(Money.ZERO)).minus(otherCredits);
        Money net = tax.finalAmount();
        DraftForecast.Settlement settlement = net.isGreaterThan(Money.ZERO) ? DraftForecast.Settlement.PAYABLE_NOW
                : net.isLessThan(Money.ZERO) ? DraftForecast.Settlement.REFUND_AFTER_FILING : DraftForecast.Settlement.NIL;
        Optional<LocalDate> payableBy = settlement == DraftForecast.Settlement.PAYABLE_NOW
                ? Optional.of(nextInstalmentOwed(tax, asOf).orElse(dueDate))
                : Optional.empty();
        return new DraftForecast(liability, byEmployer, otherCredits, net.minus(unrounded), net, settlement, payableBy,
                tax.interestOnRefund(), tax.regime(), asOf, revision, notIncluded);
    }

    private static Optional<LocalDate> nextInstalmentOwed(ComputedTax tax, LocalDate asOf) {
        return tax.advanceTax().instalments().stream()
                .filter(instalment -> !instalment.dueOn().isBefore(asOf))
                .filter(instalment -> instalment.stillOwed().isGreaterThan(Money.ZERO))
                .map(ComputedTax.AdvanceTax.Instalment::dueOn)
                .min(LocalDate::compareTo);
    }

    private static List<String> concat(List<String> first, List<String> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }
}
