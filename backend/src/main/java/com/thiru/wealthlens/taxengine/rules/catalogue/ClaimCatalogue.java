package com.thiru.wealthlens.taxengine.rules.catalogue;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.json.PackJson;
import com.thiru.wealthlens.taxengine.rules.json.ValueJson;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.timeline.Timeline;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The claim catalogue ({@code DR 22}; W-117a, D-699), read from the timeline's {@code content/claim-catalogue.json}, and
 * the build's check of it.
 *
 * <p>An entry checks itself as it is read: its steps' numbering, the late-return warning its Part needs (I24), what
 * closes each piece of evidence (I25) and each step's deadline (I26). {@link #verify} checks the rest against the
 * timeline, because a rule, a section or a placeholder is only a link if what it names exists, and I23: every rule of a
 * timeline that has a catalogue is spoken for once — by an entry, as not yet catalogued, or as not a claim (W-117d).
 *
 * @param entries how each catalogued claim is made
 * @param notYetCatalogued the deductions and exemptions a person claims that have no entry yet; only shrinks
 * @param notAClaim every other rule, and why it has no entry
 */
public record ClaimCatalogue(List<ClaimCatalogueEntry> entries, List<RuleId> notYetCatalogued, List<NotAClaim> notAClaim) {

    /** The catalogue's file, in a timeline and in a year's pack. */
    public static final String FILE = "content/claim-catalogue.json";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Pattern AMOUNT = Pattern.compile("\\d+(\\.\\d+)?");
    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{\\{([A-Z]{2,8}(?:-[A-Z0-9]{1,12}){1,5})\\.([a-zA-Z][a-zA-Z0-9]*)}}");

    public ClaimCatalogue {
        entries = List.copyOf(entries);
        notYetCatalogued = List.copyOf(notYetCatalogued);
        notAClaim = List.copyOf(notAClaim);
        Set<RuleId> seen = new TreeSet<>((left, right) -> left.value().compareTo(right.value()));
        for (ClaimCatalogueEntry entry : entries) {
            for (RuleId rule : spokenFor(entry)) {
                if (!seen.add(rule)) {
                    throw new IllegalArgumentException("A claim catalogue has one entry for a rule, but " + rule
                            + " has two");
                }
            }
        }
        Stream.concat(notYetCatalogued.stream(), notAClaim.stream().map(NotAClaim::rule)).forEach(rule -> {
            if (!seen.add(rule)) {
                throw new IllegalArgumentException("I23 — " + rule + " is placed twice: a rule is catalogued, not yet"
                        + " catalogued or not a claim, once");
            }
        });
    }

    /** Every rule the catalogue speaks for: its entries' lead and covered rules, then both lists. */
    private Set<RuleId> placed() {
        Set<RuleId> placed = new TreeSet<>((left, right) -> left.value().compareTo(right.value()));
        entries.forEach(entry -> placed.addAll(spokenFor(entry)));
        placed.addAll(notYetCatalogued);
        notAClaim.forEach(rule -> placed.add(rule.rule()));
        return placed;
    }

    /** The entry's lead rule, then the rules it covers. */
    private static List<RuleId> spokenFor(ClaimCatalogueEntry entry) {
        return Stream.concat(Stream.of(entry.rule()), entry.covers().stream()).toList();
    }

    /** The timeline's catalogue; a timeline without the file has an empty one. */
    public static ClaimCatalogue of(Timeline timeline) {
        return timeline.claimCatalogue().map(ClaimCatalogue::read)
                .orElseGet(() -> new ClaimCatalogue(List.of(), List.of(), List.of()));
    }

    private static ClaimCatalogue read(String content) {
        JsonNode file;
        try {
            file = MAPPER.readTree(content);
        } catch (JacksonException e) {
            throw new PackValidationException(FILE + ": not well-formed JSON — " + e.getOriginalMessage(), e);
        }
        return PackJson.construct(FILE, () -> new ClaimCatalogue(
                PackJson.array(file, "entries", FILE, element -> entry(element.node(), element.path())),
                PackJson.arrayOrEmpty(file, "notYetCatalogued", FILE, element -> new RuleId(element.node().asString())),
                PackJson.arrayOrEmpty(file, "notAClaim", FILE, element -> PackJson.construct(element.path(),
                        () -> new NotAClaim(new RuleId(PackJson.text(element.node(), "rule", element.path())),
                                PackJson.enumValue(element.node(), "because", element.path(), NotAClaimReason.class))))));
    }

