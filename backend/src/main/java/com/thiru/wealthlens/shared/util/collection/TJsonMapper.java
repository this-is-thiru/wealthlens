package com.thiru.wealthlens.shared.util.collection;

import java.util.List;
import java.util.Objects;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;


/**
 * Copies and reads values through one configured JSON mapper.
 *
 * <p>Nothing to read is a failure, never a {@code null} answer (D-390): a {@code null} source would otherwise
 * serialise to {@code "null"} and come back as {@code null}, carried on by a caller that never expected it.
 */
public class TJsonMapper {

    private static final String NULL_SOURCE = "nothing to read: the source is null";

    private static final JsonMapper JSON_MAPPER = getJsonMapper();
    private static final JsonMapper JSON_MAPPER_SAFE = safeJsonMapper();

    private static JsonMapper safeJsonMapper() {
        return JsonMapper.builder().enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build();
    }

    public static <T> T copy(Object source, Class<T> targetClass) {
        Objects.requireNonNull(source, NULL_SOURCE);
        return JSON_MAPPER.readValue(writeValueAsString(source), targetClass);
    }

    public static <T> T safeCopy(Object source, Class<T> targetClass) {
        Objects.requireNonNull(source, NULL_SOURCE);
        return JSON_MAPPER_SAFE.readValue(writeValueAsString(source), targetClass);
    }


    private static JsonMapper getJsonMapper() {
        return JsonMapper.builder()
                .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }


    public static <T> List<T> readAsList(String content, Class<T> targetClass) {
        JavaType listType = JSON_MAPPER.getTypeFactory().constructCollectionType(List.class, targetClass);
        return JSON_MAPPER.readValue(content, listType);
    }

    public static <T> List<T> readAsList(Object object, Class<T> targetClass) {
        Objects.requireNonNull(object, NULL_SOURCE);
        String content = writeValueAsString(object);
        JavaType listType = JSON_MAPPER.getTypeFactory().constructCollectionType(List.class, targetClass);
        return JSON_MAPPER.readValue(content, listType);
    }

    public static String writeValueAsString(Object source) {
        return JSON_MAPPER.writeValueAsString(source);
    }
}
