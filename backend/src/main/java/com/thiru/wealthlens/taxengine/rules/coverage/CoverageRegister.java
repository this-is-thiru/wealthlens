package com.thiru.wealthlens.taxengine.rules.coverage;

import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.json.PackJson;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.registry.PublicationStatus;
import com.thiru.wealthlens.taxengine.rules.temporal.Provision;
import com.thiru.wealthlens.taxengine.rules.timeline.Timeline;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The register of the Acts' sections, each with exactly one classification (D-038, D-059), so which
 * sections are modelled is a number the build reports rather than one anyone has to remember.
 *
 * <p>Read from the timeline's {@code content/coverage.json}. {@link #verify} is the gate the build
 * runs: a rule or regime citing a section the register does not list, and a {@code MODELLED} section
 * whose rules are absent or unpublished, each refuse the timeline.
 */
public record CoverageRegister(Map<SectionRef, Coverage> sections) {

    private static final String PATH = "content/coverage.json";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final String ACTS = "content/acts/";

    /** The five words a register entry may use, one per {@link Coverage} kind. */
    private enum Classification {
        MODELLED,
        PLANNED,
        DEFERRED,
        NOT_COMPUTATIONAL,
        OUT_OF_SCOPE;

        static Classification of(Coverage coverage) {
            return switch (coverage) {
                case Coverage.Modelled m -> MODELLED;
                case Coverage.Planned p -> PLANNED;
                case Coverage.Deferred d -> DEFERRED;
                case Coverage.NotComputational n -> NOT_COMPUTATIONAL;
                case Coverage.OutOfScope o -> OUT_OF_SCOPE;
            };
        }
    }

    public CoverageRegister {
        sections = Map.copyOf(sections);
    }

    /** The timeline's register; a timeline without one has nothing to check against, and is refused. */
    public static CoverageRegister of(Timeline timeline) {
        String content = timeline.coverage()
                .orElseThrow(() -> new PackValidationException(PATH + ": the timeline has no coverage register"));
        JsonNode root;
        try {
            root = MAPPER.readTree(content);
        } catch (JacksonException e) {
            throw new PackValidationException(PATH + ": not well-formed JSON — " + e.getOriginalMessage(), e);
        }
        Map<SectionRef, Coverage> sections = new HashMap<>();
        for (PackJson.Element entry : PackJson.array(root, "sections", PATH, e -> e)) {
            SectionRef section = PackJson.construct(entry.path(), () -> new SectionRef(
                    PackJson.enumValue(entry.node(), "act", entry.path(), SectionRef.Act.class),
                    PackJson.text(entry.node(), "section", entry.path())));
            if (sections.put(section, coverage(entry)) != null) {
                throw new PackValidationException(entry.path() + ": " + section + " is listed twice; a section has"
                        + " exactly one classification");
            }
        }
        return new CoverageRegister(sections);
    }

    private static Coverage coverage(PackJson.Element entry) {
        JsonNode node = entry.node();
        String path = entry.path();
        Classification classification = PackJson.enumValue(node, "classification", path, Classification.class);
        return PackJson.construct(path, () -> switch (classification) {
            case MODELLED -> new Coverage.Modelled(
                    PackJson.array(node, "rules", path, e -> new RuleId(e.node().asString())),
                    PackJson.text(node, "manager", path));
            case PLANNED -> new Coverage.Planned(
                    Set.copyOf(PackJson.array(node, "assesseeTypes", path, e -> PersonType.valueOf(e.node().asString()))),
                    PackJson.optionalText(node, "backlogItem", path).orElse(""));
            case DEFERRED -> new Coverage.Deferred(
                    PackJson.enumValue(node, "tier", path, Coverage.ScopeTier.class),
                    Set.copyOf(PackJson.arrayOrEmpty(node, "assesseeTypes", path, e -> PersonType.valueOf(e.node().asString()))));
            case NOT_COMPUTATIONAL -> new Coverage.NotComputational();
            case OUT_OF_SCOPE -> new Coverage.OutOfScope(
                    PackJson.text(node, "reason", path), PackJson.text(node, "decidedBy", path));
        });
    }

    /** How many sections of each Act carry each classification — the number the build reports (D-038). */
    public Map<SectionRef.Act, Map<String, Long>> countsByClassification() {
        Map<SectionRef.Act, Map<String, Long>> counts = new EnumMap<>(SectionRef.Act.class);
        sections.forEach((section, coverage) -> counts
                .computeIfAbsent(section.act(), act -> new TreeMap<>())
                .merge(Classification.of(coverage).name(), 1L, Long::sum));
        return counts;
    }

