package com.thiru.wealthlens.taxplanning.documents.ingest;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;

/** A value read from a form: an amount, a yes or no, a period or words ({@code DR 05} M4; D-727 C3a; W-128b). */
public sealed interface ExtractedValue {

    /** An amount, as the form states it. */
    record MoneyRead(Money amount) implements ExtractedValue {

        public MoneyRead {
            Checks.present(amount, "An amount read needs its amount");
        }
    }

    /** A yes or a no, as a tick or a word on the form. */
    record FlagRead(boolean value) implements ExtractedValue {
    }

    /** Two dates the form prints as one period, such as Form No. 130's period with the employer (W-128b). */
    record PeriodRead(LocalDate from, LocalDate to) implements ExtractedValue {

        public PeriodRead {
            Checks.present(from, "A period read needs its first day");
            Checks.present(to, "A period read needs its last day");
        }
    }

    /** Words, such as an employer's name or a TAN. */
    record TextRead(String text) implements ExtractedValue {

        public TextRead {
            Checks.text(text, "Words read cannot be blank");
        }
    }
}
