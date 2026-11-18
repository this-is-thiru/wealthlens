package com.thiru.wealthlens.taxplanning.isolation;

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

/**
 * Runs one {@code main} of the application in a child process and hands it untrusted bytes ({@code DR 14} O6; W-131f, moved here
 * by W-133a so a PDF and a workbook share it). The child gets a heap cap ({@code -Xmx}), a CPU cap ({@code ulimit -t}), an empty
 * environment, every Java socket sent to a dead SOCKS proxy and no host name resolvable; where the application runs as root it
 * runs as {@code nobody}. It is killed whatever happens, and only a size-capped reply comes back.
 *
 * <p>The network is cut inside the JVM, not by the operating system: Docker's default profile refuses a network namespace
 * (CI run 44334020550). That gap is the owner's (D-770).
 *
 * @param launch the command that starts the child, up to and without its one argument
 */
@Log4j2
public record IsolatedChild(DocumentSettings.Isolation isolation, List<String> launch) {

    private static final String BOOT_LAUNCHER = "org.springframework.boot.loader.launch.PropertiesLauncher";
    private static final String ROOT = "root";
    /** How long the reply may take to arrive once the child has exited; it is already written by then. */
    private static final Duration REPLY_AFTER_EXIT = Duration.ofSeconds(5);

    public IsolatedChild {
        launch = List.copyOf(launch);
    }

    /** A child running {@code main} under the limits, as this process's user allows. */
    public static IsolatedChild of(Class<?> main, DocumentSettings.Isolation isolation) {
        return new IsolatedChild(isolation, launching(main, isolation, currentUser()));
    }

    /** As {@link #of}, the command only: from the jar in a deployment, from the classpath in a test. */
    public static List<String> launching(Class<?> main, DocumentSettings.Isolation isolation) {
        return launching(main, isolation, currentUser());
    }

    /** As above, for the user the application runs as: root hands the child to {@code nobody}, anyone else keeps it as themselves. */
    public static List<String> launching(Class<?> main, DocumentSettings.Isolation isolation, String user) {
        String java = ProcessHandle.current().info().command()
                .orElseThrow(() -> new IllegalStateException("The Java runtime's own command is not known, so no child can start"));
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

    /** Feeds {@code input} to the child with {@code argument}, and waits at most {@code budget} for its reply. */
    public ChildReply run(String argument, byte[] input, Duration budget) {
        List<String> command = new ArrayList<>(launch);
        command.add(argument);
        Process child;
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
            // The application's environment holds its database password and keys; the child is given none of it.
            builder.environment().clear();
            child = builder.start();
        } catch (IOException e) {
            throw new IllegalStateException("A child reader could not be started", e);
        }
        try {
            FutureTask<byte[]> reply = new FutureTask<>(() -> capped(child));
            Thread.ofVirtual().start(reply);
            Thread.ofVirtual().start(() -> feed(child, input));
            boolean exited = child.waitFor(budget.toMillis(), TimeUnit.MILLISECONDS);
            if (reply.isDone() && reply.get().length > isolation.maxReplyBytes()) {
                return failed("The reader's reply was larger than " + isolation.maxReplyBytes() + " bytes");
            }
            if (!exited) {
                return failed("The reader took longer than " + budget);
            }
            if (child.exitValue() != 0) {
                return failed("The reader stopped (exit " + child.exitValue() + ")");
            }
            return new ChildReply.Answered(reply.get(REPLY_AFTER_EXIT.toMillis(), TimeUnit.MILLISECONDS));
        } catch (ExecutionException | TimeoutException e) {
            return failed("The reader's reply could not be read");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while a child was reading", e);
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

    private static void feed(Process child, byte[] input) {
        try (OutputStream in = child.getOutputStream()) {
            in.write(input);
        } catch (IOException e) {
            // A child that stops reading has exited or been killed; its exit or its missing reply says so.
            log.debug("A child reader stopped reading its input: {}", e.getMessage());
        }
    }

    private static String currentUser() {
        return ProcessHandle.current().info().user()
                .orElseThrow(() -> new IllegalStateException("The user this application runs as is not known, so no child can start"));
    }

    private static ChildReply failed(String reason) {
        log.warn("A child reader failed: {}", reason);
        return new ChildReply.Failed(reason);
    }
}
