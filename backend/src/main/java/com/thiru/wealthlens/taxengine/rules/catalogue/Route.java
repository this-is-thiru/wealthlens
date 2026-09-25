package com.thiru.wealthlens.taxengine.rules.catalogue;

import java.util.List;

/**
 * One way to satisfy a section, with its own conditions (D-042) — many sections can be met in
 * several ways, and each way qualifies differently.
 */
public record Route(String name, String description, List<EnforcedBy> conditions) {

    public Route {
        if (name == null || name.isBlank() || description == null || description.isBlank()) {
            throw new IllegalArgumentException("A route must have a name and a description");
        }
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException(
                    "A route must name what enforces it — a rule, a Condition, or NotModelled with a reason");
        }
        conditions = List.copyOf(conditions);
    }
}
