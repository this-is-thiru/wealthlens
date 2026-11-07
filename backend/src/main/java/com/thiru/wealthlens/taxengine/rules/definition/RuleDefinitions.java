package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import java.util.Optional;

/**
 * Reading the one kind of {@link RuleDefinition} a node expects from a rule (D-139). Every reader wants exactly one
 * type and treats any other as a wrong row; one check here replaces a switch over every type in each node, which grew
 * by a case at every new type.
 */
public final class RuleDefinitions {

    private RuleDefinitions() {
    }

    /**
     * The rule's definition as {@code type}.
     *
     * @throws IllegalStateException where the rule carries no definition or another kind — a wrong row, not an input
     */
    public static <T extends RuleDefinition> T require(Optional<RuleDefinition> definition, Class<T> type,
            RuleId rule) {
        RuleDefinition found = definition.orElseThrow(() -> new IllegalStateException(
                rule + " carries no definition; a " + type.getSimpleName() + " was expected"));
        if (!type.isInstance(found)) {
            throw new IllegalStateException(rule + " defines a " + found.getClass().getSimpleName() + ", not a "
                    + type.getSimpleName());
        }
        return type.cast(found);
    }
}
