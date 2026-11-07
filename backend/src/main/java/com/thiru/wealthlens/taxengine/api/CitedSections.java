package com.thiru.wealthlens.taxengine.api;

import com.thiru.wealthlens.taxengine.rules.temporal.Presence;
import com.thiru.wealthlens.taxengine.rules.temporal.Provision;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The section numbers a rule's provision reference names, and which Income-tax Act it names them in (W-236).
 *
 * <p>A reference reads like {@code s.19(1) Table Sl. 2(a)} or {@code ss.80C, 80CCC, 80CCD(1), 80CCE; excluded by
 * s.115BAC(2)(i)}: {@code s.} introduces one section, {@code ss.} a comma-separated list, and {@code ;} starts another
 * citation. Sub-divisions in brackets are dropped — the section is what an instrument amends.
 */
final class CitedSections {

    private static final Pattern ACT = Pattern.compile("Income-tax Act, (1961|2025)");

    private static final Pattern MARKER = Pattern.compile("\\b(ss?)\\.\\s*");

    private static final Pattern SECTION = Pattern.compile("^(\\d+[A-Z]*)");

    private CitedSections() {
    }

    /** The Act's year where the provision is present in an Income-tax Act; empty otherwise. */
    static Optional<String> actOf(Provision provision) {
        if (provision.presence() != Presence.PRESENT || provision.instrumentCitation() == null) {
            return Optional.empty();
        }
        Matcher act = ACT.matcher(provision.instrumentCitation());
        return act.find() ? Optional.of(act.group(1)) : Optional.empty();
    }

    /** Every section the reference names, in order. */
    static List<String> of(String reference) {
        List<String> sections = new ArrayList<>();
        for (String citation : reference.split(";")) {
            Matcher marker = MARKER.matcher(citation);
            while (marker.find()) {
                String rest = citation.substring(marker.end());
                List<String> items = marker.group(1).equals("ss") ? List.of(rest.split(",")) : List.of(rest);
                for (String item : items) {
                    Matcher section = SECTION.matcher(item.strip());
                    if (section.find()) {
                        sections.add(section.group(1));
                    }
                }
            }
        }
        return sections;
    }
}
