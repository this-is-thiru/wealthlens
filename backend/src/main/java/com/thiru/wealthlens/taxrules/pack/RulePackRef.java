package com.thiru.wealthlens.taxrules.pack;

import java.util.regex.Pattern;

/**
 * One layer of a pack, as the manifest records it: a version and the checksum it should hash to.
 *
 * <p>The checksum is carried rather than computed here. A pack declares what it ought to be, and the
 * loader verifies that the bytes agree — so a layer edited without a version bump fails to load
 * instead of being served as if it were the version it claims.
 *
 * @param layer which layer this describes
 * @param version e.g. {@code "2026-27.law.3"} — the tax year, the layer, and a serial
 * @param checksum e.g. {@code "sha256:…"}, over the canonicalised JSON of that layer
 */
public record RulePackRef(RulePackLayer layer, String version, String checksum) {

    private static final Pattern CHECKSUM = Pattern.compile("^sha256:[0-9a-f]{64}$");

    public RulePackRef {
        if (layer == null) {
            throw new IllegalArgumentException("A pack ref must say which layer it describes");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException(layer + " must carry a version");
        }
        if (checksum == null || !CHECKSUM.matcher(checksum).matches()) {
            throw new IllegalArgumentException(
                    layer + " checksum must be sha256: followed by 64 lower-case hex digits, but was \""
                            + checksum + "\"");
        }
        String expectedInfix = "." + layer.name().toLowerCase(java.util.Locale.ROOT) + ".";
        if (!version.contains(expectedInfix)) {
            throw new IllegalArgumentException(
                    "Version \"" + version + "\" does not name layer " + layer
                            + "; expected something containing \"" + expectedInfix + "\", so that a"
                            + " version quoted in a support ticket says which layer it belongs to");
        }
    }

    /** The identity this layer contributes to a cache key. */
    String cacheFragment() {
        return layer.name().toLowerCase(java.util.Locale.ROOT) + "=" + version + "@" + checksum;
    }
}
