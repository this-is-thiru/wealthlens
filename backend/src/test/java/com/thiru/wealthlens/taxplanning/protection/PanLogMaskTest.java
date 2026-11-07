package com.thiru.wealthlens.taxplanning.protection;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.joran.spi.JoranException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The shipped {@code logback-spring.xml}: no PAN in a log line, in a message or a stack trace ({@code DR 14} O1.1, O7). */
class PanLogMaskTest {

    private static String written(String message, Throwable thrown) throws JoranException {
        LoggerContext context = new LoggerContext();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(PanLogMaskTest.class.getResource("/logback-spring.xml"));
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        @SuppressWarnings("unchecked")
        OutputStreamAppender<ILoggingEvent> console = (OutputStreamAppender<ILoggingEvent>) root.getAppender("CONSOLE");
        LoggingEvent event = new LoggingEvent(PanLogMaskTest.class.getName(), root, Level.INFO, message, thrown, null);
        byte[] line = ((LayoutWrappingEncoder<ILoggingEvent>) console.getEncoder()).encode(event);
        context.stop();
        return new String(line, StandardCharsets.UTF_8);
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
