package com.thiru.wealthlens.taxengine.test;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.registry.GoverningInstruments;
import com.thiru.wealthlens.taxengine.rules.registry.Instrument;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A year's governing instruments, read from the {@code instruments} block of its shipped lock — the year's
 * registry entry until startup wiring exists (D-054, D-082). One reader, so a test cannot name
 * instruments the lock does not.
 */
public final class LockedInstruments {

    private LockedInstruments() {
    }

    public static GoverningInstruments of(TaxYearId year) {
        JsonNode lock = read("/rulepacks/" + year.label() + ".lock.json").get("instruments");
        return new GoverningInstruments(
                instrument(lock.get("act")), instrument(lock.get("financeAct")), instrument(lock.get("rules")));
    }

    private static Instrument instrument(JsonNode node) {
        return new Instrument(
                node.get("citation").asString(),
                node.has("assentedOn") ? Optional.of(LocalDate.parse(node.get("assentedOn").asString())) : Optional.empty(),
                LocalDate.parse(node.get("inForceFrom").asString()),
                node.get("gazetteRef").asString(),
                URI.create(node.get("sourceUrl").asString()),
                VerificationLevel.valueOf(node.get("verificationLevel").asString()));
    }

    private static JsonNode read(String resource) {
        try (InputStream in = LockedInstruments.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is not on the classpath");
            }
            return JsonMapper.builder().build().readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(resource + " could not be read", e);
        }
    }
}
