package com.thiru.wealthlens.taxengine.rules.coverage;

import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import java.util.List;
import java.util.Set;

/**
 * How one section is covered — exactly one of four classifications, each carrying what
 * {@code 00-BRIEF.md} §7.1 requires of it (D-038).
 */
public sealed interface Coverage {

    /** At least one rule implements the section. */
    record Modelled(List<RuleId> rules, String manager) implements Coverage {

        public Modelled {
            if (rules == null || rules.isEmpty()) {
                throw new IllegalArgumentException("A MODELLED section must name the rules that implement it");
            }
            if (manager == null || manager.isBlank()) {
                throw new IllegalArgumentException("A MODELLED section must name the manager that evaluates it");
            }
            rules = List.copyOf(rules);
        }
    }

    /** In scope, not yet built. */
    record Planned(Set<PersonType> assesseeTypes, String backlogItem) implements Coverage {

        public Planned {
            if (assesseeTypes == null || assesseeTypes.isEmpty()) {
                throw new IllegalArgumentException("A PLANNED section must name the assessee types it affects");
            }
            if (backlogItem == null || !backlogItem.matches("^W-[0-9]{3}[a-z0-9]*$")) {
                throw new IllegalArgumentException(
                        "A PLANNED section must name its backlogItem, e.g. W-067, but was \"" + backlogItem + "\"");
            }
            assesseeTypes = Set.copyOf(assesseeTypes);
        }
    }

    /** Procedure — assessment, appeals, search, prosecution — which computes nothing. */
    record NotComputational() implements Coverage {}

    /** Deliberately excluded. */
    record OutOfScope(String reason, String decidedBy) implements Coverage {

        public OutOfScope {
            if (reason == null || reason.isBlank() || decidedBy == null || decidedBy.isBlank()) {
                throw new IllegalArgumentException("An OUT_OF_SCOPE section must give a reason and who decided");
            }
        }
    }
}
