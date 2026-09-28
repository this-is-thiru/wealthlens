package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * An employer's spending on medical treatment outside India, or its reimbursement of the employee's (S2 s.17(2)(f),
 * (3); 1961 s.17(2) proviso (vi); D-112).
 *
 * <p>The two amounts are kept apart because the Act excludes them on different tests: treatment and stay "only to
 * the extent permitted by the Reserve Bank of India", travel only where gross total income before it does not
 * exceed the prescribed amount. The Reserve Bank sets no figure the Act names, so the permitted amount is entered.
 * The exclusion covers "one attendant who accompanies the patient", so a claim with more is refused rather than
 * split.
 *
 * @param employment which entered employment pays or reimburses it, by position
 * @param treatmentAndStay the cost of the treatment and of the stay abroad of the patient and the attendant
 * @param travel the cost of travel abroad of the patient and the attendant
 * @param permittedByReserveBank how much of the treatment and stay the Reserve Bank of India permitted
 * @param attendants how many attendants accompanied the patient
 */
public record MedicalTreatmentAbroad(
        int employment, Money treatmentAndStay, Money travel, Money permittedByReserveBank, int attendants) {

    public MedicalTreatmentAbroad {
        if (treatmentAndStay == null || travel == null || permittedByReserveBank == null) {
            throw new IllegalArgumentException(
                    "Medical treatment abroad needs the treatment-and-stay cost, the travel cost and the amount the"
                            + " Reserve Bank of India permitted; enter zero for one not incurred");
        }
        if (employment < 0) {
            throw new IllegalArgumentException("An employment position may not be negative, but was " + employment);
        }
        if (treatmentAndStay.isLessThan(Money.ZERO) || travel.isLessThan(Money.ZERO)
                || permittedByReserveBank.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("Medical treatment abroad amounts may not be negative");
        }
        if (attendants < 0 || attendants > 1) {
            throw new IllegalArgumentException("The exclusion covers \"one attendant who accompanies the patient\""
                    + " (S2 s.17(2)(f)(iii); 1961 s.17(2) proviso (vi)); " + attendants + " were entered, and how a"
                    + " second attendant's share of the cost is taxed is not built");
        }
    }
}
