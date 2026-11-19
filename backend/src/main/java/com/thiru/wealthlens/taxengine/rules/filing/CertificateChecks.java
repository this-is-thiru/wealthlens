package com.thiru.wealthlens.taxengine.rules.filing;

import java.util.List;

/**
 * What a certificate's figures must agree with: the sums it prints, the amounts that should be near each other, and the
 * amounts the engine's computation also produces. One value, so a certificate stays within seven components (W-332).
 *
 * @param identities the sums the form prints (W-127b)
 * @param agreements the amounts that should be near each other though no printed sum says so (W-128b)
 * @param comparisons the amounts the engine's computation also produces (W-130a)
 */
public record CertificateChecks(List<CertificateIdentity> identities, List<CertificateAgreement> agreements,
        List<CertificateComparison> comparisons) {

    public CertificateChecks {
        identities = List.copyOf(identities);
        agreements = List.copyOf(agreements);
        comparisons = List.copyOf(comparisons);
    }
}
