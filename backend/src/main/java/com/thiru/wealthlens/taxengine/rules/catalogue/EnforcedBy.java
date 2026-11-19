package com.thiru.wealthlens.taxengine.rules.catalogue;

import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import java.util.Arrays;
import java.util.List;

/**
 * What makes a route's condition or a disqualifier true in the computation (SC-3). The link is why the
 * catalogue exists: explanation and computation are then one fact, so the text cannot say a person
 * qualifies while the engine says they do not.
 */
public sealed interface EnforcedBy {

    /** A rule in the timeline enforces it. */
    record ByRule(RuleId rule) implements EnforcedBy {

        public ByRule {
            if (rule == null) {
                throw new IllegalArgumentException("ByRule must name its rule");
            }
        }
    }

    /** A record of the {@link Condition} algebra enforces it. */
    record ByCondition(Class<? extends Condition> condition) implements EnforcedBy {

        public ByCondition {
            if (condition == null) {
                throw new IllegalArgumentException("ByCondition must name its condition");
            }
        }

        /** The condition record with this simple name, e.g. {@code "HasIncomeUnderHead"}. */
        public static ByCondition named(String name) {
            List<Class<?>> records = Arrays.asList(Condition.class.getPermittedSubclasses());
            return records.stream()
                    .filter(record -> record.getSimpleName().equals(name))
                    .findFirst()
                    .map(record -> new ByCondition(record.asSubclass(Condition.class)))
                    .orElseThrow(() -> new IllegalArgumentException("\"" + name + "\" is not a Condition; it must be one of "
                            + records.stream().map(Class::getSimpleName).sorted().toList()));
        }
    }

    /** Nothing enforces it yet; the reason says why, so the gap is visible rather than implied. */
    record NotModelled(String reason) implements EnforcedBy {

        public NotModelled {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("NotModelled must give its reason");
            }
        }
    }
}
