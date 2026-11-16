package com.thiru.wealthlens.taxengine.rules.json;

import com.thiru.wealthlens.taxengine.rules.filing.CertificateField;
import com.thiru.wealthlens.taxengine.rules.filing.CertificatePart;
import com.thiru.wealthlens.taxengine.rules.filing.CertificateSpec;
import com.thiru.wealthlens.taxengine.rules.filing.CertificateValueKind;
import com.thiru.wealthlens.taxengine.rules.id.CertificateCode;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * The certificates an employer or bank issues (W-126a): {@code certificates} in a year's {@code forms/itr-forms.json},
 * written only when the year has one, and each version of a {@code forms/certificates/<CODE>.json} file on the timeline.
 * A property the reader does not know is refused, so a misspelt key cannot drop a label silently.
 */
public final class CertificateJson {

    private static final Set<String> CERTIFICATE_KEYS = Set.of("id", "title", "parts", "fields", "provenance");
    private static final Set<String> PART_KEYS = Set.of("path", "heading");
    private static final Set<String> FIELD_KEYS = Set.of("path", "row", "label", "kind", "columns", "repeating", "page");

    private CertificateJson() {
    }

    /** The year's certificates, by code; none where the file has no {@code certificates}. */
    public static Map<CertificateCode, CertificateSpec> certificates(JsonNode root, String path) {
        Map<CertificateCode, CertificateSpec> byCode = new LinkedHashMap<>();
        for (CertificateSpec spec : PackJson.arrayOrEmpty(root, "certificates", path, e -> certificate(e.node(), e.path()))) {
            if (byCode.put(spec.code(), spec) != null) {
                throw new PackValidationException(path + " defines certificate " + spec.code() + " twice");
            }
        }
        return byCode;
    }

    /** One certificate, as a year holds it. */
    public static CertificateSpec certificate(JsonNode node, String path) {
        requireOnly(node, CERTIFICATE_KEYS, path);
        CertificateCode code = PackJson.construct(path, () -> new CertificateCode(PackJson.text(node, "id", path)));
        String where = path + " (" + code + ")";
        return PackJson.construct(where, () -> new CertificateSpec(code, PackJson.text(node, "title", where),
                PackJson.array(node, "parts", where, e -> part(e.node(), e.path())),
                PackJson.array(node, "fields", where, e -> field(e.node(), e.path())),
                ValueJson.provenance(PackJson.object(node, "provenance", where), where + ".provenance")));
    }

    private static CertificatePart part(JsonNode node, String path) {
        requireOnly(node, PART_KEYS, path);
        return PackJson.construct(path, () -> new CertificatePart(PackJson.text(node, "path", path),
                PackJson.text(node, "heading", path)));
    }

    private static CertificateField field(JsonNode node, String path) {
        requireOnly(node, FIELD_KEYS, path);
        return PackJson.construct(path, () -> new CertificateField(PackJson.text(node, "path", path),
                PackJson.optionalText(node, "row", path), PackJson.text(node, "label", path),
                PackJson.enumValue(node, "kind", path, CertificateValueKind.class),
                PackJson.arrayOrEmpty(node, "columns", path, e -> e.node().asString()),
                node.has("repeating") && PackJson.bool(node, "repeating", path), PackJson.integer(node, "page", path)));
    }

    /** A key outside {@code known} is refused; a key starting with an underscore is a note and passes. */
    private static void requireOnly(JsonNode node, Set<String> known, String path) {
        for (String key : node.propertyNames()) {
            if (!key.startsWith("_") && !known.contains(key)) {
                throw new PackValidationException(path + ": unknown property " + key + "; known: " + known);
            }
        }
    }
}
