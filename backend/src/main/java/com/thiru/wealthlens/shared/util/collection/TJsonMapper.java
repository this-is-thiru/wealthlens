package com.thiru.wealthlens.shared.util.collection;

import io.micrometer.common.util.StringUtils;
import java.util.List;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;


public class TJsonMapper {

    private static final JsonMapper JSON_MAPPER = getJsonMapper();
    private static final JsonMapper JSON_MAPPER_SAFE = safeJsonMapper();

    private static JsonMapper safeJsonMapper() {
        return JsonMapper.builder().enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build();
    }

    public static <T> T copy(Object source, Class<T> targetClass) {
        return readValue(JSON_MAPPER, writeValueAsString(source), targetClass);
    }

    public static <T> T safeCopy(Object source, Class<T> targetClass) {
        return readValue(JSON_MAPPER_SAFE, writeValueAsString(source), targetClass);
    }

    public static <T> List<T> safeReadAsList(String content, Class<T> targetClass) {
        if (StringUtils.isEmpty(content)) {
            return null;
        }
        return readAsList(JSON_MAPPER_SAFE, content, targetClass);
    }

    private static <T> List<T> readAsList(JsonMapper objectMapper, String content, Class<T> targetClass) {
        if (StringUtils.isEmpty(content)) {
            return null;
        }
        JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, targetClass);
        return objectMapper.readValue(content, listType);
    }

    private static <T> T readValue(JsonMapper objectMapper, String content, Class<T> targetClass) {
        if (StringUtils.isEmpty(content)) {
            return null;
        }
        return objectMapper.readValue(content, targetClass);
    }


    private static JsonMapper getJsonMapper() {
        return JsonMapper.builder()
                .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }


    public static <T> List<T> readAsList(String content, Class<T> targetClass) {
        return readAsList(JSON_MAPPER, content, targetClass);
    }

    public static <T> List<T> readAsList(Object object, Class<T> targetClass) {

        if (object == null) {
            return null;
        }

        String content = writeValueAsString(object);
        JavaType listType = JSON_MAPPER.getTypeFactory().constructCollectionType(List.class, targetClass);
        return JSON_MAPPER.readValue(content, listType);
    }

    private static <T> T readValue(String content, Class<T> targetClass) {
        if (StringUtils.isEmpty(content)) {
            return null;
        }
        return JSON_MAPPER.readValue(content, targetClass);
    }

    public static String writeValueAsString(Object source) {
        return JSON_MAPPER.writeValueAsString(source);
    }
}
