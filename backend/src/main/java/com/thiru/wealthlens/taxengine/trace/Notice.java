package com.thiru.wealthlens.taxengine.trace;

import com.thiru.wealthlens.taxrules.id.RuleId;
import java.util.List;

/**
 * Something worth saying about a computation step.
 *
 * <p>{@code 05-domain-model.md} M5.1, {@code D-TRACE}. Two properties matter and both are
 * deliberate:
 *
 * <ol>
 *   <li><b>There is no separate warnings collection</b> (O-5). A notice hangs off the line that
 *       produced it, so it cannot drift away from the number it is about.
 *   <li><b>A notice cannot abort</b> (O-27). {@link Severity} has no error value. Something that
 *       must stop the engine throws; it does not file a notice and hope someone reads it.
 * </ol>
 *
 * @param severity how much attention it deserves
 * @param message human text — the notice is read by a person, not matched on by code
 * @param relatedRules the rules this notice arises from; may be empty, never null
 */
public record Notice(Severity severity, String message, List<RuleId> relatedRules) {

    public Notice {
        if (severity == null) {
            throw new IllegalArgumentException("A Notice must state its severity");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException(
                    "A Notice must carry a message; a notice nobody can read is not a notice");
        }
        if (relatedRules == null) {
            throw new IllegalArgumentException(
                    "Notice.relatedRules may not be null; pass an empty list where there are none");
        }
        relatedRules = List.copyOf(relatedRules);
    }

    /** A notice that arises from no rule in particular. */
    public static Notice of(Severity severity, String message) {
        return new Notice(severity, message, List.of());
    }

    /** A notice that arises from one rule. */
    public static Notice about(Severity severity, String message, RuleId rule) {
        return new Notice(severity, message, List.of(rule));
    }
}
