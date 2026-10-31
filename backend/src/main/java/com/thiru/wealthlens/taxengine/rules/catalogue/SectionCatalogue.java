package com.thiru.wealthlens.taxengine.rules.catalogue;

import com.thiru.wealthlens.taxengine.rules.coverage.Coverage;
import com.thiru.wealthlens.taxengine.rules.coverage.CoverageRegister;
import com.thiru.wealthlens.taxengine.rules.coverage.SectionRef;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.json.PackJson;
import com.thiru.wealthlens.taxengine.rules.json.ValueJson;
import com.thiru.wealthlens.taxengine.rules.timeline.Timeline;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The section catalogue (D-042, D-060), read from the timeline's {@code content/sections/}, and the
 * build's check of SC-1 to SC-4 ({@code 00-BRIEF.md} §6.3).
 *
 * <p>SC-4 is enforced on the way in: an entry authoring {@code availability} or {@code history} is
 * refused, since both are derived from rule versions. {@link #verify} checks the rest against the
 * timeline, because a reference is only a link if what it names exists.
 */
public record SectionCatalogue(Map<String, SectionCatalogueEntry> entries) {

    private static final String DIRECTORY = "content/sections/";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final List<String> DERIVED_FIELDS = List.of("availability", "history");
    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{\\{([A-Z]{2,8}(?:-[A-Z0-9]{1,12}){1,5})\\.[a-zA-Z][a-zA-Z0-9]*}}");
    /** A figure, rate or date typed into text, or a placeholder that is not well-formed. */
    private static final Pattern TYPED_FIGURE =
            Pattern.compile("[0-9₹%{}]|\\bRs\\b|\\blakhs?\\b|\\bcrores?\\b", Pattern.CASE_INSENSITIVE);

    public SectionCatalogue {
        entries = Map.copyOf(entries);
    }

    /** The timeline's catalogue; a timeline with no entries has an empty one. */
    public static SectionCatalogue of(Timeline timeline) {
        Map<String, SectionCatalogueEntry> entries = new HashMap<>();
        timeline.sections().forEach((key, content) -> entries.put(key, entry(DIRECTORY + key + ".json", content)));
        return new SectionCatalogue(entries);
    }

    private static SectionCatalogueEntry entry(String path, String content) {
        JsonNode node;
        try {
            node = MAPPER.readTree(content);
        } catch (JacksonException e) {
            throw new PackValidationException(path + ": not well-formed JSON — " + e.getOriginalMessage(), e);
        }
        for (String derived : DERIVED_FIELDS) {
            if (node.has(derived)) {
                throw new PackValidationException(path + "." + derived + ": SC-4 — " + derived
                        + " is derived from the rules' versions, never authored");
            }
        }
        return PackJson.construct(path, () -> new SectionCatalogueEntry(
                PackJson.text(node, "key", path),
                ValueJson.provision(PackJson.object(node, "provision1961", path), path + ".provision1961"),
                ValueJson.provision(PackJson.object(node, "provision2025", path), path + ".provision2025"),
                PackJson.text(node, "plainName", path),
                PackJson.text(node, "summary", path),
                PackJson.array(node, "routes", path, SectionCatalogue::route),
                PackJson.arrayOrEmpty(node, "disqualifiers", path, SectionCatalogue::disqualifier)));
    }

    private static Route route(PackJson.Element element) {
        JsonNode node = element.node();
        String path = element.path();
        return PackJson.construct(path, () -> new Route(
                PackJson.text(node, "name", path),
                PackJson.text(node, "description", path),
                PackJson.array(node, "conditions", path, e -> enforcedBy(e.node(), e.path()))));
    }

    private static Disqualifier disqualifier(PackJson.Element element) {
        JsonNode node = element.node();
        String path = element.path();
        return PackJson.construct(path, () -> new Disqualifier(
                PackJson.enumValue(node, "kind", path, DisqualifierKind.class),
                PackJson.text(node, "description", path),
                enforcedBy(PackJson.object(node, "enforcedBy", path), path + ".enforcedBy")));
    }

    private static EnforcedBy enforcedBy(JsonNode node, String path) {
        String type = PackJson.tag(node, path, "ByRule", "ByCondition", "NotModelled");
        return PackJson.construct(path, () -> switch (type) {
            case "ByRule" -> new EnforcedBy.ByRule(new RuleId(PackJson.text(node, "rule", path)));
            case "ByCondition" -> EnforcedBy.ByCondition.named(PackJson.text(node, "condition", path));
            default -> new EnforcedBy.NotModelled(PackJson.text(node, "reason", path));
        });
    }

    /** The register sections the entries' provisions cite — what SC-1 counts as catalogued. */
    public Set<SectionRef> catalogued() {
        return entries.values().stream()
                .flatMap(entry -> Stream.of(entry.provision1961(), entry.provision2025()))
                .map(SectionRef::citedBy)
                .flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Refuses the catalogue, listing every problem: a {@code MODELLED} section with no entry (SC-1), a
     * figure typed into text (SC-2), and a rule named by a route, disqualifier or placeholder that is not
     * in the timeline (SC-3).
     */
    public void verify(Timeline timeline, CoverageRegister register) {
        Set<String> problems = new TreeSet<>();
        Set<SectionRef> catalogued = catalogued();
        register.sections().forEach((section, coverage) -> {
            if (coverage instanceof Coverage.Modelled && !catalogued.contains(section)) {
                problems.add("SC-1 — " + section + " is MODELLED and has no catalogue entry");
            }
        });
        entries.values().forEach(entry -> {
            String path = DIRECTORY + entry.key() + ".json";
            texts(entry).forEach((field, text) -> checkText(path + " " + field, text, timeline, problems));
            references(entry).forEach((field, enforcedBy) -> {
                if (enforcedBy instanceof EnforcedBy.ByRule byRule) {
                    requireInTimeline(path + " " + field, byRule.rule(), timeline, problems);
                }
            });
        });
        if (!problems.isEmpty()) {
            throw new PackValidationException("content/sections: " + String.join("; ", problems));
        }
    }

    private static void checkText(String where, String text, Timeline timeline, Set<String> problems) {
        Matcher placeholder = PLACEHOLDER.matcher(text);
        while (placeholder.find()) {
            requireInTimeline(where, new RuleId(placeholder.group(1)), timeline, problems);
        }
        Matcher typed = TYPED_FIGURE.matcher(placeholder.replaceAll(""));
        if (typed.find()) {
            problems.add(where + " types \"" + typed.group() + "\" — SC-2: a figure, rate or date comes only from a"
                    + " {{RuleId.field}} placeholder, and a section number only from the provisions");
        }
    }

    private static void requireInTimeline(String where, RuleId rule, Timeline timeline, Set<String> problems) {
        if (!timeline.rules().containsKey(rule)) {
            problems.add(where + " names rule " + rule + ", which is not in the timeline — SC-3: link a rule that"
                    + " exists, or say NotModelled with a reason");
        }
    }

    /** Every text a person reads, by its field path, in the order the entry states them. */
    private static Map<String, String> texts(SectionCatalogueEntry entry) {
        Map<String, String> texts = new LinkedHashMap<>();
        texts.put("plainName", entry.plainName());
        texts.put("summary", entry.summary());
        for (int i = 0; i < entry.routes().size(); i++) {
            texts.put("routes[" + i + "].name", entry.routes().get(i).name());
            texts.put("routes[" + i + "].description", entry.routes().get(i).description());
        }
        for (int i = 0; i < entry.disqualifiers().size(); i++) {
            texts.put("disqualifiers[" + i + "].description", entry.disqualifiers().get(i).description());
        }
        references(entry).forEach((field, enforcedBy) -> {
            if (enforcedBy instanceof EnforcedBy.NotModelled notModelled) {
                texts.put(field + ".reason", notModelled.reason());
            }
        });
        return texts;
    }

    /** Every route condition and disqualifier link, by its field path. */
    private static Map<String, EnforcedBy> references(SectionCatalogueEntry entry) {
        Map<String, EnforcedBy> references = new LinkedHashMap<>();
        for (int i = 0; i < entry.routes().size(); i++) {
            List<EnforcedBy> conditions = entry.routes().get(i).conditions();
            for (int j = 0; j < conditions.size(); j++) {
                references.put("routes[" + i + "].conditions[" + j + "]", conditions.get(j));
            }
        }
        for (int i = 0; i < entry.disqualifiers().size(); i++) {
            references.put("disqualifiers[" + i + "].enforcedBy", entry.disqualifiers().get(i).enforcedBy());
        }
        return references;
    }
}
