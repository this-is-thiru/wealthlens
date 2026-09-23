package com.thiru.wealthlens.taxengine.rules.generated;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Reads every rule pack and produces the text of {@code RuleIds} and {@code ComponentCodes}.
 *
 * <p>Lives in test scope because nothing at runtime needs it: the constants it writes are committed
 * source, and {@link GeneratedConstantsTest} is what keeps them honest. That is a deliberate
 * departure from {@code 19-engineering-foundations.md}, which generates into
 * {@code target/generated-sources} at build time — see the erratum there. The properties are the
 * same; the cost is not, since a build-time generator would add two plugins to a root pom every
 * module inherits, for one module's convenience.
 */
final class ConstantGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    static final Path PACK_ROOT = Path.of("src/main/resources/rulepacks");
    static final Path OUTPUT_DIR =
            Path.of("src/main/java/com/thiru/wealthlens/taxengine/rules/generated");

    private ConstantGenerator() {
    }

    /** Every rule id across every pack, sorted, deduplicated. */
    static TreeSet<String> ruleIds() {
        return collect("law/rates.json", "rules", "ruleId");
    }

    /** Every component code across every pack, sorted, deduplicated. */
    static TreeSet<String> componentCodes() {
        return collect("exec/components.json", "components", "code");
    }

    private static TreeSet<String> collect(String file, String arrayField, String idField) {
        TreeSet<String> found = new TreeSet<>();
        for (Path pack : packDirectories()) {
            Path source = pack.resolve(file);
            if (!Files.exists(source)) {
                continue;
            }
            JsonNode array = read(source).get(arrayField);
            if (array == null) {
                continue;
            }
            for (JsonNode element : array) {
                JsonNode id = element.get(idField);
                if (id != null && !id.isNull()) {
                    found.add(id.asText());
                }
            }
        }
        return found;
    }

    static TreeSet<Path> packDirectories() {
        try (Stream<Path> years = Files.list(PACK_ROOT)) {
            return years.filter(Files::isDirectory).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list " + PACK_ROOT.toAbsolutePath(), e);
        }
    }

    private static JsonNode read(Path path) {
        try {
            return MAPPER.readTree(Files.readString(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + path, e);
        }
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