    /** A year's compiled catalogue file (W-118 C2a): each entry with its section's names and its figures. */
    public static List<CompiledClaim> compiled(JsonNode file, String path) {
        return PackJson.array(file, "entries", path, element -> PackJson.construct(element.path(), () -> new CompiledClaim(
                entry(element.node(), element.path()),
                PackJson.text(element.node(), "plainName", element.path()),
                PackJson.text(element.node(), "summary", element.path()),
                figures(PackJson.object(element.node(), "figures", element.path())))));
    }

    private static Map<String, Money> figures(JsonNode node) {
        Map<String, Money> figures = new TreeMap<>();
        node.properties().forEach(figure -> figures.put(figure.getKey(), Money.of(figure.getValue().asString())));
        return figures;
    }

    private static ClaimCatalogueEntry entry(JsonNode node, String path) {
        JsonNode basis = PackJson.object(node, "basis", path);
        return PackJson.construct(path, () -> new ClaimCatalogueEntry(
                new RuleId(PackJson.text(node, "rule", path)),
                PackJson.arrayOrEmpty(node, "covers", path, element -> new RuleId(element.node().asString())),
                PackJson.text(node, "section", path),
                PackJson.enumValue(node, "channel", path, ClaimChannel.class),
                PackJson.optionalText(node, "chapterViiiPart", path)
                        .map(part -> PackJson.enumValue(node, "chapterViiiPart", path, ChapterViiiPart.class)),
                PackJson.enumValue(node, "ifMissingFromForm16", path, AbsenceFromForm16.class),
                new ClaimBasis(
                        ValueJson.provision(PackJson.object(basis, "provision1961", path + ".basis"),
                                path + ".basis.provision1961"),
                        ValueJson.provision(PackJson.object(basis, "provision2025", path + ".basis"),
                                path + ".basis.provision2025")),
                guidance(node, path)));
    }

    private static ClaimGuidance guidance(JsonNode node, String path) {
        return PackJson.construct(path, () -> new ClaimGuidance(
                PackJson.optionalText(node, "appliesTo", path),
                PackJson.array(node, "steps", path, ClaimCatalogue::step),
                PackJson.arrayOrEmpty(node, "askWhom", path, element -> PackJson.construct(element.path(),
                        () -> new ClaimRequest(
                                PackJson.enumValue(element.node(), "whom", element.path(), ClaimActor.class),
                                PackJson.text(element.node(), "askFor", element.path())))),
                PackJson.arrayOrEmpty(node, "evidence", path, element -> PackJson.construct(element.path(),
                        () -> new ClaimEvidence(
                                PackJson.text(element.node(), "name", element.path()),
                                // An absent closedBy reaches the evidence as blank, so I25 refuses it by name
                                PackJson.optionalText(element.node(), "closedBy", element.path()).orElse(""),
                                PackJson.enumValue(element.node(), "from", element.path(), ClaimActor.class),
                                PackJson.enumValue(element.node(), "recordedAs", element.path(),
                                        EvidenceRecord.class)))),
                PackJson.arrayOrEmpty(node, "mistakes", path, ClaimCatalogue::mistake)));
    }

    private static String mistake(PackJson.Element element) {
        if (!element.node().isTextual()) {
            throw new PackValidationException(element.path() + ": a common mistake is a sentence, but was "
                    + element.node());
        }
        return element.node().asText();
    }

    private static ClaimStep step(PackJson.Element element) {
        JsonNode node = element.node();
        String path = element.path();
        return PackJson.construct(path, () -> new ClaimStep(
                PackJson.integer(node, "order", path),
                PackJson.text(node, "instruction", path),
                PackJson.enumValue(node, "actor", path, ClaimActor.class),
                PackJson.enumValue(node, "by", path, ClaimDeadline.class),
                PackJson.optionalText(node, "produces", path),
                PackJson.bool(node, "warnsOfLateReturnBar", path)));
    }

