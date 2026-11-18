package com.thiru.wealthlens.taxplanning.documents.parser;

import com.thiru.wealthlens.taxplanning.documents.store.DocumentSettings;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.log4j.Log4j2;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads an uploaded PDF in a child process ({@code DR 14} O6, "Parsing isolation"; W-131f). Only {@link PdfReadChild} hands the
 * untrusted bytes to iText. The application waits for its reply within a budget, kills it whatever happens, and trusts nothing
 * from it but a size-capped reply that parses strictly. The child runs with a heap cap ({@code -Xmx}), a CPU cap
 * ({@code ulimit -t}), every Java socket sent to a dead SOCKS proxy and no host name resolvable. It starts with an empty
 * environment and, where the application runs as root, as {@code nobody}; otherwise as the application's own user, the application
 * having made itself non-dumpable ({@link ProcessProtection}, W-131j). Either way it holds none of the application's secrets and
 * cannot read the application's process, so code that takes it over has nothing to send but the uploader's own PDF.
 *
 * <p>The network is cut inside the JVM, not by the operating system: Docker's default profile refuses a network namespace
 * ({@code unshare -rn}: "Operation not permitted", CI run 44334020550), so native code in the child could still reach the network.
 * That gap is the owner's, recorded in W-131f's D-row (C2a's fallback).
 *
 * <p>Every way the child can fail (too slow, a non-zero exit, a reply too large or not understood) is an {@code UNREADABLE}
 * reading with its reason, so the caller decides what it means. Only a child that cannot be started is the application's fault.
 *
 * @param launch the command that starts the child, up to and without the {@link PdfText.Depth}
 */
@Log4j2
public record IsolatedPdfRead(DocumentSettings.Isolation isolation, Duration parseBudget, List<String> launch) {

    private static final String BOOT_LAUNCHER = "org.springframework.boot.loader.launch.PropertiesLauncher";
    private static final String ROOT = "root";
    /** How long the reply may take to arrive once the child has exited; it is already written by then. */
    private static final Duration REPLY_AFTER_EXIT = Duration.ofSeconds(5);
    private static final JsonMapper STRICT = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                    DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
            .build();

    public IsolatedPdfRead {
        launch = List.copyOf(launch);
    }

    public static IsolatedPdfRead of(DocumentSettings.Isolation isolation, Duration parseBudget) {
        return new IsolatedPdfRead(isolation, parseBudget, launching(PdfReadChild.class, isolation, currentUser()));
    }

    /** The command that runs {@code main} under the limits: from the jar in a deployment, from the classpath in a test. */
    static List<String> launching(Class<?> main, DocumentSettings.Isolation isolation) {
        return launching(main, isolation, currentUser());
    }

    /** As above, for the user the application runs as: root hands the child to {@code nobody}, anyone else keeps it as themselves. */
    static List<String> launching(Class<?> main, DocumentSettings.Isolation isolation, String user) {
        String java = ProcessHandle.current().info().command()
                .orElseThrow(() -> new IllegalStateException("The Java runtime's own command is not known, so no PDF reader can start"));
        String limited = "ulimit -t " + isolation.cpuSeconds() + " && exec \"$0\" \"$@\"";
        List<String> command = new ArrayList<>(ROOT.equals(user)
                // BusyBox su reads options anywhere on its line; "--" ends them, so the JVM's flags reach the JVM (CI run 44338445613).
                ? List.of("/bin/su", "-s", "/bin/sh", "-c", limited, "--", "nobody")
                : List.of("/bin/sh", "-c", limited));
        command.addAll(List.of(java, "-Xmx" + isolation.heapMebibytes() + "m", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1",
                // No hsperfdata file: nobody may have nowhere to write it, and nothing needs to attach to the child.
                "-XX:-UsePerfData",
                "-DsocksProxyHost=127.0.0.1", "-DsocksProxyPort=9", "-Djdk.net.hosts.file=/dev/null",
                "-cp", System.getProperty("java.class.path")));
        // Inside the Spring Boot jar a class sits under BOOT-INF/classes, which only the jar's own launcher can load.
        if (ClassLoader.getSystemClassLoader().getResource(main.getName().replace('.', '/') + ".class") == null) {
            command.addAll(List.of("-Dloader.main=" + main.getName(), BOOT_LAUNCHER));
        } else {
            command.add(main.getName());
        }
        return command;
    }

    /** Whether the child runs as {@code nobody}: only where the application itself runs as root (W-131j moved it off root). */
    public boolean handsToNobody() {
        return launch.getFirst().equals("/bin/su");
    }

    /** The page count, or why there is none, within the upload's budget. */
    public PdfText pages(DocumentContent content) {
        return read(content, PdfText.Depth.PAGES, isolation.uploadReadBudget());
    }

    /** The page count and the printed words, or why there are none, within the parse budget. */
    PdfText words(DocumentContent content) {
        return read(content, PdfText.Depth.WORDS, parseBudget);
    }

    private PdfText read(DocumentContent content, PdfText.Depth depth, Duration budget) {
        List<String> command = new ArrayList<>(launch);
        command.add(depth.name());
        Process child;
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
            // The application's environment holds its database password and keys; the child is given none of it.
            builder.environment().clear();
            child = builder.start();
        } catch (IOException e) {
            throw new IllegalStateException("The PDF reader could not be started", e);
        }
        try {
            FutureTask<byte[]> reply = new FutureTask<>(() -> capped(child));
            Thread.ofVirtual().start(reply);
            Thread.ofVirtual().start(() -> feed(child, content.bytes()));
            boolean exited = child.waitFor(budget.toMillis(), TimeUnit.MILLISECONDS);
            if (reply.isDone() && reply.get().length > isolation.maxReplyBytes()) {
                return unreadable("The PDF reader's reply was larger than " + isolation.maxReplyBytes() + " bytes");
            }
            if (!exited) {
                return unreadable("The PDF reader took longer than " + budget);
            }
            if (child.exitValue() != 0) {
                return unreadable("The PDF reader stopped (exit " + child.exitValue() + ")");
            }
            return understood(reply.get(REPLY_AFTER_EXIT.toMillis(), TimeUnit.MILLISECONDS));
        } catch (ExecutionException | TimeoutException e) {
            return unreadable("The PDF reader's reply could not be read");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while a PDF was being read", e);
        } finally {
            child.destroyForcibly();
        }
    }

    /** Reads at most one byte past the cap, then stops the child, so a flood can neither fill memory nor block it. */
    private byte[] capped(Process child) throws IOException {
        try (InputStream out = child.getInputStream()) {
            byte[] read = out.readNBytes(isolation.maxReplyBytes() + 1);
            if (read.length > isolation.maxReplyBytes()) {
                child.destroyForcibly();
            }
            return read;
        }
    }

    private static void feed(Process child, byte[] pdf) {
        try (OutputStream in = child.getOutputStream()) {
            in.write(pdf);
        } catch (IOException e) {
            // A child that stops reading has exited or been killed; its exit or its missing reply says so.
            log.debug("The PDF reader stopped reading its input: {}", e.getMessage());
        }
    }

    private PdfText understood(byte[] reply) {
        try {
            return STRICT.readValue(reply, PdfText.class);
        } catch (JacksonException e) {
            return unreadable("The PDF reader's reply was not understood");
        }
    }

    private static String currentUser() {
        return ProcessHandle.current().info().user()
                .orElseThrow(() -> new IllegalStateException("The user this application runs as is not known, so no PDF reader can start"));
    }

    private static PdfText unreadable(String reason) {
        log.warn("A PDF could not be read in isolation: {}", reason);
        return new PdfText(PdfText.Outcome.UNREADABLE, 0, List.of(), reason);
    }
}
