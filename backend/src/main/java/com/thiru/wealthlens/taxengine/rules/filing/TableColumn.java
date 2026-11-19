package com.thiru.wealthlens.taxengine.rules.filing;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One column of a certificate's table, by the heading the form prints over it (W-126c2a, D-737 C2a). The key names the
 * column's cells in a reading, since a printed heading such as "Amount paid/credited" cannot be a path word.
 *
 * @param group the heading printed over this column and its neighbours, such as "Book Identification Number (BIN)"
 * @param captions the words the form prints inside the column's cell before each value, in order, such as Form 16's
 *     "Address", "City" and "Pin Code" under "CIT (TDS)" (W-126e3a, D-745 C2a); none for most columns
 */
public record TableColumn(String heading, String key, CertificateValueKind kind, Optional<String> group, List<String> captions) {

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
        requirePrintedOnce(key, captions);
        captions = List.copyOf(captions);
    }

    private static void requirePrintedOnce(String key, List<String> captions) {
        if (captions == null || captions.stream().anyMatch(caption -> caption == null || caption.isBlank())
                || new HashSet<>(captions).size() != captions.size()) {
            throw new IllegalArgumentException("Column " + key + "'s captions are printed words, each once: " + captions);
        }
    }
}
