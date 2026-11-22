package com.thiru.wealthlens.testsupport;

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

/**
 * A log line as the shipped {@code logback-spring.xml} writes it, so a mask in that file is proven against the file itself rather than
 * a copy of its pattern (the PAN mask, D-447 C4a; the email mask, W-358d1).
 */
public final class ShippedLogLine {

    private ShippedLogLine() {
    }

    /** The console line for {@code message}, with {@code thrown}'s trace when one is given. */
    public static String written(String message, Throwable thrown) throws JoranException {
        LoggerContext context = new LoggerContext();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(ShippedLogLine.class.getResource("/logback-spring.xml"));
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        @SuppressWarnings("unchecked")
        OutputStreamAppender<ILoggingEvent> console = (OutputStreamAppender<ILoggingEvent>) root.getAppender("CONSOLE");
        LoggingEvent event = new LoggingEvent(ShippedLogLine.class.getName(), root, Level.INFO, message, thrown, null);
        byte[] line = ((LayoutWrappingEncoder<ILoggingEvent>) console.getEncoder()).encode(event);
        context.stop();
        return new String(line, StandardCharsets.UTF_8);
    }
}
