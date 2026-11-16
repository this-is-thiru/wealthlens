package com.thiru.wealthlens.taxengine.rules.filing;

/** One cell of a printed sum, added (+1) or taken away (-1) (W-127b). */
public record CertificateTerm(CertificateCell cell, int sign) {

    public CertificateTerm {
        if (cell == null || (sign != 1 && sign != -1)) {
            throw new IllegalArgumentException("A sum's term is a cell with a sign of +1 or -1");
        }
    }
}
