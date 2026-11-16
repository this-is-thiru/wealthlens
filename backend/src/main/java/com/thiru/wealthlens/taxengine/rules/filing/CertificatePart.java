package com.thiru.wealthlens.taxengine.rules.filing;

import java.util.regex.Pattern;

/**
 * A part of a certificate, or a group inside one, found by its printed heading (W-126a).
 *
 * @param path dot-separated, e.g. {@code partC.annexureI}
 * @param heading as printed
 */
public record CertificatePart(String path, String heading) {

    /** Dot-separated words, each starting lower-case or with a digit after the first. */
    static final Pattern PATH = Pattern.compile("^[a-z][A-Za-z0-9]*(\\.[a-z0-9][A-Za-z0-9]*)*$");

    public CertificatePart {
        if (path == null || !PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("A certificate part's path is dot-separated words: " + path);
        }
        if (heading == null || heading.isBlank()) {
            throw new IllegalArgumentException("A certificate part needs its printed heading: " + path);
        }
    }
}
