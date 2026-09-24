package com.thiru.wealthlens.taxengine.rules.pack;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Serves packs from the classpath, verifying each layer against the checksum its manifest declares.
 *
 * <p>The checksum is the reason this class is more than a file read. A manifest that nobody checks
 * is decoration: a layer edited in place keeps its old version string, and every computation made
 * from it claims a provenance it does not have. Here, bytes that do not hash to what the manifest
 * says are a load failure, so the mismatch surfaces once, at startup, rather than silently in
 * everything computed afterwards.
 *
 * <p>Layer content is handed to a {@link PackAssembler}; this class has no opinion about what a rule
 * row looks like.
 */
public final class ClasspathRulePackProvider implements RulePackProvider {

    private final String root;
    private final Set<TaxYearId> years;
    private final PackAssembler assembler;

    /**
     * @param root the classpath directory holding one subdirectory per tax year, e.g.
     *     {@code "rulepacks"}
     * @param years the years this provider serves; declared rather than scanned, because the
     *     classpath is not reliably enumerable inside a fat jar and a pack that silently fails to
     *     appear is worse than one that fails to load
     * @param assembler turns verified bytes into a pack
     */
    public ClasspathRulePackProvider(String root, Set<TaxYearId> years, PackAssembler assembler) {
        if (root == null || root.isBlank()) {
            throw new IllegalArgumentException("A classpath pack provider needs a root directory");
        }
        if (years == null || years.isEmpty()) {
            throw new IllegalArgumentException(
                    "A pack provider serving no years cannot compute anything; declare its years");
        }
        if (assembler == null) {
            throw new IllegalArgumentException("A pack provider needs an assembler");
        }
        this.root = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
        this.years = Set.copyOf(years);
        this.assembler = assembler;
    }

    @Override
    public Set<TaxYearId> availableYears() {
        return years;
    }

    @Override
    public RulePack load(TaxYearId year) {
        if (year == null || !years.contains(year)) {
            throw new PackValidationException(
                    "No pack for " + year + "; this provider serves " + new TreeSet<>(
                            years.stream().map(TaxYearId::label).toList()));
        }
        String packRoot = root + "/" + year.label();
        Map<RulePackLayer, RulePackRef> declared = readManifest(packRoot, year);

        Map<String, String> contents = new LinkedHashMap<>();
        for (Map.Entry<RulePackLayer, RulePackRef> entry : declared.entrySet()) {
            RulePackLayer layer = entry.getKey();
            StringBuilder canonical = new StringBuilder();
            for (String file : layer.files()) {
                String body = read(packRoot + "/" + file, year);
                contents.put(file, body);
                canonical.append(body);
            }
            verifyChecksum(year, entry.getValue(), canonical.toString());
        }
        return assembler.assemble(year, declared, contents);
    }

    private Map<RulePackLayer, RulePackRef> readManifest(String packRoot, TaxYearId year) {
        String json = read(packRoot + "/manifest.json", year);
        Manifest manifest = com.thiru.wealthlens.shared.util.collection.TJsonMapper.readValue(
                json, Manifest.class);

        if (manifest == null || manifest.layers() == null) {
            throw new PackValidationException(year + " manifest.json has no layers block");
        }
        if (!year.label().equals(manifest.taxYear())) {
            throw new PackValidationException(
                    "Pack filed under " + year.label() + " but its manifest says "
                            + manifest.taxYear() + "; one of the two is in the wrong directory");
        }
        Map<RulePackLayer, RulePackRef> refs = new EnumMap<>(RulePackLayer.class);
        for (RulePackLayer layer : RulePackLayer.values()) {
            ManifestEntry entry = manifest.layers().get(layer.name().toLowerCase(java.util.Locale.ROOT));
            if (entry == null) {
                throw new PackValidationException(
                        year + " manifest.json does not version layer " + layer);
            }
            refs.put(layer, new RulePackRef(layer, entry.version(), entry.checksum()));
        }
        return refs;
    }

    private void verifyChecksum(TaxYearId year, RulePackRef ref, String canonicalContent) {
        String actual = RulePackRef.checksumOf(canonicalContent);
        if (!actual.equals(ref.checksum())) {
            throw new PackValidationException(
                    year + " layer " + ref.layer() + " version " + ref.version()
                            + " does not match its declared checksum. Manifest says " + ref.checksum()
                            + ", content hashes to " + actual
                            + ". A layer edited without a version bump would otherwise be served as"
                            + " the version it claims to be.");
        }
    }


    private String read(String resourcePath, TaxYearId year) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new PackValidationException(
                        year + " pack is incomplete: " + resourcePath + " is not on the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + resourcePath, e);
        }
    }

    /** The manifest's on-disk shape — see {@code 23-architecture-consolidation.md} A2. */
    record Manifest(String taxYear, Map<String, ManifestEntry> layers) {}

    record ManifestEntry(String version, String checksum) {}
}
