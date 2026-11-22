package com.thiru.wealthlens.taxplanning.protection;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.core.joran.spi.JoranException;
import com.thiru.wealthlens.testsupport.ShippedLogLine;
import org.junit.jupiter.api.Test;

/** The shipped {@code logback-spring.xml}: no PAN in a log line, in a message or a stack trace ({@code DR 14} O1.1, O7). */
class PanLogMaskTest {

    private static String written(String message, Throwable thrown) throws JoranException {
        return ShippedLogLine.written(message, thrown);
    }

    @Test
    void write_whenAMessageNamesAPan_writesItMasked() throws JoranException {
        // When / Then — Pan.masked(): the first three, five X, the last two
        assertThat(written("Saved ABCPE1234F for the owner", null)).contains("Saved ABCXXXXX4F for the owner").doesNotContain("ABCPE1234F");
    }

    @Test
    void write_whenAnExceptionNamesAPan_writesTheTraceOnceAndMasked() throws JoranException {
        // When
        String line = written("Refused", new IllegalArgumentException("PQRST5678K is taken"));

        // Then
        assertThat(line).contains("PQRXXXXX8K is taken").doesNotContain("PQRST5678K");
        assertThat(line.split("IllegalArgumentException", -1)).hasSize(2);
    }

    @Test
    void write_whenATokenIsNotPanShaped_leavesItAlone() throws JoranException {
        // When / Then — a TAN (four letters, five digits, a letter) and a longer word are not PANs
        assertThat(written("TAN BLRA12345B, ref XABCPE1234FX", null)).contains("BLRA12345B", "XABCPE1234FX");
    }
}
