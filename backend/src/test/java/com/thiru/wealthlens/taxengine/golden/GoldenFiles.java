package com.thiru.wealthlens.taxengine.golden;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads golden case files from the test classpath, one JSON document each, so every case kind loads alike. */
final class GoldenFiles {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private GoldenFiles() {
    }

    static <T> T load(String resource, Function<JsonNode, T> read) {
        try (InputStream in = GoldenFiles.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is not on the classpath");
            }
            return read.apply(MAPPER.readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException(resource + " could not be read", e);
        }
    }

    static <T> List<T> loadAll(String directory, Function<JsonNode, T> read) {
        URL url = GoldenFiles.class.getClassLoader().getResource(directory);
        if (url == null) {
            throw new IllegalStateException(directory + " is not on the classpath");
        }
        try (Stream<Path> files = Files.list(Path.of(url.toURI()))) {
            List<T> cases = new ArrayList<>();
            files.filter(file -> file.toString().endsWith(".json")).sorted()
                    .forEach(file -> cases.add(load(directory + "/" + file.getFileName(), read)));
            if (cases.isEmpty()) {
                throw new IllegalStateException(directory + " holds no cases");
            }
            return cases;
        } catch (IOException e) {
            throw new UncheckedIOException(directory + " could not be listed", e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(directory + " has no file-system path", e);
        }
    }
}
