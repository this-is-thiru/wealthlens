package com.thiru.wealthlens.taxplanning.workspace;

import com.thiru.wealthlens.taxengine.input.Declaration;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.FilingFacts;
import com.thiru.wealthlens.taxengine.input.LedgerSnapshot;
import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A draft's document: the year's entries, the engine's own model, and what has been paid (D-441 C1a, D-442 C1a).
 *
 * @param entered the entries
 * @param filing the due date, which may not be known while drafting, and tax already paid
 * @param brought what earlier years carried in, as the user's last return states it (W-234, D-445)
 * @param thirdParties the landlords and lenders this draft relies on ({@code DR 15} F3; W-239a, D-506); none when absent,
 *     which every draft stored before W-239a is, since none could name one
 * @param evidence what the person can show for their claims (W-119b); never handed to the engine, and nothing when
 *     absent, which every draft stored before W-119b is
 * @param onboarding which groups of questions the person has answered (W-122a); never handed to the engine, and none when
 *     absent, which every draft stored before W-122a is
 */
public record DraftInputs(EnteredFacts entered, Filing filing, LedgerSnapshot brought, ThirdParties thirdParties,
        EvidenceHeld evidence, Onboarding onboarding) {

    public DraftInputs {
        thirdParties = thirdParties == null ? ThirdParties.NONE : thirdParties;
        evidence = evidence == null ? EvidenceHeld.NONE : evidence;
        onboarding = onboarding == null ? Onboarding.NONE : onboarding;
    }

    static DraftInputs blank() {
        return new DraftInputs(EnteredFacts.none(), new Filing(null, null, List.of(), null, List.of(), Map.of(), null, null), LedgerSnapshot.empty(),
                ThirdParties.NONE, EvidenceHeld.NONE, Onboarding.NONE);
    }

    /** A stored draft's inputs, read for a job that is not editing it (retention, W-239a). */
    public static DraftInputs of(FilingDraft draft) {
        return DraftInputsMapper.read(draft.getInputsJson());
    }

    /**
     * Ids of the profile's landlords and lenders, as the draft names them. Retention keeps a named record to two years past
     * the last tax year naming it ({@code DR 14} O5).
     */
    public record ThirdParties(List<String> landlordIds, List<String> lenderIds) {

        static final ThirdParties NONE = new ThirdParties(List.of(), List.of());

        public ThirdParties {
            landlordIds = landlordIds == null ? List.of() : List.copyOf(landlordIds);
            lenderIds = lenderIds == null ? List.of() : List.copyOf(lenderIds);
        }
    }

    /**
     * The steps the person has marked answered, "nothing to enter" included (W-122a C1a); any may be marked or opened
     * again at any time (C4a). Set by the person, never inferred from the entries.
     */
    public record Onboarding(Set<OnboardingStep> answered) {

        static final Onboarding NONE = new Onboarding(Set.of());

        public Onboarding {
            answered = answered == null || answered.isEmpty() ? Set.of()
                    : Collections.unmodifiableSet(EnumSet.copyOf(answered));
        }
    }

    /**
     * The engine's {@link FilingFacts} once the due date is stated; {@code dueDate} and {@code furnishedOn} may be null until
     * then. {@code declarations} are the person's answers to the year's questions (W-090a2); none when absent, which every
     * draft stored before W-090a2 is, since none could answer one. {@code refundGrantedOn} is null until a refund is granted,
     * which every draft stored before W-112b is. {@code ofWhichByEmployer} is the part of {@code taxAtSource} the employer
     * deducts for the full year, null where not stated, which every draft stored before W-113 is: no projection of the
     * employer's deduction is built (F12), so the forecast shows what the person states (D-664 C1a). The engine reads only
     * the total.
     */
    public record Filing(LocalDate dueDate, LocalDate furnishedOn, List<FilingFacts.Payment> advanceTaxPaid, Money taxAtSource,
            List<FilingFacts.Payment> selfAssessmentPaid, Map<DeclarationCode, Declaration> declarations,
            LocalDate refundGrantedOn, Money ofWhichByEmployer) {

        public Filing {
            declarations = declarations == null ? Map.of() : Map.copyOf(declarations);
            if (ofWhichByEmployer != null && (ofWhichByEmployer.isLessThan(Money.ZERO)
                    || ofWhichByEmployer.isGreaterThan(taxAtSource == null ? Money.ZERO : taxAtSource))) {
                throw new IllegalArgumentException("/filing/ofWhichByEmployer must be nil or more and no more than"
                        + " /filing/taxAtSource, of which it is a part");
            }
        }

        /** The employer's part of tax at source, where the person has stated it. */
        public Optional<Money> byEmployer() {
            return Optional.ofNullable(ofWhichByEmployer);
        }

        /** @throws IllegalArgumentException if no due date is stated */
        public FilingFacts toFilingFacts() {
            if (dueDate == null) {
                throw new IllegalArgumentException("A computation needs the draft's /filing/dueDate");
            }
            return new FilingFacts(dueDate, Optional.ofNullable(furnishedOn), advanceTaxPaid,
                    taxAtSource == null ? Money.ZERO : taxAtSource, selfAssessmentPaid, declarations,
                    Optional.ofNullable(refundGrantedOn));
        }
    }
}
