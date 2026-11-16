package com.thiru.wealthlens.taxplanning.documents.ingest;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/** A value read from a form: an amount, a yes or no, or words ({@code DR 05} M4; D-727 C3a). */
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

    /** Words, such as an employer's name or a TAN. */
    record TextRead(String text) implements ExtractedValue {

        public TextRead {
            Checks.text(text, "Words read cannot be blank");
        }
    }
}