    /**
     * Refuses the timeline, listing every section it cites that is unregistered or falsely modelled,
     * and, for each Act whose section list the timeline carries, every section the register misses or
     * invents.
     */
    public void verify(Timeline timeline) {
        TreeSet<String> problems = new TreeSet<>();
        timeline.rules().values().forEach(row -> row.versions().forEach(version -> {
            requireRegistered("rule " + row.id(), version.provision1961(), problems);
            requireRegistered("rule " + row.id(), version.provision2025(), problems);
        }));
        timeline.regimes().values().forEach(regime -> {
            requireRegistered("regime " + regime.code(), regime.provision1961(), problems);
            requireRegistered("regime " + regime.code(), regime.provision2025(), problems);
        });
        sections.forEach((section, coverage) -> {
            if (coverage instanceof Coverage.Modelled modelled) {
                modelled.rules().forEach(rule -> requirePublished(section, rule, timeline, problems));
            }
        });
        timeline.acts().forEach((code, content) -> requireEveryActSection(code, content, problems));
        if (!problems.isEmpty()) {
            throw new PackValidationException(PATH + ": " + problems.stream().collect(Collectors.joining("; ")));
        }
    }

    /**
     * The register lists exactly the Act's sections in force: every listed section not marked
     * {@code omitted}, and none that is (D-067). The list itself must run without a gap, omitted
     * numbers included, so a gap is a bad list rather than an omitted section.
     */
    private void requireEveryActSection(String code, String content, Set<String> problems) {
        String path = ACTS + code + ".json";
        SectionRef.Act act = PackJson.construct(path, () -> SectionRef.Act.valueOf(code));
        Map<SectionRef, Boolean> omittedBySection = actSections(path, act, content);
        requireContinuous(path, omittedBySection.keySet(), problems);
        omittedBySection.forEach((section, omitted) -> {
            if (omitted && sections.containsKey(section)) {
                problems.add(section + " is omitted from the Act and must not be in the register");
            } else if (!omitted && !sections.containsKey(section)) {
                problems.add(section + " is a section of the Act and not in the register");
            }
        });
        sections.keySet().stream().filter(section -> section.act() == act && !omittedBySection.containsKey(section))
                .forEach(section -> problems.add(section + " is in the register and not a section of the Act"));
    }

    private static Map<SectionRef, Boolean> actSections(String path, SectionRef.Act act, String content) {
        JsonNode root;
        try {
            root = MAPPER.readTree(content);
        } catch (JacksonException e) {
            throw new PackValidationException(path + ": not well-formed JSON — " + e.getOriginalMessage(), e);
        }
        Map<SectionRef, Boolean> omittedBySection = new HashMap<>();
        PackJson.array(root, "sections", path, e -> Map.entry(
                        PackJson.construct(e.path(), () -> new SectionRef(act, PackJson.text(e.node(), "section", e.path()))),
                        e.node().path("omitted").asBoolean(false)))
                .forEach(entry -> omittedBySection.put(entry.getKey(), entry.getValue()));
        return omittedBySection;
    }

    /** Plain-numbered sections run 1 to the highest without a gap; a gap is a bad list, not a short Act. */
    private static void requireContinuous(String path, Set<SectionRef> inAct, Set<String> problems) {
        Set<Integer> numbers = inAct.stream()
                .map(SectionRef::section)
                .filter(section -> section.chars().allMatch(Character::isDigit))
                .map(Integer::valueOf)
                .collect(Collectors.toSet());
        int highest = numbers.stream().mapToInt(Integer::intValue).max().orElse(0);
        List<Integer> missing = IntStream.rangeClosed(1, highest).filter(n -> !numbers.contains(n)).boxed().toList();
        if (!missing.isEmpty()) {
            problems.add(path + " is not continuous: no section " + missing);
        }
    }

    private void requireRegistered(String citer, Provision provision, Set<String> problems) {
        SectionRef.citedBy(provision)
                .filter(section -> !sections.containsKey(section))
                .ifPresent(section -> problems.add(citer + " cites " + section + ", which the register does not list"));
    }

    private static void requirePublished(SectionRef section, RuleId rule, Timeline timeline, Set<String> problems) {
        RuleRow row = timeline.rules().get(rule);
        if (row == null) {
            problems.add(section + " is MODELLED by " + rule + ", which is not in the timeline");
        } else if (row.versions().stream().noneMatch(v -> v.publicationStatus() == PublicationStatus.PUBLISHED)) {
            problems.add(section + " is MODELLED by " + rule + ", which has no PUBLISHED version");
        }
    }
}
