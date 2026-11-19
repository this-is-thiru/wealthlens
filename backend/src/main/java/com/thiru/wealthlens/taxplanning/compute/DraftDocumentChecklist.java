package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * For each claim a draft's computation makes, every document the claim catalogue says it rests on, whether the draft
 * shows it held, and — where not — what closes it and whom to ask (PRD F25; {@code DR 12} C3.2, {@code DR 22} C7;
 * W-120): what the person can show, never a judgement of risk. Computed on every request and stored nowhere.
 *
 * @param asOf the day it was computed on
 * @param regime the regime computed: the draft's election, or the year's default where none is made
 * @param revision the draft revision it read
 * @param claims each claim the computation makes, in the catalogue's order
 * @param notIncluded what this checklist does not yet say
 */
public record DraftDocumentChecklist(LocalDate asOf, String regime, int revision, List<Claim> claims,
        List<String> notIncluded) {

    public DraftDocumentChecklist {
        claims = List.copyOf(claims);
        notIncluded = List.copyOf(notIncluded);
    }

    /**
     * @param rule the rule the claim is catalogued under
     * @param plainName its plain name
     * @param documents what it rests on, in the catalogue's order
     */
    public record Claim(String rule, String plainName, List<Document> documents) {

        public Claim {
            documents = List.copyOf(documents);
        }
    }

    /**
     * @param name the document
     * @param closedBy what the person must hold for it to be complete
     * @param from who provides it, as the catalogue names them
     * @param askFor what to ask them for, where it is someone other than the person
     * @param state whether it is held
     * @param outstanding what is not yet evidenced, where the draft measures it
     * @param says what the state means and what would close it, in the year's words
     */
    public record Document(String name, String closedBy, String from, Optional<String> askFor, State state,
            Optional<Outstanding> outstanding, String says) {
    }

    /** What a document's absence leaves unevidenced: rupees, or months of rent. */
    public record Outstanding(Optional<Money> amount, Optional<Long> months) {
    }

    /** Whether a document is held, as the draft and the profile show it. */
    public enum State {
        /** The draft shows it held in full. */
        HELD,
        /** The draft shows it missing, or held for less than the claim. */
        NOT_HELD,
        /** The law does not ask for it on these facts: the engine raised nothing. */
        NOT_ASKED,
        /** The product keeps no record of it, so the person checks it. */
        NOT_RECORDED
    }
}
