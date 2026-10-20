package com.thiru.wealthlens.taxengine.rules.timeline;

import com.thiru.wealthlens.taxengine.rules.component.ComponentDefinition;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.json.PackJson;
import com.thiru.wealthlens.taxengine.rules.json.RegimeJson;
import com.thiru.wealthlens.taxengine.rules.json.RegistryJson;
import com.thiru.wealthlens.taxengine.rules.json.RuleJson;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.regime.DisallowanceSetId;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads a {@link Timeline} from a directory, one file per identity, each file's layer given by its
 * directory.
 *
 * <p>Refuses the whole timeline, naming the file, rather than skip anything: a file in a directory
 * this reader does not know is content that would silently never reach a rule pack. Directories are
 * added with their first content and its reader (D-053) — {@code forms/} has none yet;
 * {@code exec/components} is read ahead of its first content, because each component arrives with the
 * first rule that reads it (D-089); {@code content/} has the coverage register, the section catalogue and
 * the Acts' section lists.
 *
 * <p>Each file is named for the identity it holds, which is also what keeps one identity in one file:
 * a second file claiming it has a different name, and is refused for it.
 */
public final class TimelineReader {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String RULES = "law/rules";
    private static final String REGIMES = "law/regimes";
    /** The regimes' shared disallowance sets, one per file, a version per span of years (D-165). */
    private static final String DISALLOWANCES = "law/disallowances";
    private static final String PIPELINES = "exec/pipelines";
    /** The pay components, one per file (D-089). */
    private static final String COMPONENTS = "exec/components";
    /** The section catalogue, carried verbatim for its own reader (D-060). */
    private static final String SECTIONS = "content/sections";
    /** Each Act's section list, carried verbatim for the coverage register (D-066). */
    private static final String ACTS = "content/acts";
    private static final List<String> KNOWN_DIRECTORIES = List.of(RULES, REGIMES, DISALLOWANCES, PIPELINES, COMPONENTS, SECTIONS,
            ACTS);
    /** The one content file: the coverage register, carried verbatim for its own reader (D-059). */
    private static final String COVERAGE = "content/coverage.json";
    /** The rule sets every year's pack declares; each rule row names its own (D-252). */
    private static final String RULE_SETS = "law/rule-sets.json";

    private TimelineReader() {
    }

    public static Timeline read(Path root) {
        Contents contents = new Contents();
        for (Path file : files(root)) {
            String path = root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
            if (RULE_SETS.equals(path)) {
                String content = contentOf(file);
                parse(content, path);
                contents.ruleSets(content);
                continue;
            }
            if (COVERAGE.equals(path)) {
                String content = contentOf(file);
                parse(content, path);
                contents.coverage(content);
                continue;
            }
            String directory = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
            if (!KNOWN_DIRECTORIES.contains(directory) || !path.endsWith(".json")) {
                throw new PackValidationException(
                        path + ": not a timeline file; a timeline holds only .json files under "
                                + KNOWN_DIRECTORIES + ", " + RULE_SETS + " and " + COVERAGE);
            }
            String content = contentOf(file);
            String stem = file.getFileName().toString().replaceFirst("\\.json$", "");
            contents.add(directory, path, stem, parse(content, path), content);
        }
        return contents.timeline();
    }

    /** What the files hold, gathered by directory as they are read, then frozen into a {@link Timeline}. */
    private static final class Contents {

        private final Map<RuleId, RuleRow> rules = new HashMap<>();
        private final Map<RegimeCode, RegimeSpec> regimes = new HashMap<>();
        private final Map<RuleId, String> ruleSources = new HashMap<>();
        private final Map<RegimeCode, String> regimeSources = new HashMap<>();
        private final Map<DisallowanceSetId, String> disallowanceSetSources = new HashMap<>();
        private final Map<ComponentCode, ComponentDefinition> components = new HashMap<>();
        private final Map<ComponentCode, String> componentSources = new HashMap<>();
        private final Map<NodeId, String> pipelineNodes = new HashMap<>();
        private final Map<String, String> sections = new HashMap<>();
        private final Map<String, String> acts = new HashMap<>();
        private Optional<String> coverage = Optional.empty();
        private Optional<String> ruleSets = Optional.empty();

        void ruleSets(String content) {
            ruleSets = Optional.of(content);
        }

        void coverage(String content) {
            coverage = Optional.of(content);
        }

