package com.thiru.wealthlens.util.time;

import com.thiru.wealthlens.shared.util.time.TLocalDateTime;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TLocalDateTimeTest {

    @Test
    void testNow() {
        LocalDateTime time = TLocalDateTime.now();

        Assertions.assertNotNull(time);
    }

    @Test
    void testOfZonedDateTime() {
        LocalDateTime input = LocalDateTime.now();
        LocalDateTime time = TLocalDateTime.atUtc(input, "Asia/Kolkata");

        Assertions.assertEquals(time.plusHours(5).plusMinutes(30), input);
    }

    @Test
    void testOfZonedDateTime1() {
        long timestamp = System.currentTimeMillis();
        int nano = (int) (timestamp % 1000);
        LocalDateTime input = LocalDateTime.ofEpochSecond(timestamp / 1000, nano*1000000, ZoneOffset.of("+05:30"));
        LocalDateTime time = TLocalDateTime.atUtc(timestamp, "Asia/Kolkata");

        Assertions.assertEquals(input, time.plusHours(5).plusMinutes(30));
    }
}
