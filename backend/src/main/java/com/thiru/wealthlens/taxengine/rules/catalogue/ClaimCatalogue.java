package com.thiru.wealthlens.taxengine.rules.catalogue;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.json.PackJson;
import com.thiru.wealthlens.taxengine.rules.json.ValueJson;
import com.thiru.wealthlens.taxengine.rules.timeline.Timeline;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The claim catalogue ({@code DR 22}; W-117a, D-699), read from the timeline's {@code content/claim-catalogue.json}, and
 * the build's check of it.
 *
 * <p>An entry checks itself as it is read: its steps' numbering, the late-return warning its Part needs (I24), what
 * closes each piece of evidence (I25) and each step's deadline (I26). {@link #verify} checks the rest against the
 * timeline, because a rule, a section or a placeholder is only a link if what it names exists.
 */
public record ClaimCatalogue(List<ClaimCatalogueEntry> entries) {

    /** The catalogue's file, in a timeline and in a year's pack. */
    public static final String FILE = "content/claim-catalogue.json";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{\\{([A-Z]{2,8}(?:-[A-Z0-9]{1,12}){1,5})\\.([a-zA-Z][a-zA-Z0-9]*)}}");

    public ClaimCatalogue {
        entries = List.copyOf(entries);
        Set<RuleId> seen = new TreeSet<>((left, right) -> left.value().compareTo(right.value()));
        for (ClaimCatalogueEntry entry : entries) {
            if (!seen.add(entry.rule())) {
                throw new IllegalArgumentException("A claim catalogue has one entry for a rule, but " + entry.rule()
                        + " has two");
            }
        }
    }

    /** The timeline's catalogue; a timeline without the file has an empty one. */
    public static ClaimCatalogue of(Timeline timeline) {
        return timeline.claimCatalogue().map(ClaimCatalogue::read).orElseGet(() -> new ClaimCatalogue(List.of()));
    }

    private static ClaimCatalogue read(String content) {
        JsonNode file;
        try {
            file = MAPPER.readTree(content);
        } catch (JacksonException e) {
            throw new PackValidationException(FILE + ": not well-formed JSON — " + e.getOriginalMessage(), e);
        }
        return PackJson.construct(FILE, () -> new ClaimCatalogue(
                PackJson.array(file, "entries", FILE, element -> entry(element.node(), element.path()))));
    }

    private static ClaimCatalogueEntry entry(JsonNode node, String path) {
        JsonNode basis = PackJson.object(node, "basis", path);
        return PackJson.construct(path, () -> new ClaimCatalogueEntry(
                new RuleId(PackJson.text(node, "rule", path)),
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
                                PackJson.optionalText(element.node(), "closedBy", element.path()).orElse("")))),
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
     * Refuses the catalogue, listing every problem: an entry for a rule or a section the timeline does not have, a
     * figure typed into text, and a placeholder naming a rule the timeline does not have or a field not every version
     * of that rule defines.
     */
    public void verify(Timeline timeline) {
        Set<String> problems = new TreeSet<>();
        for (ClaimCatalogueEntry entry : entries) {
            String where = FILE + " " + entry.rule();
            if (!timeline.rules().containsKey(entry.rule())) {
                problems.add(where + " is for a rule that is not in the timeline");
            }
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
            } else if (!everyVersionDefines(timeline.ruleSources().get(rule), field)) {
                problems.add(where + " names " + rule + "." + field + ", which not every version of the rule defines");
            }
        }
        Matcher typed = SectionCatalogue.TYPED_FIGURE.matcher(placeholder.replaceAll(""));
        if (typed.find()) {
            problems.add(where + " types \"" + typed.group() + "\" — a figure, rate or date comes only from a"
                    + " {{RuleId.field}} placeholder, and a section number only from the basis");
        }
    }

    private static boolean everyVersionDefines(String ruleSource, String field) {
        JsonNode versions = MAPPER.readTree(ruleSource).path("versions");
        for (JsonNode version : versions) {
            if (!version.path("definition").has(field)) {
                return false;
            }
        }
        return !versions.isEmpty();
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
