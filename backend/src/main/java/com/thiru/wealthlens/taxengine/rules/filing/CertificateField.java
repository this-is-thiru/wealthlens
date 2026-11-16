package com.thiru.wealthlens.taxengine.rules.filing;

import java.util.List;
import java.util.Optional;

/**
 * One line of a certificate, found by its printed label (W-126a; {@code DR 06} G5.1 L1). The label is the form's own words,
 * without the row number, which is printed apart from it.
 *
 * @param path dot-separated, under its part's path
 * @param row the row number as printed ({@code 1(a)}, {@code A}), where the form prints one
 * @param columns the printed headings of a line with several value cells; empty for one cell
 * @param repeating whether the form leaves room for as many such lines as the issuer needs ("section …")
 * @param page the Gazette page the line is printed on
 */
public record CertificateField(String path, Optional<String> row, String label, CertificateValueKind kind,
        List<String> columns, boolean repeating, int page) {

    public CertificateField {
        if (path == null || !CertificatePart.PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("A certificate field's path is dot-separated words: " + path);
        }
        if (row == null || label == null || label.isBlank() || kind == null || columns == null) {
            throw new IllegalArgumentException(path + " needs its row (or none), label, kind and columns");
        }
        if (kind == CertificateValueKind.NONE && (!columns.isEmpty() || repeating)) {
            throw new IllegalArgumentException(path + " is a heading line; it has no columns and does not repeat");
        }
        if (page < 1) {
            throw new IllegalArgumentException(path + " is printed on a page from 1");
        }
        columns = List.copyOf(columns);
    }
}
