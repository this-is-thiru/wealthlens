package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The api-collection, the API's only client, matches the contract the code serves (W-359b): every request names an operation the spec
 * has — its method and path — and a JSON body sends only fields that operation's request type declares, since the app refuses any other
 * as unknown. A request the code no longer serves, or a body that kept a removed field, fails the build instead of a manual run.
 */
class ApiCollectionContractIntegrationTest extends AbstractIntegrationTest {

    private static final Path COLLECTION = Path.of("..", "api-collection");

    /** Below this the folder is missing or cut short, and a check over nothing must not pass. 114 requests at W-359b. */
    private static final int AT_LEAST = 100;

    /** Not files of requests: the collection's own settings. */
    private static final Set<String> NOT_REQUESTS = Set.of("folder.yml", "opencollection.yml");

    /** Folders of settings, not requests: the environments a request's variables are read from. */
    private static final Set<Path> NOT_REQUEST_FOLDERS = Set.of(COLLECTION.resolve(".git"), COLLECTION.resolve("environments"));

    /**
     * Not in the spec: the notice as a page, served outside the zones as RouteAccessIntegrationTest lists it (W-247), and the spec's own
     * route, which springdoc leaves out of what it describes.
     */
    private static final Set<String> OUTSIDE_THE_SPEC = Set.of("/privacy", OpenApiIntegrationTest.SPEC);

    private static final Pattern ORIGIN = Pattern.compile("^(\\{\\{[^}]+}}|https?://[^/]+)");
    private static final Pattern COMMENT_LINE = Pattern.compile("(?m)^\\s*//.*$");

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void everyRequest_whenReadAgainstTheSpec_namesAnOperationAndSendsOnlyItsFields() throws IOException, InterruptedException {
        // Given
        JsonNode spec = OpenApiIntegrationTest.spec(generateToken("admin@example.com", "SUPER_USER"));
        List<Path> files = requestFiles();

        // When
        List<String> mismatches = new ArrayList<>();
        for (Path file : files) {
            mismatch(spec, file).ifPresent(mismatches::add);
        }

        // Then
        assertThat(files).as("the api-collection's requests, read from " + COLLECTION.toAbsolutePath()).hasSizeGreaterThanOrEqualTo(AT_LEAST);
        assertThat(mismatches).as("requests the code no longer serves, or bodies with fields it refuses").isEmpty();
    }

    private Optional<String> mismatch(JsonNode spec, Path file) throws IOException {
        Map<String, Object> http = http(file);
        String method = String.valueOf(http.get("method")).toLowerCase(Locale.ROOT);
        String path = ORIGIN.matcher(String.valueOf(http.get("url"))).replaceFirst("").split("\\?")[0];
        String name = COLLECTION.relativize(file) + ": " + method.toUpperCase(Locale.ROOT) + " " + path;
        if (OUTSIDE_THE_SPEC.contains(path)) {
            return Optional.empty();
        }
        Optional<JsonNode> operation = operation(spec, method, path);
        if (operation.isEmpty()) {
            return Optional.of(name + " — no such operation");
        }
        List<String> refused = refusedFields(spec, operation.get(), http.get("body"));
        return refused.isEmpty() ? Optional.empty() : Optional.of(name + " — fields the request type lacks: " + refused);
    }

    private static Optional<JsonNode> operation(JsonNode spec, String method, String path) {
        for (Map.Entry<String, JsonNode> template : spec.get("paths").properties()) {
            String pattern = "^" + template.getKey().replaceAll("\\{[^}]+}", "[^/]+") + "$";
            if (path.matches(pattern) && template.getValue().has(method)) {
                return Optional.of(template.getValue().get(method));
            }
        }
        return Optional.empty();
    }

    /** A JSON object body's top-level fields its operation's request type does not declare; nothing for any other body. */
    private List<String> refusedFields(JsonNode spec, JsonNode operation, Object body) {
        if (!(body instanceof Map<?, ?> bodyFields) || !"json".equals(bodyFields.get("type")) || bodyFields.get("data") == null) {
            return List.of();
        }
        JsonNode sent = json.readTree(COMMENT_LINE.matcher(String.valueOf(bodyFields.get("data"))).replaceAll(""));
        JsonNode declared = schema(spec, operation.path("requestBody").path("content").path("application/json").path("schema"));
        if (!sent.isObject() || !declared.has("properties")) {
            return List.of();
        }
        return sent.propertyNames().stream().filter(field -> !declared.get("properties").has(field)).toList();
    }

    private static JsonNode schema(JsonNode spec, JsonNode schema) {
        String reference = schema.path("$ref").asString("");
        return reference.isEmpty() ? schema : spec.get("components").get("schemas").get(reference.substring(reference.lastIndexOf('/') + 1));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> http(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Object> request = new Yaml().load(reader);
            return (Map<String, Object>) request.get("http");
        }
    }

    private static List<Path> requestFiles() throws IOException {
        if (!Files.isDirectory(COLLECTION)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(COLLECTION)) {
            return files.filter(file -> file.toString().endsWith(".yml"))
                    .filter(file -> !NOT_REQUESTS.contains(file.getFileName().toString()))
                    .filter(file -> NOT_REQUEST_FOLDERS.stream().noneMatch(file::startsWith))
                    .sorted().collect(Collectors.toList());
        }
    }
}
