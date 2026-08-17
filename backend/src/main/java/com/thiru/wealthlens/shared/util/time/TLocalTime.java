package com.thiru.wealthlens.shared.util.time;


import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.jspecify.annotations.NonNull;

public class TLocalTime {

    public static LocalTime toLocalTime(@NonNull String time, @NonNull String pattern) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return LocalTime.parse(time, formatter);
    }

    public static String format(@NonNull LocalTime time, @NonNull String pattern) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern, Locale.US);
        return time.format(formatter);
    }
}
