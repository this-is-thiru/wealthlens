package com.thiru.wealthlens.shared.util.collection;

import java.util.Optional;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TOptional {

    public static <T> T mapO(T object, T defaultValue) {
        return optional(object).orElse(defaultValue);
    }

    public static <T, U> U map1(T object, Function<T, U> mapper) {
        return map1(object, mapper, null);
    }

    public static <T, U, V> V map2(T object, Function<T, U> mapper1, Function<U, V> mapper2) {
        return optional(object).map(mapper1).map(mapper2).orElse(null);
    }

    public static <T, U> U map1(T object, Function<T, U> mapper, U defaultValue) {
        return optional(object).map(mapper).orElse(defaultValue);
    }

    private static <T> Optional<T> optional(T object) {
        return Optional.ofNullable(object);
    }
}
