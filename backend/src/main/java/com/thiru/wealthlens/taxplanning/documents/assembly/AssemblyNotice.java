package com.thiru.wealthlens.taxplanning.documents.assembly;

import java.util.List;

/**
 * What the assembly asks the user to look at or to act on ({@code DR 06} G5.3), naming the certificates it is about.
 *
 * @param documentIds the certificates the notice is about
 * @param relatedRules the rule ids it rests on, where it rests on law
 */
public record AssemblyNotice(Level level, String message, List<String> documentIds, List<String> relatedRules) {

    /** G5.3's two levels. */
    public enum Level {
        /** Worth a look: nothing is known to be wrong. */
        ATTENTION,
        /** Tax will be owed or claimed at filing unless the user acts. */
        ACTION_REQUIRED
    }

    public AssemblyNotice {
        if (level == null || message == null || message.isBlank() || documentIds == null || relatedRules == null) {
            throw new IllegalArgumentException("A notice needs its level, message, certificates and rules (or none)");
        }
        documentIds = List.copyOf(documentIds);
        relatedRules = List.copyOf(relatedRules);
    }
}
