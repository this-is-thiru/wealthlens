package com.thiru.wealthlens.taxengine.rules.coverage;

import com.thiru.wealthlens.taxengine.rules.temporal.Presence;
import com.thiru.wealthlens.taxengine.rules.temporal.Provision;
import java.util.Arrays;
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
        if (provision.presence() != Presence.PRESENT) {
            return Optional.empty();
        }
        Optional<Act> act = Arrays.stream(Act.values())
                .filter(candidate -> candidate.citation().equals(provision.instrumentCitation()))
                .findFirst();
        Matcher citation = CITATION.matcher(provision.reference());
        if (act.isEmpty() || !citation.find()) {
            return Optional.empty();
        }
        return Optional.of(new SectionRef(act.get(), citation.group(1)));
    }

    @Override
    public String toString() {
        return act.citation() + " s." + section;
    }
}
