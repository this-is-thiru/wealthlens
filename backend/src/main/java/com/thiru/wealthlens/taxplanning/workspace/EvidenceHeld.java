package com.thiru.wealthlens.taxplanning.workspace;

import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.Journey;
import com.thiru.wealthlens.taxengine.input.QualifyingPayment;
import com.thiru.wealthlens.taxengine.input.Tenancy;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.List;
import java.util.Map;

/**
 * What a person can show for their claims ({@code DR 12} C3.2; W-119 C4a, W-119b): the product's record of documents
 * held, never a fact the engine reads, so nothing recorded here can change the tax (W-119 C6a). What the profile or the
 * engine already knows — a lender's particulars, the lender's certificate — is read from there and not recorded again
 * (W-119b C2a, C3a).
 *
 * <p>Each list is matched to the entries by position: the {@code i}th element is for the {@code i}th entry. A list is
 * empty where nothing is recorded, or as long as the entries it is for.
 *
 * @param receiptedMonths for each tenancy, the months of it for which a rent receipt is held (C1a)
 * @param journeyExpenditure for each leave-travel journey, the expenditure for which evidence is held (C5a)
 * @param qualifyingPayments for each Schedule XV payment code entered, the amount for which evidence is held (C4a)
 */
public record EvidenceHeld(List<Integer> receiptedMonths, List<Money> journeyExpenditure,
        Map<ComponentCode, Money> qualifyingPayments) {

    /** Nothing recorded — what every draft stored before W-119b reads as. */
    public static final EvidenceHeld NONE = new EvidenceHeld(List.of(), List.of(), Map.of());

    public EvidenceHeld {
        receiptedMonths = receiptedMonths == null ? List.of() : List.copyOf(receiptedMonths);
        journeyExpenditure = journeyExpenditure == null ? List.of() : List.copyOf(journeyExpenditure);
        qualifyingPayments = qualifyingPayments == null ? Map.of() : Map.copyOf(qualifyingPayments);
    }

    /**
     * Refuses evidence that does not fit the entries it is for: a list of another length, months beyond a tenancy's run
     * in the year, expenditure beyond what a journey incurred, or an amount for a payment code not entered or beyond what
     * was paid under it. Removing an entry therefore means removing its evidence in the same patch.
     *
     * @throws IllegalArgumentException naming the field and the bound
     */
    public void requireFits(EnteredFacts entered, TaxYearId taxYear) {
        List<Tenancy> tenancies = entered.tenancies();
        requireMatched("/evidence/receiptedMonths", receiptedMonths.size(), "/entered/tenancies", tenancies.size());
        for (int i = 0; i < receiptedMonths.size(); i++) {
            long months = tenancies.get(i).monthsOccupiedIn(taxYear);
            int receipted = receiptedMonths.get(i);
            if (receipted < 0 || receipted > months) {
                throw new IllegalArgumentException("/evidence/receiptedMonths/" + i + " must be from nil to the " + months
                        + " months tenancy " + i + " runs in the year, but was " + receipted);
            }
        }
        List<Journey> journeys = entered.leaveTravel();
        requireMatched("/evidence/journeyExpenditure", journeyExpenditure.size(), "/entered/leaveTravel", journeys.size());
        for (int i = 0; i < journeyExpenditure.size(); i++) {
            requireWithin("/evidence/journeyExpenditure/" + i, journeyExpenditure.get(i),
                    journeys.get(i).amountIncurred(), "what journey " + i + " incurred");
        }
        qualifyingPayments.forEach((code, amount) -> requireWithin("/evidence/qualifyingPayments/" + code, amount,
                paidUnder(entered.qualifyingPayments(), code), "what is entered as paid under " + code));
    }

    private static void requireMatched(String evidence, int evidenced, String entries, int entered) {
        if (evidenced != 0 && evidenced != entered) {
            throw new IllegalArgumentException(evidence + " holds " + evidenced + " but " + entries + " holds " + entered
                    + "; it is matched to them by position, so it is empty or as long — remove an entry's evidence with it");
        }
    }

    private static void requireWithin(String field, Money amount, Money bound, String what) {
        if (amount.isLessThan(Money.ZERO) || amount.isGreaterThan(bound)) {
            throw new IllegalArgumentException(field + " must be from nil to " + bound + ", " + what + ", but was "
                    + amount);
        }
    }

    private static Money paidUnder(List<QualifyingPayment> payments, ComponentCode code) {
        List<QualifyingPayment> under = payments.stream().filter(payment -> payment.code().equals(code)).toList();
        if (under.isEmpty()) {
            throw new IllegalArgumentException("/evidence/qualifyingPayments/" + code + " is for a payment code no"
                    + " /entered/qualifyingPayments entry has");
        }
        return under.stream().map(QualifyingPayment::amount).reduce(Money.ZERO, Money::plus);
    }
}
