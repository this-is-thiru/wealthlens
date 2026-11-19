package com.thiru.wealthlens.taxengine.rules.pack;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A trace line's plain-language sentences for one rule (PRD F37; W-116, D-671): what the figure is, and why — one
 * sentence per limit of the rule's least-of, chosen by the limit the line names in {@code limitedBy}. Content, so it
 * changes no number, and holds none (SC-2): the figure comes from the trace through a placeholder.
 *
 * @param rule the rule whose lines it explains
 * @param what what the line's figure is; {@code {out}} stands for it, and {@code {in}} for what the line started from
 * @param byLimit why, keyed by each limit's name as the rule's cap names it; empty for a rule with no least-of
 */
public record Explanation(RuleId rule, String what, Map<String, String> byLimit) {

    /** The placeholders {@link #what} may hold; the figures are filled from the trace line. */
    public static final Set<String> PLACEHOLDERS = Set.of("{in}", "{out}");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[^}]*}");

    private static final Pattern FIGURE = Pattern.compile("\\d");

    public Explanation {
        if (rule == null) {
            throw new IllegalArgumentException("An explanation must name its rule");
        }
        requireProse(rule + ".what", what, PLACEHOLDERS);
        if (byLimit == null) {
            throw new IllegalArgumentException(rule + " has no byLimit; pass an empty map for a rule with no least-of");
        }
        byLimit.forEach((limit, sentence) -> requireProse(rule + ".byLimit[" + limit + "]", sentence, Set.of()));
        byLimit = Map.copyOf(new TreeMap<>(byLimit));
    }

    /**
     * Refuses blank text, a figure, and a placeholder other than {@code permitted}: content states no number (SC-2), and
     * a misspelt placeholder would print as written.
     */
    static void requireProse(String where, String text, Set<String> permitted) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(where + " is blank; a line with nothing to say shows its rule id instead");
        }
        if (FIGURE.matcher(text).find()) {
            throw new IllegalArgumentException(where + " holds a figure; content states none, the trace supplies them (SC-2): "
                    + text);
        }
        Matcher placeholder = PLACEHOLDER.matcher(text);
        while (placeholder.find()) {
            if (!permitted.contains(placeholder.group())) {
                throw new IllegalArgumentException(where + " holds " + placeholder.group() + "; it may hold only "
                        + permitted);
            }
        }
    }
}
