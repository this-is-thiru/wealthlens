package com.thiru.wealthlens.shared.migration;

import java.util.List;

/**
 * What the copier found for one source after copying it (W-337g, D-953 C4a): the rows on each side, the digest of the rows
 * as mapped from the source, and every check that failed. The copy is proven only when {@link #problems} is empty.
 *
 * @param digest SHA-256 over the source rows as mapped, ordered by key; the target's equals it when nothing changed
 */
public record CopyReport(String source, String table, int sourceRows, int targetRows, String digest, List<String> problems) {

    public CopyReport {
        problems = List.copyOf(problems);
    }

    public boolean isClean() {
        return problems.isEmpty();
    }

    public String describe() {
        return source + " -> " + table + ": " + sourceRows + " source rows, " + targetRows + " target rows, digest " + digest
                + (isClean() ? ", clean" : ", PROBLEMS " + problems);
    }
}
