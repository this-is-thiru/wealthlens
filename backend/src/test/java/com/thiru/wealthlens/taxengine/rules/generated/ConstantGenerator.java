package com.thiru.wealthlens.taxengine.rules.generated;

import com.thiru.wealthlens.taxengine.rules.timeline.TimelineReader;
import java.nio.file.Path;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Reads the timeline and produces the text of {@code RuleIds}, {@code ComponentCodes} and
 * {@code NodeIds}.
 *
 * <p>Lives in test scope because nothing at runtime needs it: the constants it writes are committed
 * source, and {@link GeneratedConstantsTest} is what keeps them honest. That is a deliberate
 * departure from {@code 19-engineering-foundations.md}, which generates into
 * {@code target/generated-sources} at build time — see the erratum there. The properties are the
 * same; the cost is not, since a build-time generator would add two plugins to a root pom every
 * module inherits, for one module's convenience.
 */
final class ConstantGenerator {

    static final Path TIMELINE = Path.of("src/main/resources/rulepacks/timeline");
    static final Path OUTPUT_DIR =
            Path.of("src/main/java/com/thiru/wealthlens/taxengine/rules/generated");

    private ConstantGenerator() {
    }

    /** Every rule id in the timeline, sorted. */
    static TreeSet<String> ruleIds() {
        TreeSet<String> ids = new TreeSet<>();
        TimelineReader.read(TIMELINE).rules().keySet().forEach(id -> ids.add(id.value()));
        return ids;
    }

    /** Every node in the timeline's pipeline catalogue, sorted. */
    static TreeSet<String> nodeIds() {
        TreeSet<String> ids = new TreeSet<>();
        TimelineReader.read(TIMELINE).pipelineNodes().keySet().forEach(id -> ids.add(id.value()));
        return ids;
    }

    /** Every node a regime's pipeline names, across the timeline, sorted. */
    static TreeSet<String> regimeNodeRefs() {
        TreeSet<String> refs = new TreeSet<>();
        TimelineReader.read(TIMELINE).regimes().values()
                .forEach(regime -> regime.pipeline().nodeRefs().forEach(ref -> refs.add(ref.value())));
        return refs;
    }

    /** Every component code in the timeline, sorted (D-089). */
    static TreeSet<String> componentCodes() {
        TreeSet<String> codes = new TreeSet<>();
        TimelineReader.read(TIMELINE).components().keySet().forEach(code -> codes.add(code.value()));
        return codes;
    }

    /**
     * The source text of one constants class.
     *
     * @param purposeLines each emitted as its own javadoc line, already short enough that the
     *     formatter has nothing to rewrap — a generated file the formatter edits would make
     *     {@code GeneratedConstantsTest} fail on every build
     */
    static String render(
            String className, String valueType, java.util.List<String> purposeLines, TreeSet<String> values) {
        StringBuilder out = new StringBuilder();
        out.append("package com.thiru.wealthlens.taxengine.rules.generated;\n\n");
        if (!values.isEmpty()) {
            // Only when something uses it: the formatter strips an unused import, and a generated
            // file the formatter edits makes GeneratedConstantsTest fail on every build.
            out.append("import com.thiru.wealthlens.taxengine.rules.id.").append(valueType).append(";\n\n");
        }
        out.append("/**\n")
                .append(" * GENERATED — do not edit. Source: {@code src/main/resources/rulepacks/&#42;&#42;/*.json}.\n")
                .append(" *\n")
                .append(" * <p>").append(purposeLines.getFirst()).append("\n");
        for (String line : purposeLines.subList(1, purposeLines.size())) {
            out.append(" * ").append(line).append("\n");
        }
        out.append(" *\n")
                .append(" * <p>Regenerate with {@code ./mvnw test -pl backend -Dtest=GeneratedConstantsTest\n")
                .append(" * -Dtaxengine.rules.regenerate=true}. {@code GeneratedConstantsTest} fails if this file and the\n")
                .append(" * packs disagree, so a stale copy cannot survive a build.\n")
                .append(" */\n")
                .append("public final class ").append(className).append(" {\n\n");

        if (values.isEmpty()) {
            out.append("    // No values in any pack yet.\n\n");
        }
        for (String value : values) {
            out.append("    public static final ").append(valueType).append(' ')
                    .append(constantName(value)).append(" = new ").append(valueType)
                    .append("(\"").append(value).append("\");\n");
        }
        if (!values.isEmpty()) {
            out.append('\n');
        }
        out.append("    private ").append(className).append("() {\n    }\n}\n");
        return out.toString();
    }

    /** {@code SAL-HRA-001} becomes {@code SAL_HRA_001}. */
    static String constantName(String value) {
        return value.replace('-', '_').replace('.', '_').toUpperCase(Locale.ROOT);
    }
}
