package com.thiru.wealthlens.taxengine.rules.filing;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A sum the certificate prints, such as row 3's "[1(d)-2(f)]": its target cell equals its terms, each with its sign
 * (W-127b, D-734). Law as the form prints it, so data: a later form's arithmetic is a new version of its file.
 *
 * @param page the Gazette page the sum is printed on
 */
public record CertificateIdentity(CertificateCell target, List<CertificateTerm> terms, int page) {

    public CertificateIdentity {
        if (target == null || terms == null || terms.isEmpty() || page < 1) {
            throw new IllegalArgumentException("A printed sum needs its target, at least one term and its page");
        }
        terms = List.copyOf(terms);
        Set<CertificateCell> cells = new HashSet<>(Set.of(target));
        for (CertificateTerm term : terms) {
            if (!cells.add(term.cell())) {
                throw new IllegalArgumentException("The sum for " + target + " names " + term.cell() + " twice");
            }
        }
    }
}
