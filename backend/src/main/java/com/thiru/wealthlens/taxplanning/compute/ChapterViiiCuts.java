package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxengine.input.AgniveerCorpusFund;
import com.thiru.wealthlens.taxengine.input.DisabledDependantSupport;
import com.thiru.wealthlens.taxengine.input.Donation;
import com.thiru.wealthlens.taxengine.input.EducationLoanInterest;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.HealthPayment;
import com.thiru.wealthlens.taxengine.input.PurposeLoanInterest;
import com.thiru.wealthlens.taxengine.input.SpecifiedDiseaseTreatment;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.workspace.ChapterViiiEvidence;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The other Chapter VIII entries cut to what is held (W-119b1 C3a, C4a): each entry's amount replaced by the amount
 * evidenced for it, matched by position, through {@code EnteredFacts}' own {@code with…} methods. Nothing recorded for a
 * family holds every entry of it to nil; a donation held to nil is left out, since a donation is more than nil.
 */
final class ChapterViiiCuts {

    private static final Set<EvidenceKind> KINDS = Set.of(EvidenceKind.HEALTH_PAYMENT, EvidenceKind.DONATION,
            EvidenceKind.EDUCATION_LOAN_INTEREST, EvidenceKind.PURPOSE_LOAN_INTEREST, EvidenceKind.DEPENDANT_SUPPORT,
            EvidenceKind.SPECIFIED_DISEASE_TREATMENT, EvidenceKind.AGNIVEER_SUBSCRIPTION);

    private ChapterViiiCuts() {
    }

    static boolean isChapterViii(EvidenceKind kind) {
        return KINDS.contains(kind);
    }

    /** Adds each family the entries hold something of. */
    static void kindsEntered(EnteredFacts entered, Set<EvidenceKind> kinds) {
        addIf(kinds, !entered.healthPayments().isEmpty(), EvidenceKind.HEALTH_PAYMENT);
        addIf(kinds, !entered.donations().isEmpty(), EvidenceKind.DONATION);
        addIf(kinds, !entered.educationLoanInterest().isEmpty(), EvidenceKind.EDUCATION_LOAN_INTEREST);
        addIf(kinds, !entered.purposeLoanInterest().isEmpty(), EvidenceKind.PURPOSE_LOAN_INTEREST);
        addIf(kinds, !entered.disabledDependantSupport().isEmpty(), EvidenceKind.DEPENDANT_SUPPORT);
        addIf(kinds, !entered.specifiedDiseaseTreatment().isEmpty(), EvidenceKind.SPECIFIED_DISEASE_TREATMENT);
        addIf(kinds, entered.agniveerCorpusFund().isPresent(), EvidenceKind.AGNIVEER_SUBSCRIPTION);
    }

    private static void addIf(Set<EvidenceKind> kinds, boolean entered, EvidenceKind kind) {
        if (entered) {
            kinds.add(kind);
        }
    }

    /** Every family cut to what is held. */
    static EnteredFacts cut(EnteredFacts facts, ChapterViiiEvidence held) {
        EnteredFacts cut = facts;
        for (EvidenceKind kind : KINDS) {
            cut = cut(cut, held, kind);
        }
        return cut;
    }

    /** One family cut to nothing held, the rest as they are. */
    static EnteredFacts nothingHeldFor(EnteredFacts facts, EvidenceKind kind) {
        return cut(facts, ChapterViiiEvidence.NONE, kind);
    }

