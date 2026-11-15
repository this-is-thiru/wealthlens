package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * T12-14, the copy test ({@code DR 12} C7, C8; W-119d C3a): nothing the product can say about a claim calls it safe, says it
 * is unlikely to be questioned, or sets it against a peer average — the panel's words are amounts and documents, never a
 * judgement of risk. Read from the sources of every sentence: the timeline's content files, which every year's pack is
 * compiled from, and the fixed {@code notIncluded} texts of the draft's reads — so a sentence added later is covered.
 */
class ForbiddenWordingTest {

    private static final Path CONTENT = Path.of("src/main/resources/rulepacks/timeline/content");

    /**
     * The three forms {@code DR 12} C8 names. "Average" alone is no peer average: the law measures gratuity and leave on an
     * employee's own average salary, and a sentence saying so is a figure's basis, not a comparison with anyone else.
     */
    private static final Pattern FORBIDDEN = Pattern.compile("\\bsafe(ly)?\\b|unlikely to be questioned|\\bpeers?\\b"
            + "|peer average|people like you|average (person|taxpayer|user|claim)s?\\b|(than|of) others\\b",
            Pattern.CASE_INSENSITIVE);

    /** The Acts' own words, quoted for a citation's first words: the law's text, not the product's judgement. */
    private static final Path ACTS = CONTENT.resolve("acts");

    /** The draft reads that state fixed texts beside the engine's (each class's {@code NOT_INCLUDED}). */
    private static final List<Class<?>> READS = List.of(DraftComputationService.class, DraftClaimsService.class,
            DraftClaimIntegrityService.class, DraftCompletenessService.class, DraftCalendarService.class,
            DraftForecastService.class);

    @Test
    void t12_14_whenEverySentenceTheProductCanSayIsRead_noneJudgesAClaimsRisk() throws IOException {
        // Given
        List<String> sentences = new ArrayList<>();
        try (Stream<Path> files = Files.walk(CONTENT)) {
            files.filter(file -> file.toString().endsWith(".json") && !file.startsWith(ACTS))
                    .forEach(file -> texts(read(file), file.toString(), sentences));
        }
        READS.forEach(read -> notIncluded(read).forEach(text -> sentences.add(read.getSimpleName() + ": " + text)));
        DraftOnboardingService.NOT_MEASURED.values().forEach(text -> sentences.add("DraftOnboardingService: " + text));

        // When
        List<String> judging = sentences.stream().filter(sentence -> FORBIDDEN.matcher(sentence).find()).toList();

        // Then — the content files hold hundreds of sentences, so an empty read would pass for nothing
        assertThat(sentences).hasSizeGreaterThan(500);
        assertThat(judging).isEmpty();
    }

    @Test
    void forbidden_whenASentenceJudgesRisk_isFound() {
        // When / Then — the three forms, and words that only look like them
        assertThat(Stream.of("This claim is safe.", "Unlikely to be questioned.", "Above your peers.",
                "Higher than the peer average.", "More than the average taxpayer claims.", "Lower than others."))
                .allMatch(sentence -> FORBIDDEN.matcher(sentence).find());
        assertThat(Stream.of("Keep it in a safety deposit.", "Your landlord's PAN.", "Measured on your average salary."))
                .noneMatch(sentence -> FORBIDDEN.matcher(sentence).find());
    }

    private static JsonNode read(Path file) {
        try {
            return JsonMapper.builder().build().readTree(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every string a person can be shown: notes ({@code _}-prefixed) never are, and the compiler drops them. */
    private static void texts(JsonNode node, String where, List<String> into) {
        if (node.isString()) {
            into.add(where + ": " + node.asString());
        } else if (node.isArray()) {
            node.forEach(element -> texts(element, where, into));
        } else if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                if (!field.getKey().startsWith("_")) {
                    texts(field.getValue(), where + "." + field.getKey(), into);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> notIncluded(Class<?> read) {
        try {
            Field field = read.getDeclaredField("NOT_INCLUDED");
            field.setAccessible(true);
            return (List<String>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(read + " has no NOT_INCLUDED", e);
        }
    }
}
