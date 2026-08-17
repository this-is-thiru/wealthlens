package com.thiru.wealthlens.shared.util.collection;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class TCollectionUtil {

    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";


    public static <T, R, U> Map<R, U> toMap(Collection<T> source, Function<T, R> keyMapper, Function<T, U> valueMapper) {
        return stream(source).collect(Collectors.toMap(keyMapper, valueMapper));
    }

    public static <T, R> List<R> map(Collection<T> source, Function<T, R> mapper) {
        return stream(source).map(mapper).collect(Collectors.toList());
    }

    public static <T, R> List<R> mapAndApply(Collection<T> source, Function<T, R> mapper, Predicate<R> predicate) {
        return stream(source).map(mapper).filter(predicate).collect(Collectors.toList());
    }

    public static <T> List<T> filter(Collection<T> source, Predicate<T> consumer) {
        return stream(source).filter(consumer).collect(Collectors.toList());
    }

    public static <T, R> List<R> applyMap(Collection<T> source, Predicate<T> predicate, Function<T, R> mapper) {
        return stream(source).filter(predicate).map(mapper).collect(Collectors.toList());
    }

    public static <T> T findFirst(Collection<T> source, Predicate<T> predicate, T defaultValue) {
        return stream(source).filter(predicate).findFirst().orElse(defaultValue);
    }

    public static <T> T findFirst(Collection<T> source, Predicate<T> predicate) {
        return stream(source).filter(predicate).findFirst().orElse(null);
    }

    public static <T> T getElementSafe(List<T> source, int index) {
        return getElementSafe(source, index, null);
    }

    public static <T> T getElementSafe(List<T> source, int index, T defaultValue) {
        return source.size() > index ? source.get(index) : defaultValue;
    }

    public static <T, R> void mapAndApply(Collection<T> source, Function<T, R> mapper, Consumer<R> consumer) {
        stream(source).map(mapper).forEach(consumer);
    }

    public static <T> List<T> flatMap(Collection<List<T>> source) {
        return stream(source).flatMap(TCollectionUtil::stream).collect(Collectors.toList());
    }

    public static <T> LongStream mapToLong(List<T> list, ToLongFunction<T> mapper) {
        return stream(list).mapToLong(mapper);
    }

    public static <T> DoubleStream mapToDouble(List<T> list, ToDoubleFunction<T> mapper) {
        return stream(list).mapToDouble(mapper);
    }

    public static <T, U> Map<U, List<T>> groupingBy(List<T> list, Function<T, U> mapper) {
        return stream(list).collect(Collectors.groupingBy(mapper));
    }

    private static <T> Stream<T> stream(Collection<T> list) {
        return list.stream();
    }
}
