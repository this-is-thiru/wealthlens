package com.thiru.wealthlens.taxengine.rules.catalogue;

import com.thiru.wealthlens.taxengine.rules.temporal.Provision;
import java.util.List;
import java.util.regex.Pattern;

/**
 * One section of the law explained for a person rather than the engine (D-042, {@code 00-BRIEF.md}
 * §6.3): what it is, the ways to qualify, and what disqualifies.
 *
 * <p>The provisions are {@link Provision}s, not sections, because a concept is not always a section in
 * both Acts — HRA is a Schedule III item in the 2025 Act (D-060). Availability and history are absent
 * on purpose: they are derived from rule versions, never authored (SC-4).
 *
 * @param key a concept, e.g. {@code SEC-DEFAULT-TAX-REGIME}; the entry's file is named for it
 */
public record SectionCatalogueEntry(
        String key,
        Provision provision1961,
        Provision provision2025,
        String plainName,
        String summary,
        List<Route> routes,
        List<Disqualifier> disqualifiers) {

    private static final Pattern KEY = Pattern.compile("^SEC(-[A-Z0-9]+)+$");

    public SectionCatalogueEntry {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("A catalogue key is SEC- then hyphenated capitals, e.g."
                    + " SEC-DEFAULT-TAX-REGIME, but was \"" + key + "\"");
        }
        if (provision1961 == null || provision2025 == null) {
            throw new IllegalArgumentException("An entry must state its provision in both Acts");
        }
        if (plainName == null || plainName.isBlank() || summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("An entry must have a plainName and a summary");
        }
        if (routes == null || routes.isEmpty()) {
            throw new IllegalArgumentException("An entry must have at least one route");
        }
        routes = List.copyOf(routes);
        disqualifiers = List.copyOf(disqualifiers);
    }
}
