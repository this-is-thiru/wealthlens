package com.thiru.wealthlens.testsupport;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * Captures what a class logged, so a warning can be asserted rather than assumed.
 *
 * <p>A branch that only logs has no observable effect, which makes it untestable and — as mutation
 * testing demonstrates — indistinguishable from a branch that was deleted. That matters in the
 * charges engine, which uses warnings for exactly the situations it most wants noticed: a rule
 * pricing on an amount the trade does not carry, a band covering nothing, a derived rule naming no
 * base. Every one of those charges zero, and a silent zero is the failure the design exists to
 * prevent.
 *
 * <p>Attaches to one class's logger and detaches on close, so test classes running in parallel do
 * not capture each other's output.
 *
 * <p>Bound to Logback deliberately. {@code @Log4j2} throughout this codebase is Lombok's API
 * annotation; the implementation behind it is Logback, reached through {@code log4j-to-slf4j}.
 */
public final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCapture(Logger logger) {
        this.logger = logger;
    }

    /** Starts capturing everything the given class logs. Close it to stop. */
    public static LogCapture on(Class<?> type) {
        LogCapture capture = new LogCapture((Logger) LoggerFactory.getLogger(type));
        capture.appender.start();
        capture.logger.addAppender(capture.appender);
        return capture;
    }

    /** Each captured line as {@code "LEVEL formatted message"}, in order. */
    public List<String> messages() {
        return appender.list.stream()
                .map(event -> event.getLevel() + " " + event.getFormattedMessage())
                .toList();
    }

    public List<String> warnings() {
        return messages().stream().filter(message -> message.startsWith("WARN ")).toList();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
