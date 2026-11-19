package com.thiru.wealthlens.taxengine.rules.filing;

import java.util.Optional;

/**
 * One cell of a certificate: a line, and for a line printing several cells, the column by its printed heading (W-127b).
 */
public record CertificateCell(String line, Optional<String> column) {

    public CertificateCell {
        if (line == null || column == null) {
            throw new IllegalArgumentException("A certificate cell names its line, and its column or none");
        }
    }

    @Override
    public String toString() {
        return line + column.map(heading -> " [" + heading + "]").orElse("");
    }
}
