package com.thiru.wealthlens.taxengine.rules.timeline;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.PackJson;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackLayer;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackRef;
import com.thiru.wealthlens.taxengine.rules.registry.GoverningInstruments;
import com.thiru.wealthlens.taxengine.rules.registry.Instrument;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * What one tax year's compiled rule pack must be: each layer's version and checksum, and each rule's
 * checksum (D-054). A timeline edit that changes the year's law without a lock update fails here,
 * naming the rule — the reviewed lock, not the timeline, is what a year pins.
 *
 * <p>The lock also names the Act, the Finance Act and the Rules that govern the year: it is the year's
 * registry entry (D-082), reviewed with the law it pins, so the instruments a pack is assembled with
 * cannot drift from the law it holds (D-210).
 *
 * @param layers each layer's declared version and checksum
 * @param ruleChecksums each rule the year holds, by id
 * @param instruments the legislation governing the year
 */
public record RulePackLock(
        TaxYearId taxYear,
        Map<RulePackLayer, RulePackRef> layers,
        Map<RuleId, String> ruleChecksums,
        GoverningInstruments instruments) {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public RulePackLock {
        layers = Map.copyOf(layers);
        ruleChecksums = Map.copyOf(ruleChecksums);
        if (instruments == null) {
            throw new IllegalArgumentException(taxYear + "'s lock must name the instruments governing it");
        }
    }

    /** Reads the lock for {@code taxYear}; {@code path} prefixes every error. */
    public static RulePackLock read(TaxYearId taxYear, String content, String path) {
        JsonNode root;
        try {
            root = MAPPER.readTree(content);
        } catch (JacksonException e) {
            throw new PackValidationException(path + ": not well-formed JSON — " + e.getOriginalMessage(), e);
        }
        String declaredYear = PackJson.text(root, "taxYear", path);
        if (!taxYear.label().equals(declaredYear)) {
            throw new PackValidationException(
                    path + ": is the lock for " + declaredYear + ", read as the lock for " + taxYear.label());
        }
        Map<RulePackLayer, RulePackRef> layers = new EnumMap<>(RulePackLayer.class);
        JsonNode declared = PackJson.object(root, "layers", path);
        for (RulePackLayer layer : RulePackLayer.values()) {
            String name = layer.name().toLowerCase(Locale.ROOT);
            JsonNode entry = PackJson.object(declared, name, path + ".layers");
            layers.put(layer, PackJson.construct(path + ".layers." + name, () -> new RulePackRef(
                    layer, PackJson.text(entry, "version", path), PackJson.text(entry, "checksum", path))));
        }
        Map<RuleId, String> rules = new HashMap<>();
        PackJson.object(root, "rules", path).properties().forEach(rule -> rules.put(
                PackJson.construct(path + ".rules", () -> new RuleId(rule.getKey())), rule.getValue().asString()));
        JsonNode instruments = PackJson.object(root, "instruments", path);
        String at = path + ".instruments";
        return new RulePackLock(taxYear, layers, rules, new GoverningInstruments(
                instrument(instruments, "act", at), instrument(instruments, "financeAct", at),
                instrument(instruments, "rules", at)));
    }

    private static Instrument instrument(JsonNode instruments, String field, String path) {
        JsonNode node = PackJson.object(instruments, field, path);
        String at = path + "." + field;
        return PackJson.construct(at, () -> new Instrument(
                PackJson.text(node, "citation", at),
                PackJson.optionalDate(node, "assentedOn", at),
                PackJson.date(node, "inForceFrom", at),
                PackJson.text(node, "gazetteRef", at),
                PackJson.uri(node, "sourceUrl", at),
                PackJson.enumValue(node, "verificationLevel", at, VerificationLevel.class)));
    }

    /** Refuses a compiled pack that differs from this lock, naming every rule that moved, then any layer. */
    public void verify(CompiledPack pack) {
        List<String> moved = new ArrayList<>();
        TreeSet<RuleId> ids = new TreeSet<>(Comparator.comparing(RuleId::value));
        ids.addAll(ruleChecksums.keySet());
        ids.addAll(pack.ruleChecksums().keySet());
        for (RuleId id : ids) {
            String locked = ruleChecksums.get(id);
            String compiled = pack.ruleChecksums().get(id);
            if (locked == null) {
                moved.add(id + " is in the timeline for " + taxYear.label() + " but not in its lock");
            } else if (compiled == null) {
                moved.add(id + " is in the lock for " + taxYear.label() + " but no longer in the timeline");
            } else if (!locked.equals(compiled)) {
                moved.add(id + " changed: lock says " + locked + ", timeline compiles to " + compiled);
            }
        }
        for (RulePackRef ref : layers.values()) {
            String compiled = pack.layerChecksum(ref.layer());
            if (!compiled.equals(ref.checksum())) {
                moved.add(ref.layer() + " layer " + ref.version() + " does not match: lock says "
                        + ref.checksum() + ", timeline compiles to " + compiled);
            }
        }
        if (!moved.isEmpty()) {
            throw new PackValidationException(taxYear.label() + " does not match its lock; update the lock"
                    + " only after review, with a version bump: " + moved);
        }
    }
}
