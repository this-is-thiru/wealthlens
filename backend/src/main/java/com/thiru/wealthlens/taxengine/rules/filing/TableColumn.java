package com.thiru.wealthlens.taxengine.rules.filing;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One column of a certificate's table, by the heading the form prints over it (W-126c2a, D-737 C2a). The key names the
 * column's cells in a reading, since a printed heading such as "Amount paid/credited" cannot be a path word.
 *
 * @param group the heading printed over this column and its neighbours, such as "Book Identification Number (BIN)"
 */
public record TableColumn(String heading, String key, CertificateValueKind kind, Optional<String> group) {

    /** One word in camel case. */
    private static final Pattern KEY = Pattern.compile("^[a-z][A-Za-z0-9]*$");

    public TableColumn {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("A table column's key is one word in camel case: " + key);
        }
        if (heading == null || heading.isBlank() || kind == null || group == null || group.filter(String::isBlank).isPresent()) {
            throw new IllegalArgumentException("Column " + key + " needs its printed heading, its kind and its group or none");
        }
        if (kind == CertificateValueKind.NONE) {
            throw new IllegalArgumentException("Column " + key + " holds a value; NONE is a heading line's kind");
        }
    }
}
