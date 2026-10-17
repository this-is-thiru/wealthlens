package com.thiru.wealthlens.taxengine.test;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.registry.GoverningInstruments;
import com.thiru.wealthlens.taxengine.rules.timeline.RulePackLock;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * A year's governing instruments, as its shipped lock names them — read by {@link RulePackLock}, the reader
 * the running application uses (D-210), so a test cannot name instruments the lock does not.
 */
public final class LockedInstruments {

    private LockedInstruments() {
    }

    public static GoverningInstruments of(TaxYearId year) {
        String resource = "/rulepacks/" + year.label() + ".lock.json";
        return RulePackLock.read(year, read(resource), resource).instruments();
    }

    private static String read(String resource) {
        try (InputStream in = LockedInstruments.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is not on the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(resource + " could not be read", e);
        }
    }
}
