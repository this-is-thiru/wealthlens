package com.thiru.wealthlens.taxplanning.workspace;

import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * What a person can show for the Chapter VIII entries beyond Schedule XV (W-119b1 C1a, C2a): one evidenced amount per
 * entry, matched by position as {@link EvidenceHeld}'s lists are. A product record the engine never reads; the integrity
 * panel cuts each entry to it.
 *
 * @param healthPayments for each health payment, the amount evidenced
 * @param donations for each donation, the amount evidenced
 * @param educationLoanInterest for each education loan, the interest evidenced
 * @param purposeLoanInterest for each house or vehicle loan, the interest evidenced
 * @param disabledDependantSupport for each dependant with disability, the spending evidenced
 * @param specifiedDiseaseTreatment for each specified-disease treatment, the amount paid evidenced
 * @param agniveerOwnSubscription the person's own subscription to the Agniveer fund evidenced; the government's part is
 *     the employer's to show
 */
public record ChapterViiiEvidence(List<Money> healthPayments, List<Money> donations, List<Money> educationLoanInterest,
        List<Money> purposeLoanInterest, List<Money> disabledDependantSupport, List<Money> specifiedDiseaseTreatment,
        Optional<Money> agniveerOwnSubscription) {

    /** Nothing recorded — what every draft stored before W-119b1 reads as. */
    public static final ChapterViiiEvidence NONE = new ChapterViiiEvidence(List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), Optional.empty());

    public ChapterViiiEvidence {
        healthPayments = healthPayments == null ? List.of() : List.copyOf(healthPayments);
        donations = donations == null ? List.of() : List.copyOf(donations);
        educationLoanInterest = educationLoanInterest == null ? List.of() : List.copyOf(educationLoanInterest);
        purposeLoanInterest = purposeLoanInterest == null ? List.of() : List.copyOf(purposeLoanInterest);
        disabledDependantSupport = disabledDependantSupport == null ? List.of() : List.copyOf(disabledDependantSupport);
        specifiedDiseaseTreatment = specifiedDiseaseTreatment == null ? List.of() : List.copyOf(specifiedDiseaseTreatment);
        agniveerOwnSubscription = agniveerOwnSubscription == null ? Optional.empty() : agniveerOwnSubscription;
    }

    /**
     * Refuses evidence that does not fit its entries: a list of another length, or an amount beyond what the entry states.
     *
     * @throws IllegalArgumentException naming the field and the bound
     */
    void requireFits(EnteredFacts entered) {
        fits("healthPayments", healthPayments, entered.healthPayments(), payment -> payment.amount());
        fits("donations", donations, entered.donations(), donation -> donation.amount());
        fits("educationLoanInterest", educationLoanInterest, entered.educationLoanInterest(), loan -> loan.interestPaid());
        fits("purposeLoanInterest", purposeLoanInterest, entered.purposeLoanInterest(), loan -> loan.interestPayable());
        fits("disabledDependantSupport", disabledDependantSupport, entered.disabledDependantSupport(),
                support -> support.spentOrDeposited());
        fits("specifiedDiseaseTreatment", specifiedDiseaseTreatment, entered.specifiedDiseaseTreatment(),
                treatment -> treatment.paid());
        agniveerOwnSubscription.ifPresent(evidenced -> EvidenceHeld.requireWithin(
                "/evidence/chapterViii/agniveerOwnSubscription", evidenced,
                entered.agniveerCorpusFund().orElseThrow(() -> new IllegalArgumentException(
                        "/evidence/chapterViii/agniveerOwnSubscription is for an Agniveer fund /entered/agniveerCorpusFund"
                                + " does not have")).ownSubscription(),
                "the own subscription entered"));
    }

    private static <T> void fits(String field, List<Money> evidenced, List<T> entries, Function<T, Money> amount) {
        String path = "/evidence/chapterViii/" + field;
        EvidenceHeld.requireMatched(path, evidenced.size(), "/entered/" + field, entries.size());
        for (int i = 0; i < evidenced.size(); i++) {
            EvidenceHeld.requireWithin(path + "/" + i, evidenced.get(i), amount.apply(entries.get(i)),
                    "what entry " + i + " states");
        }
    }
}