    private static EnteredFacts cut(EnteredFacts facts, ChapterViiiEvidence held, EvidenceKind kind) {
        return switch (kind) {
            case HEALTH_PAYMENT -> facts.withHealthPayments(each(facts.healthPayments(), held.healthPayments(),
                    (payment, amount) -> new HealthPayment(payment.kind(), payment.coversAssessee(), payment.familyMembers(),
                            amount, payment.paidInCash(), payment.relevantTaxYears())));
            case DONATION -> facts.withDonations(donations(facts.donations(), held.donations()));
            case EDUCATION_LOAN_INTEREST -> facts.withEducationLoanInterest(each(facts.educationLoanInterest(),
                    held.educationLoanInterest(), (loan, amount) -> new EducationLoanInterest(amount, loan.initialTaxYear())));
            case PURPOSE_LOAN_INTEREST -> facts.withPurposeLoanInterest(each(facts.purposeLoanInterest(),
                    held.purposeLoanInterest(), (loan, amount) -> new PurposeLoanInterest(loan.purpose(), amount,
                            loan.sanctionedOn(), loan.amountSanctioned(), loan.propertyValue(), loan.stampDutyValue(),
                            loan.ownedResidentialHouseOnSanction())));
            case DEPENDANT_SUPPORT -> facts.withDisabledDependantSupport(each(facts.disabledDependantSupport(),
                    held.disabledDependantSupport(), (support, amount) -> new DisabledDependantSupport(
                            support.familyMember(), amount, support.dependantClaimsSection154())));
            case SPECIFIED_DISEASE_TREATMENT -> facts.withSpecifiedDiseaseTreatment(each(facts.specifiedDiseaseTreatment(),
                    held.specifiedDiseaseTreatment(), (treatment, amount) -> new SpecifiedDiseaseTreatment(
                            treatment.forAssessee(), treatment.familyMember(), amount, Money.min(treatment.reimbursed(), amount))));
            case AGNIVEER_SUBSCRIPTION -> facts.withAgniveerCorpusFund(facts.agniveerCorpusFund().map(fund ->
                    new AgniveerCorpusFund(held.agniveerOwnSubscription().orElse(Money.ZERO), fund.governmentContribution())));
            default -> throw new IllegalArgumentException(kind + " is not a Chapter VIII family W-119b1 cuts");
        };
    }

    /** Each donation at the amount held for it; one held to nil is left out, since a donation is more than nil. */
    private static List<Donation> donations(List<Donation> entries, List<Money> held) {
        List<Donation> cut = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Money amount = held.isEmpty() ? Money.ZERO : held.get(i);
            Donation donation = entries.get(i);
            if (amount.isGreaterThan(Money.ZERO)) {
                cut.add(new Donation(donation.clause(), amount, donation.paidInCash(), donation.reportedByDonee()));
            }
        }
        return cut;
    }

    /** Each entry rebuilt with the amount held for it by position; none held is nil. */
    private static <T> List<T> each(List<T> entries, List<Money> held, BiFunction<T, Money, T> rebuilt) {
        List<T> cut = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            cut.add(rebuilt.apply(entries.get(i), held.isEmpty() ? Money.ZERO : held.get(i)));
        }
        return cut;
    }

    /** What a family's entries came to beyond what is held for them. */
    static Money notEvidenced(EnteredFacts entered, ChapterViiiEvidence held, EvidenceKind kind) {
        return switch (kind) {
            case HEALTH_PAYMENT -> gap(entered.healthPayments(), HealthPayment::amount, held.healthPayments());
            case DONATION -> gap(entered.donations(), Donation::amount, held.donations());
            case EDUCATION_LOAN_INTEREST -> gap(entered.educationLoanInterest(), EducationLoanInterest::interestPaid,
                    held.educationLoanInterest());
            case PURPOSE_LOAN_INTEREST -> gap(entered.purposeLoanInterest(), PurposeLoanInterest::interestPayable,
                    held.purposeLoanInterest());
            case DEPENDANT_SUPPORT -> gap(entered.disabledDependantSupport(), DisabledDependantSupport::spentOrDeposited,
                    held.disabledDependantSupport());
            case SPECIFIED_DISEASE_TREATMENT -> gap(entered.specifiedDiseaseTreatment(), SpecifiedDiseaseTreatment::paid,
                    held.specifiedDiseaseTreatment());
            case AGNIVEER_SUBSCRIPTION -> entered.agniveerCorpusFund().map(AgniveerCorpusFund::ownSubscription)
                    .orElse(Money.ZERO).minus(held.agniveerOwnSubscription().orElse(Money.ZERO));
            default -> throw new IllegalArgumentException(kind + " is not a Chapter VIII family W-119b1 cuts");
        };
    }

    private static <T> Money gap(List<T> entries, Function<T, Money> amount, List<Money> held) {
        Money entered = entries.stream().map(amount).reduce(Money.ZERO, Money::plus);
        return entered.minus(held.stream().reduce(Money.ZERO, Money::plus));
    }
}
