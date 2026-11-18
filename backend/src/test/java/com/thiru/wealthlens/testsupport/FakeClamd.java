package com.thiru.wealthlens.testsupport;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * A stand-in for ClamAV's {@code clamd}, speaking its {@code INSTREAM} protocol on a local port, so every upload in the tests
 * passes through the malware scan without a 3 GiB container (W-131e, C4a). By default it answers as clamd does: FOUND for a
 * stream holding {@link #MALWARE_MARKER}, OK otherwise. A test may give it another answer; an empty answer closes the connection
 * without a reply.
 */
public final class FakeClamd implements AutoCloseable {

    /** Bytes the default answer treats as malware: the fake's own, so no real test signature sits in the repository. */
    public static final byte[] MALWARE_MARKER = "WEALTHLENS-FAKE-MALWARE-MARKER".getBytes(StandardCharsets.US_ASCII);

    /** The reply the default answer gives for {@link #MALWARE_MARKER}, shaped as clamd's (`stream: <name> FOUND`). */
    public static final String FOUND_REPLY = "stream: WealthLens-Fake-Marker FOUND";

    private final ServerSocket server;
    private final AtomicReference<Function<byte[], Optional<String>>> answer = new AtomicReference<>(FakeClamd::asClamd);
    private final List<String> commands = new CopyOnWriteArrayList<>();
    private final List<Integer> chunkLengths = new CopyOnWriteArrayList<>();

    private FakeClamd(ServerSocket server) {
        this.server = server;
    }

    /** Listens on a free local port until closed. */
    public static FakeClamd start() {
        try {
            FakeClamd clamd = new FakeClamd(new ServerSocket(0));
            Thread.ofVirtual().start(clamd::serve);
            return clamd;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public int port() {
        return server.getLocalPort();
    }

    /** Answers every later stream with {@code reply}: the reply without its terminator, or empty to close without one. */
    public void answerWith(Function<byte[], Optional<String>> reply) {
        answer.set(reply);
    }

    /** Back to the default answer. */
    public void answerAsClamd() {
        answer.set(FakeClamd::asClamd);
    }

    /** Every command received, without its {@code z} prefix and terminator, oldest first. */
    public List<String> commands() {
        return List.copyOf(commands);
    }

    /** Every chunk length received, in order, the zero that ends each stream included. */
    public List<Integer> chunkLengths() {
        return List.copyOf(chunkLengths);
    }

    @Override
    public void close() throws IOException {
        server.close();
    }

    private static Optional<String> asClamd(byte[] stream) {
        return Optional.of(contains(stream, MALWARE_MARKER) ? FOUND_REPLY : "stream: OK");
    }

    private static boolean contains(byte[] stream, byte[] part) {
        for (int at = 0; at + part.length <= stream.length; at++) {
            if (Arrays.equals(stream, at, at + part.length, part, 0, part.length)) {
                return true;
            }
        }
        return false;
    }

    private void serve() {
        while (!server.isClosed()) {
            try {
                Socket connection = server.accept();
                Thread.ofVirtual().start(() -> answer(connection));
            } catch (IOException e) {
                if (!server.isClosed()) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }

    private void answer(Socket connection) {
        try (connection) {
            DataInputStream in = new DataInputStream(connection.getInputStream());
            commands.add(command(in));
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            int length;
            do {
                length = in.readInt();
                chunkLengths.add(length);
                stream.write(in.readNBytes(length));
            } while (length > 0);
            Optional<String> reply = answer.get().apply(stream.toByteArray());
            if (reply.isPresent()) {
                OutputStream out = connection.getOutputStream();
                out.write((reply.get() + "\0").getBytes(StandardCharsets.US_ASCII));
                out.flush();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String command(InputStream in) throws IOException {
        StringBuilder command = new StringBuilder();
        for (int next = in.read(); next > 0; next = in.read()) {
            command.append((char) next);
        }
        return command.toString();
    }
}
