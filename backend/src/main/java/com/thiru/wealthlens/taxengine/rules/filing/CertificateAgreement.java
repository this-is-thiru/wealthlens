package com.thiru.wealthlens.taxengine.rules.filing;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * Two amounts a certificate prints that should be near each other though no sum on the form says so (W-128b, D-750 C3a):
 * Form No. 130's Part B tax deducted against Part C's net tax payable. A validation the design asks for ({@code DR 06}
 * G5.2), not law, kept with the form's version so a later form carries its own. A difference beyond the tolerance is
 * shown to the user, never resolved: an employer may deduct more or less than the year's tax.
 *
 * @param printed the first amount, by its path in a reading, e.g. {@code partB.summary.total.taxDeducted}
 * @param near the amount it should be near, e.g. {@code partC.annexureI.row21}
 * @param tolerance how far apart the two may be
 * @param basis where the expectation comes from
 */
public record CertificateAgreement(String printed, String near, Money tolerance, String basis) {

    public CertificateAgreement {
        if (printed == null || printed.isBlank() || near == null || near.isBlank() || tolerance == null || basis == null
                || basis.isBlank()) {
            throw new IllegalArgumentException("An agreement names two amounts, its tolerance and its basis");
        }
        if (printed.equals(near)) {
            throw new IllegalArgumentException("An agreement compares two amounts, not " + printed + " with itself");
        }
        if (tolerance.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("An agreement's tolerance is not negative: " + tolerance);
        }
    }
}
