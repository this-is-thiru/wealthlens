package com.thiru.wealthlens.taxplanning.gazette;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import com.thiru.wealthlens.taxengine.api.ProvisionCitation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Decides whether a Gazette copy may change a shipped pack (W-236 E1a, E3). The whole copy is read page by page, and the
 * first page by which it has named any of {@code leadWhenAny}, or all of {@code leadWhenAll}, makes it a lead — page 1
 * alone missed the Finance Act, 2026 (published by the Law ministry) and the Income-tax Rules, 2026 (English from page
 * 1,533; {@code sources/2026-11-07-gazette-watch-window.md}). On a lead, that page and the next two are searched for
 * "section N of the Income-tax Act, 1961 / 2025" and each shipped rule citing it is named — for the owner's triage, never
 * a finding that the rule changed.
 */
public final class GazetteMatcher {

    private static final int PAGES_AFTER_MATCH = 2;

    private static final Pattern SECTION_OF_ACT = Pattern.compile(
            "(?i:\\bsection)\\s+(\\d+[A-Z]*)\\s+(?i:of\\s+the\\s+income-tax\\s+act),\\s*(1961|2025)");

    private final List<String> leadWhenAny;

    private final List<String> leadWhenAll;

    private final Map<String, Set<String>> rulesBySection;

    /**
     * @param settings the terms
     * @param cited what the shipped packs cite, from {@code TaxCalculator.provisionsCited}
     */
    public GazetteMatcher(GazetteWatchSettings settings, List<ProvisionCitation> cited) {
        this.leadWhenAny = lowered(settings.leadWhenAny());
        this.leadWhenAll = lowered(settings.leadWhenAll());
        this.rulesBySection = cited.stream().collect(Collectors.groupingBy(
                citation -> key(citation.act(), citation.section()),
                Collectors.mapping(ProvisionCitation::ruleId, Collectors.toCollection(TreeSet::new))));
    }

    /** Whether the copy is a lead, and why; empty where it names none of the terms. */
    public Optional<GazetteMatch> match(byte[] pdf) {
        PdfReader reader = open(pdf);
        try {
            Set<String> seen = new LinkedHashSet<>();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                String text = normalised(text(reader, page));
                String lower = text.toLowerCase(Locale.ROOT);
                leadWhenAny.stream().filter(lower::contains).forEach(seen::add);
                leadWhenAll.stream().filter(lower::contains).forEach(seen::add);
                if (isLead(seen)) {
                    return Optional.of(new GazetteMatch(page, List.copyOf(seen), affected(reader, page)));
                }
            }
            return Optional.empty();
        } finally {
            reader.close();
        }
    }

    private boolean isLead(Set<String> seen) {
        return leadWhenAny.stream().anyMatch(seen::contains)
                || (!leadWhenAll.isEmpty() && seen.containsAll(leadWhenAll));
    }

    private List<String> affected(PdfReader reader, int page) {
        Set<String> affected = new TreeSet<>();
        int last = Math.min(reader.getNumberOfPages(), page + PAGES_AFTER_MATCH);
        for (int read = page; read <= last; read++) {
            Matcher section = SECTION_OF_ACT.matcher(normalised(text(reader, read)));
            while (section.find()) {
                String key = key(section.group(2), section.group(1));
                rulesBySection.getOrDefault(key, Set.of()).forEach(rule -> affected.add(key + " " + rule));
            }
        }
        return new ArrayList<>(affected);
    }

    private static String key(String act, String section) {
        return act + " s." + section;
    }

    /** One line of text, and the Gazette's hyphenation as typeset ("Income -tax") read as the word it is. */
    private static String normalised(String text) {
        return text.replaceAll("\\s+", " ").replaceAll("\\s*-\\s*", "-");
    }

    private static List<String> lowered(List<String> terms) {
        return terms.stream().map(term -> term.toLowerCase(Locale.ROOT)).toList();
    }

    private static PdfReader open(byte[] pdf) {
        try {
            return new PdfReader(pdf);
        } catch (IOException e) {
            throw new UncheckedIOException("The copy is not a readable PDF", e);
        }
    }

    private static String text(PdfReader reader, int page) {
        try {
            return PdfTextExtractor.getTextFromPage(reader, page);
        } catch (IOException e) {
            throw new UncheckedIOException("Page " + page + " of the copy could not be read", e);
        }
    }
}