        void add(String directory, String path, String stem, JsonNode node, String content) {
            switch (directory) {
                case RULES -> addRule(path, stem, node, content);
                case REGIMES -> addRegime(path, stem, node, content);
                case DISALLOWANCES -> addDisallowanceSet(path, stem, node, content);
                case PIPELINES -> addPipelineNode(path, stem, node, content);
                case COMPONENTS -> addComponent(path, stem, node, content);
                case SECTIONS -> addSection(path, stem, node, content);
                case ACTS -> addAct(path, stem, node, content);
                default -> throw new IllegalStateException(directory + " is a known directory with no reader");
            }
        }

        private void addRule(String path, String stem, JsonNode node, String content) {
            RuleRow row = RuleJson.rule(node, path);
            requireNamedFor(path, stem, row.id().value());
            requireNoOverlap(path, row);
            rules.put(row.id(), row);
            ruleSources.put(row.id(), content);
        }

        private void addRegime(String path, String stem, JsonNode node, String content) {
            // Each election version is read once; the timeline keeps the last for code and lifecycle
            RegimeSpec regime = RegimeElections.everyVersion(node, path).stream()
                    .map(version -> RegimeJson.regime(version, path))
                    .reduce((earlier, later) -> later)
                    .orElseThrow(() -> new PackValidationException(
                            path + ": a regime needs at least one election version"));
            requireNamedFor(path, stem, regime.code().value());
            regimes.put(regime.code(), regime);
            regimeSources.put(regime.code(), content);
        }

        private void addDisallowanceSet(String path, String stem, JsonNode node, String content) {
            DisallowanceSetId id = PackJson.construct(path,
                    () -> new DisallowanceSetId(PackJson.text(node, "id", path)));
            requireNamedFor(path, stem, id.value());
            DisallowanceSetVersions.everyVersion(node, path);
            disallowanceSetSources.put(id, content);
        }

        private void addPipelineNode(String path, String stem, JsonNode node, String content) {
            NodeId id = PackJson.construct(path, () -> new NodeId(PackJson.text(node, "nodeId", path)));
            requireNamedFor(path, stem, id.value());
            pipelineNodes.put(id, content);
        }

        private void addComponent(String path, String stem, JsonNode node, String content) {
            ComponentDefinition component = RegistryJson.component(node, path);
            requireNamedFor(path, stem, component.code().value());
            components.put(component.code(), component);
            componentSources.put(component.code(), content);
        }

        private void addSection(String path, String stem, JsonNode node, String content) {
            String key = PackJson.text(node, "key", path);
            requireNamedFor(path, stem, key);
            sections.put(key, content);
        }

        private void addAct(String path, String stem, JsonNode node, String content) {
            String act = PackJson.text(node, "act", path);
            requireNamedFor(path, stem, act);
            acts.put(act, content);
        }

        Timeline timeline() {
            return new Timeline(rules, ruleSets, regimes, ruleSources, regimeSources, disallowanceSetSources, components, componentSources,
                    pipelineNodes, coverage, sections, acts);
        }
    }

    private static void requireNamedFor(String path, String stem, String identity) {
        if (!stem.equals(identity)) {
            throw new PackValidationException(
                    path + ": holds " + identity + "; a timeline file is named for the one identity it holds");
        }
    }

    /**
     * Two versions may not claim the same day. A gap is allowed, unlike within one pack's year (I13):
     * across all time a rule may lapse and return.
     */
    private static void requireNoOverlap(String path, RuleRow row) {
        List<DateRange> windows = row.versions().stream()
                .map(RuleVersion::validity)
                .map(validity -> validity.window())
                .flatMap(Optional::stream)
                .toList();
        for (int i = 0; i < windows.size(); i++) {
            for (int j = i + 1; j < windows.size(); j++) {
                if (windows.get(i).overlaps(windows.get(j))) {
                    throw new PackValidationException(
                            path + ": rule " + row.id() + " has versions that overlap, " + windows.get(i)
                                    + " and " + windows.get(j) + "; one day may have one version");
                }
            }
        }
    }

    private static List<Path> files(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list the timeline at " + root.toAbsolutePath(), e);
        }
    }

    private static String contentOf(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file.toAbsolutePath(), e);
        }
    }

    private static JsonNode parse(String content, String path) {
        try {
            return MAPPER.readTree(content);
        } catch (JacksonException e) {
            throw new PackValidationException(path + ": not well-formed JSON — " + e.getOriginalMessage(), e);
        }
    }
}
