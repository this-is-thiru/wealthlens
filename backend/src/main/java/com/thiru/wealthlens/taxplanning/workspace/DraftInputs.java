package com.thiru.wealthlens.taxplanning.workspace;

import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.FilingFacts;
import com.thiru.wealthlens.taxengine.input.LedgerSnapshot;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * A draft's document: the year's entries, the engine's own model, and what has been paid (D-441 C1a, D-442 C1a).
 *
 * @param entered the entries
 * @param filing the due date, which may not be known while drafting, and tax already paid
 * @param brought what earlier years carried in, as the user's last return states it (W-234, D-445)
 */
public record DraftInputs(EnteredFacts entered, Filing filing, LedgerSnapshot brought) {

    static DraftInputs blank() {
        return new DraftInputs(EnteredFacts.none(), new Filing(null, null, List.of(), null, List.of()), LedgerSnapshot.empty());
    }

    /** The engine's {@link FilingFacts} once the due date is stated; {@code dueDate} and {@code furnishedOn} may be null until then. */
    public record Filing(LocalDate dueDate, LocalDate furnishedOn, List<FilingFacts.Payment> advanceTaxPaid, Money taxAtSource,
            List<FilingFacts.Payment> selfAssessmentPaid) {

        /** @throws IllegalArgumentException if no due date is stated */
        public FilingFacts toFilingFacts() {
            if (dueDate == null) {
                throw new IllegalArgumentException("A computation needs the draft's /filing/dueDate");
            }
            return new FilingFacts(dueDate, Optional.ofNullable(furnishedOn), advanceTaxPaid,
                    taxAtSource == null ? Money.ZERO : taxAtSource, selfAssessmentPaid);
        }
    }
}
