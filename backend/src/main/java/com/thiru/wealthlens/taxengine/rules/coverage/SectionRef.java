package com.thiru.wealthlens.taxengine.rules.coverage;

import com.thiru.wealthlens.taxengine.rules.temporal.Presence;
import com.thiru.wealthlens.taxengine.rules.temporal.Provision;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One section of one Income-tax Act. The Act is part of the identity: the 1961 and 2025 Acts reuse
 * section numbers for different law ({@code s.10}, {@code s.16}, {@code s.19}), so a number alone
 * names nothing (D-059).
 *
 * @param section the number with any letters, as the Act writes it, e.g. {@code "115BAC"}, {@code "194-O"}
 */
public record SectionRef(Act act, String section) {

    /** A number, then letters, optionally after a hyphen: the Act writes 194-O so it cannot read as 1940, and 115V-I. */
    private static final Pattern CITATION = Pattern.compile("^s\\.([0-9]+[A-Z]*(?:-[A-Z]+)?)");
    private static final Pattern SECTION = Pattern.compile("^[0-9]+[A-Z]*(?:-[A-Z]+)?$");
    /** A citation's opening, single or plural: {@code s.112A(1)} or {@code ss.80C, 80CCC, 80CCE}. */
    private static final Pattern MARKER = Pattern.compile("(?<![A-Za-z])s(s?)\\.");
    private static final Pattern NUMBER = Pattern.compile("\\s*([0-9]+[A-Z]*(?:-[A-Z]+)?)");
    /** A list entry's closing: an optional clause in brackets, then the comma or semicolon before the next. */
    private static final Pattern SEPARATOR = Pattern.compile("(?:\\([^)]*\\))?\\s*[,;]\\s*");
    /** Another Act's section: the Finance Act's own {@code s.2} is not the Income-tax Act's. */
    private static final Pattern OTHER_INSTRUMENT =
            Pattern.compile("Finance(?: \\(No\\. \\d\\))? Act, \\d{4} s\\.\\d+(?:\\([a-z0-9]+\\))?");

    /** The two Acts whose sections the register lists. */
    public enum Act {
        INCOME_TAX_ACT_1961("Income-tax Act, 1961"),
        INCOME_TAX_ACT_2025("Income-tax Act, 2025");

        private final String citation;

        Act(String citation) {
            this.citation = citation;
        }

        /** The instrument citation a provision uses for this Act. */
        public String citation() {
            return citation;
        }
    }

    public SectionRef {
        if (act == null) {
            throw new IllegalArgumentException("A section must name its Act");
        }
        if (section == null || !SECTION.matcher(section).matches()) {
            throw new IllegalArgumentException(
                    "A section is a number with optional capital letters, e.g. 115BAC or 194-O, but was \"" + section + "\"");
        }
    }

    /**
     * The section a provision cites, if it cites one: a {@code PRESENT} provision of one of the two Acts
     * whose reference starts {@code s.<number>}. Schedules, Rules and Finance Act provisions cite no
     * section of this register.
     */
    public static Optional<SectionRef> citedBy(Provision provision) {
        Optional<Act> act = actOf(provision);
        if (act.isEmpty()) {
            return Optional.empty();
        }
        Matcher citation = CITATION.matcher(provision.reference());
        if (!citation.find()) {
            return Optional.empty();
        }
        return Optional.of(new SectionRef(act.get(), citation.group(1)));
    }

    /**
     * Every section a provision names, in the order the reference names them — all {@code s.N}, and
     * every entry of a plural {@code ss.A, B, C} list, with another Act's citation removed first.
     *
     * <p>{@link #citedBy} answers a different question and neither replaces the other: {@code citedBy}
     * is the one section a provision is <em>under</em> (its reference must open with it), while this
     * is every section it <em>mentions</em> — so a provision that opens with a Schedule and names its
     * section further on is under no section but names one here, and a section named only in an
     * "excluded by" note counts as named. The coverage check asks whether a register row and a rule it
     * lists have any section in common, and that is this method's question.
     */
    public static List<SectionRef> namedBy(Provision provision) {
        Optional<Act> act = actOf(provision);
        if (act.isEmpty()) {
            return List.of();
        }
        String reference = OTHER_INSTRUMENT.matcher(provision.reference()).replaceAll("");
        LinkedHashSet<SectionRef> named = new LinkedHashSet<>();
        Matcher marker = MARKER.matcher(reference);
        int at = 0;
        while (marker.find(at)) {
            at = marker.end();
            at = marker.group(1).isEmpty()
                    ? addOne(reference, at, act.get(), named)
                    : addList(reference, at, act.get(), named);
        }
        return List.copyOf(named);
    }

    private static Optional<Act> actOf(Provision provision) {
        if (provision.presence() != Presence.PRESENT) {
            return Optional.empty();
        }
        return Arrays.stream(Act.values())
                .filter(candidate -> candidate.citation().equals(provision.instrumentCitation()))
                .findFirst();
    }

    private static int addOne(String reference, int at, Act act, LinkedHashSet<SectionRef> named) {
        Matcher number = NUMBER.matcher(reference);
        number.region(at, reference.length());
        if (number.lookingAt()) {
            named.add(new SectionRef(act, number.group(1)));
        }
        return at;
    }

    /** Reads a plural list entry by entry; returns where the list ended, so the scan resumes after it. */
    private static int addList(String reference, int at, Act act, LinkedHashSet<SectionRef> named) {
        Matcher number = NUMBER.matcher(reference);
        Matcher separator = SEPARATOR.matcher(reference);
        while (true) {
            number.region(at, reference.length());
            if (!number.lookingAt()) {
                return at;
            }
            named.add(new SectionRef(act, number.group(1)));
            at = number.end();
            separator.region(at, reference.length());
            if (!separator.lookingAt()) {
                return at;
            }
            at = separator.end();
        }
    }

    @Override
    public String toString() {
        return act.citation() + " s." + section;
    }
}
