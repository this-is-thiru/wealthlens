package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxengine.api.ComputedTax;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.FilingFacts;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
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
 * A draft's calendar, computed on every request and stored nowhere (W-114 C5a, D-665; up to nine runs with W-114a's two). Each cost is the draft computed
 * again with one action taken, both runs as of the later of the return's due date and today: measured as of today, an
 * instalment still to come would cost nothing, since its interest has not accrued yet (the W-114 probe).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DraftCalendarService {

    /** Stated with every calendar beyond what every computation states (D-665 C1a). */
    private static final List<String> NOT_INCLUDED = List.of(
            "The last day to pay for a health-insurance or own-pension deduction (W-114c)",
            "The end of a leave-travel block, which carries an unused journey into the next block (W-114a C4a)",
            "The employer's cut-off for proofs, the window for a revised return and the regime election (W-114b)",
            "Entries are separate actions: their costs overlap and do not add up");

    private final DraftService draftService;
    private final EngineInputsAssembler assembler;
    private final TaxCalculator taxCalculator;
    private final Clock clock;

    /**
     * @throws IllegalArgumentException if the draft is not this account's, states no due date, or elects a regime the
     *     year does not have
     */
    public DraftCalendar calendar(String email, String draftId) {
        DraftService.OwnedDraft owned = draftService.ownedDraft(email, draftId);
        LocalDate today = LocalDate.now(clock);
        EngineInputsAssembler.Assembled assembled = assembler.assemble(email, draftId,
                DraftForecastService.regimeOf(owned.draft(), taxCalculator), today);
        EngineInputs inputs = assembled.inputs();
        LocalDate measuredAsOf = latest(inputs.filing().dueDate(), today);
        return new DraftCalendar(today, measuredAsOf, inputs.regime().value(), assembled.draft().getCurrentRevision(),
                entries(inputs, measuredAsOf), Stream.concat(DraftComputationService.NOT_INCLUDED.stream(),
                        NOT_INCLUDED.stream()).toList());
    }

    /** The entries, each cost measured as of {@code measuredAsOf}; separate from {@link #calendar} so it is tested on the engine alone. */
    List<DraftCalendar.Entry> entries(EngineInputs inputs, LocalDate measuredAsOf) {
        FilingFacts filing = inputs.filing();
        ComputedTax base = taxCalculator.compute(at(inputs, measuredAsOf, filing));
        Money baseOwed = owed(base);
        List<DraftCalendar.Entry> entries = new ArrayList<>();
        for (ComputedTax.AdvanceTax.Instalment instalment : base.advanceTax().instalments()) {
            entries.add(new DraftCalendar.Entry(instalment.dueOn(), DraftCalendar.Kind.ADVANCE_TAX_INSTALMENT,
                    "ADV-INSTALMENTS", Optional.of(instalment.stillOwed()), Optional.of(instalment.paidByThen()),
                    Optional.empty(), costOfNotPaying(inputs, measuredAsOf, instalment.dueOn(), instalment.stillOwed(), baseOwed)));
        }
        base.advanceTax().reliefPaidBy().ifPresent(paidBy -> entries.add(reliefEntry(inputs, measuredAsOf, base, paidBy)));
        deductionEntries(inputs).forEach(entries::add);
        entries.add(returnEntry(inputs, measuredAsOf, base));
        entries.sort(Comparator.comparing(DraftCalendar.Entry::on));
        return entries;
    }

    /** Each deduction with room whose filling would save tax, on the year's last day (W-114a C3a); none in a regime that allows none. */
    private List<DraftCalendar.Entry> deductionEntries(EngineInputs inputs) {
        return taxCalculator.priceDeductionRoom(inputs).stream()
                .filter(room -> room.taxSaved().isGreaterThan(Money.ZERO))
                .map(room -> new DraftCalendar.Entry(inputs.temporal().taxYearEnd(), DraftCalendar.Kind.DEDUCTION_PAID_BY,
                        room.rule(), Optional.of(room.room()), Optional.of(room.counted()), Optional.empty(), room.taxSaved()))
                .toList();
    }

    /** s.425(4): the advance tax still owed at the last instalment, paid by the relief's day. */
    private DraftCalendar.Entry reliefEntry(EngineInputs inputs, LocalDate measuredAsOf, ComputedTax base, LocalDate paidBy) {
        Money stillOwed = base.advanceTax().instalments().stream()
                .max(Comparator.comparing(ComputedTax.AdvanceTax.Instalment::dueOn))
                .map(ComputedTax.AdvanceTax.Instalment::stillOwed).orElse(Money.ZERO);
        return new DraftCalendar.Entry(paidBy, DraftCalendar.Kind.DEFERMENT_RELIEF_PAID_BY, "INT-DEFERMENT",
                Optional.of(stillOwed), Optional.of(inputs.filing().advanceTaxPaidBy(paidBy)), Optional.empty(),
                costOfNotPaying(inputs, measuredAsOf, paidBy, stillOwed, owed(base)));
    }

    /**
     * The return: furnished, what furnishing it when it was cost against furnishing it on the due date; not furnished, what
     * furnishing it a day late would cost, or, once the due date has passed, what has run since.
     */
    private DraftCalendar.Entry returnEntry(EngineInputs inputs, LocalDate measuredAsOf, ComputedTax base) {
        FilingFacts filing = inputs.filing();
        LocalDate due = filing.dueDate();
        Money onTime = owed(taxCalculator.compute(at(inputs, due, furnishedOn(filing, due))));
        Money cost;
        if (filing.furnishedOn().isPresent() || measuredAsOf.isAfter(due)) {
            cost = owed(base).minus(onTime);
        } else {
            LocalDate dayLate = due.plusDays(1);
            cost = owed(taxCalculator.compute(at(inputs, dayLate, furnishedOn(filing, dayLate)))).minus(onTime);
        }
        Optional<Money> payable = Optional.of(base.finalAmount()).filter(amount -> amount.isGreaterThan(Money.ZERO));
        return new DraftCalendar.Entry(due, DraftCalendar.Kind.RETURN_DUE, "INT-LATE-RETURN", payable, Optional.empty(),
                filing.furnishedOn(), cost.floorAtZero());
    }

    /** The interest and fees {@code amount} paid on {@code paidOn} would have saved; nil, with no run, where nothing is owed. */
    private Money costOfNotPaying(EngineInputs inputs, LocalDate measuredAsOf, LocalDate paidOn, Money amount, Money baseOwed) {
        if (!amount.isGreaterThan(Money.ZERO)) {
            return Money.ZERO;
        }
        FilingFacts filing = inputs.filing();
        List<FilingFacts.Payment> paid = new ArrayList<>(filing.advanceTaxPaid());
        paid.add(new FilingFacts.Payment(paidOn, amount));
        FilingFacts withPayment = new FilingFacts(filing.dueDate(), filing.furnishedOn(), paid, filing.taxAtSource(),
                filing.selfAssessmentPaid(), filing.declarations(), filing.refundGrantedOn());
        return baseOwed.minus(owed(taxCalculator.compute(at(inputs, measuredAsOf, withPayment)))).floorAtZero();
    }

    private static FilingFacts furnishedOn(FilingFacts filing, LocalDate on) {
        return new FilingFacts(filing.dueDate(), Optional.of(on), filing.advanceTaxPaid(), filing.taxAtSource(),
                filing.selfAssessmentPaid(), filing.declarations(), filing.refundGrantedOn());
    }

    /** {@code inputs} as of {@code asOf} with {@code filing}; nothing else moves, the portfolio included. */
    private static EngineInputs at(EngineInputs inputs, LocalDate asOf, FilingFacts filing) {
        return new EngineInputs(inputs.taxYear(), inputs.regime(), TemporalContext.forTaxYear(inputs.taxYear(), asOf),
                inputs.entered(), inputs.assessee(), inputs.family(), inputs.ledgers(), inputs.elections(),
                inputs.capitalGains(), filing);
    }

    private static Money owed(ComputedTax tax) {
        return tax.interestAndFees().stream().map(ComputedTax.Owed::amount).reduce(Money.ZERO, Money::plus);
    }

    private static LocalDate latest(LocalDate one, LocalDate other) {
        return one.isAfter(other) ? one : other;
    }
}