    /**
     * Refuses the catalogue, listing every problem: an entry for, or covering, a rule the timeline does not have, a
     * section it does not have, a figure typed into text, and a placeholder naming a rule the timeline does not have or
     * a field not every version of that rule defines; and, where the timeline has a catalogue, I23: a listed rule the
     * timeline does not have, and a timeline rule the catalogue does not speak for.
     */
    public void verify(Timeline timeline) {
        Set<String> problems = new TreeSet<>();
        if (timeline.claimCatalogue().isPresent()) {
            Set<RuleId> placed = placed();
            Stream.concat(notYetCatalogued.stream(), notAClaim.stream().map(NotAClaim::rule))
                    .filter(rule -> !timeline.rules().containsKey(rule)).forEach(rule ->
                            problems.add(FILE + " lists " + rule + ", which is not in the timeline"));
            timeline.rules().keySet().stream().filter(rule -> !placed.contains(rule)).forEach(rule ->
                    problems.add("I23 — " + rule + " is not catalogued, not listed as not yet catalogued and not"
                            + " listed as not a claim"));
        }
        for (ClaimCatalogueEntry entry : entries) {
            String where = FILE + " " + entry.rule();
            if (!timeline.rules().containsKey(entry.rule())) {
                problems.add(where + " is for a rule that is not in the timeline");
            }
            entry.covers().stream().filter(covered -> !timeline.rules().containsKey(covered)).forEach(covered ->
                    problems.add(where + " covers rule " + covered + ", which is not in the timeline"));
            if (!timeline.sections().containsKey(entry.section())) {
                problems.add(where + " names section " + entry.section() + ", which the section catalogue does not have");
            }
            texts(entry.guidance()).forEach((field, text) -> checkText(where + " " + field, text, timeline, problems));
        }
        if (!problems.isEmpty()) {
            throw new PackValidationException(String.join("; ", problems));
        }
    }

    private static void checkText(String where, String text, Timeline timeline, Set<String> problems) {
        Matcher placeholder = PLACEHOLDER.matcher(text);
        while (placeholder.find()) {
            RuleId rule = new RuleId(placeholder.group(1));
            String field = placeholder.group(2);
            if (!timeline.rules().containsKey(rule)) {
                problems.add(where + " names rule " + rule + ", which is not in the timeline");
            } else if (!everyVersionDefinesAnAmount(timeline.ruleSources().get(rule), field)) {
                problems.add(where + " names " + rule + "." + field + ", which not every version of the rule defines"
                        + " as an amount");
            }
        }
        Matcher typed = SectionCatalogue.TYPED_FIGURE.matcher(placeholder.replaceAll(""));
        if (typed.find()) {
            problems.add(where + " types \"" + typed.group() + "\" — a figure, rate or date comes only from a"
                    + " {{RuleId.field}} placeholder, and a section number only from the basis");
        }
    }

    /** An amount is what a pack can carry as a figure and a page can format as rupees (W-118 C2a). */
    private static boolean everyVersionDefinesAnAmount(String ruleSource, String field) {
        JsonNode versions = MAPPER.readTree(ruleSource).path("versions");
        for (JsonNode version : versions) {
            JsonNode value = version.path("definition").path(field);
            if (!value.isString() || !AMOUNT.matcher(value.asString()).matches()) {
                return false;
            }
        }
        return !versions.isEmpty();
    }

    /** Each {@code RULE-ID.field} the entry's texts name, for the compiler to give its year's figure (W-118 C2a). */
    public static Set<String> placeholdersOf(ClaimCatalogueEntry entry) {
        Set<String> placeholders = new TreeSet<>();
        for (String text : texts(entry.guidance()).values()) {
            Matcher placeholder = PLACEHOLDER.matcher(text);
            while (placeholder.find()) {
                placeholders.add(placeholder.group(1) + "." + placeholder.group(2));
            }
        }
        return placeholders;
    }

    /** Every text a person reads, by its field path, in the order the guidance states them. */
    private static Map<String, String> texts(ClaimGuidance guidance) {
        Map<String, String> texts = new LinkedHashMap<>();
        guidance.appliesTo().ifPresent(text -> texts.put("appliesTo", text));
        for (ClaimStep step : guidance.steps()) {
            texts.put("steps[" + step.order() + "].instruction", step.instruction());
            step.produces().ifPresent(text -> texts.put("steps[" + step.order() + "].produces", text));
        }
        for (int i = 0; i < guidance.askWhom().size(); i++) {
            texts.put("askWhom[" + i + "].askFor", guidance.askWhom().get(i).askFor());
        }
        for (int i = 0; i < guidance.evidence().size(); i++) {
            texts.put("evidence[" + i + "].name", guidance.evidence().get(i).name());
            texts.put("evidence[" + i + "].closedBy", guidance.evidence().get(i).closedBy());
        }
        for (int i = 0; i < guidance.mistakes().size(); i++) {
            texts.put("mistakes[" + i + "]", guidance.mistakes().get(i));
        }
        return texts;
    }
}
