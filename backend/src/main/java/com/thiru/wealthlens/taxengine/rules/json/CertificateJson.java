package com.thiru.wealthlens.taxengine.rules.json;

import com.thiru.wealthlens.taxengine.rules.filing.CertificateAgreement;
import com.thiru.wealthlens.taxengine.rules.filing.CertificateCell;
import com.thiru.wealthlens.taxengine.rules.filing.CertificateChecks;
import com.thiru.wealthlens.taxengine.rules.filing.CertificateComparison;
import com.thiru.wealthlens.taxengine.rules.filing.CertificateField;
import com.thiru.wealthlens.taxengine.rules.filing.CertificateIdentity;
import com.thiru.wealthlens.taxengine.rules.filing.CertificatePart;
import com.thiru.wealthlens.taxengine.rules.filing.CertificateSpec;
import com.thiru.wealthlens.taxengine.rules.filing.CertificateTable;
import com.thiru.wealthlens.taxengine.rules.filing.CertificateTerm;
import com.thiru.wealthlens.taxengine.rules.filing.CertificateValueKind;
import com.thiru.wealthlens.taxengine.rules.filing.ComparedTotal;
import com.thiru.wealthlens.taxengine.rules.filing.TableColumn;
import com.thiru.wealthlens.taxengine.rules.filing.TableTotal;
import com.thiru.wealthlens.taxengine.rules.id.CertificateCode;
import com.thiru.wealthlens.taxengine.rules.id.CertificateFact;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
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

    private static final Set<String> CERTIFICATE_KEYS = Set.of("id", "title", "parts", "fields", "identities", "tables", "agreements",
            "comparisons", "provenance");
    private static final Set<String> COMPARISON_KEYS = Set.of("printed", "column", "node", "total", "reason", "whenHigher", "whenLower");
    private static final Set<String> AGREEMENT_KEYS = Set.of("printed", "near", "tolerance", "basis");
    private static final Set<String> IDENTITY_KEYS = Set.of("target", "terms", "page");
    private static final Set<String> TARGET_KEYS = Set.of("line", "column");
    private static final Set<String> TERM_KEYS = Set.of("line", "column", "sign");
    private static final Set<String> PART_KEYS = Set.of("path", "heading");
    private static final Set<String> FIELD_KEYS = Set.of("path", "row", "label", "kind", "columns", "repeating", "page", "fact");
    private static final Set<String> TABLE_KEYS = Set.of("path", "title", "columns", "total", "page");
    private static final Set<String> COLUMN_KEYS = Set.of("heading", "key", "kind", "group", "captions");
    private static final Set<String> TOTAL_KEYS = Set.of("label", "cells");
    private static final Set<String> TOTAL_CELL_KEYS = Set.of("columns");

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
                PackJson.arrayOrEmpty(node, "tables", where, e -> table(e.node(), e.path())),
                new CertificateChecks(PackJson.arrayOrEmpty(node, "identities", where, e -> identity(e.node(), e.path())),
                        PackJson.arrayOrEmpty(node, "agreements", where, e -> agreement(e.node(), e.path())),
                        PackJson.arrayOrEmpty(node, "comparisons", where, e -> comparison(e.node(), e.path()))),
                ValueJson.provenance(PackJson.object(node, "provenance", where), where + ".provenance")));
    }

    private static CertificateIdentity identity(JsonNode node, String path) {
        requireOnly(node, IDENTITY_KEYS, path);
        return PackJson.construct(path, () -> new CertificateIdentity(cell(PackJson.object(node, "target", path), path + ".target", TARGET_KEYS),
                PackJson.array(node, "terms", path, e -> PackJson.construct(e.path(), () -> new CertificateTerm(
                        cell(e.node(), e.path(), TERM_KEYS), PackJson.integer(e.node(), "sign", e.path())))),
                PackJson.integer(node, "page", path)));
    }

    private static CertificateAgreement agreement(JsonNode node, String path) {
        requireOnly(node, AGREEMENT_KEYS, path);
        return PackJson.construct(path, () -> new CertificateAgreement(PackJson.text(node, "printed", path), PackJson.text(node, "near", path),
                PackJson.money(node, "tolerance", path), PackJson.text(node, "basis", path)));
    }

    private static CertificateComparison comparison(JsonNode node, String path) {
        requireOnly(node, COMPARISON_KEYS, path);
        return PackJson.construct(path, () -> new CertificateComparison(PackJson.text(node, "printed", path),
                PackJson.optionalText(node, "column", path), PackJson.optionalText(node, "node", path).map(NodeId::new),
                PackJson.optionalText(node, "total", path).map(ComparedTotal::valueOf), PackJson.optionalText(node, "reason", path),
                PackJson.optionalText(node, "whenHigher", path), PackJson.optionalText(node, "whenLower", path)));
    }

    private static CertificateTable table(JsonNode node, String path) {
        requireOnly(node, TABLE_KEYS, path);
        return PackJson.construct(path, () -> new CertificateTable(PackJson.text(node, "path", path),
                PackJson.arrayOrEmpty(node, "title", path, e -> e.node().asString()),
                PackJson.array(node, "columns", path, e -> column(e.node(), e.path())),
                PackJson.optionalObject(node, "total", path).map(total -> total(total, path + ".total")),
                PackJson.integer(node, "page", path)));
    }

    private static TableColumn column(JsonNode node, String path) {
        requireOnly(node, COLUMN_KEYS, path);
        return PackJson.construct(path, () -> new TableColumn(PackJson.text(node, "heading", path), PackJson.text(node, "key", path),
                PackJson.enumValue(node, "kind", path, CertificateValueKind.class), PackJson.optionalText(node, "group", path),
                PackJson.arrayOrEmpty(node, "captions", path, e -> e.node().asString())));
    }

    /** A total cell is an object naming the columns it spans, so a merged cell reads as one. */
    private static TableTotal total(JsonNode node, String path) {
        requireOnly(node, TOTAL_KEYS, path);
        return PackJson.construct(path, () -> new TableTotal(PackJson.text(node, "label", path),
                PackJson.array(node, "cells", path, e -> {
                    requireOnly(e.node(), TOTAL_CELL_KEYS, e.path());
                    return PackJson.array(e.node(), "columns", e.path(), c -> c.node().asString());
                })));
    }

    private static CertificateCell cell(JsonNode node, String path, Set<String> known) {
        requireOnly(node, known, path);
        return PackJson.construct(path, () -> new CertificateCell(PackJson.text(node, "line", path),
                PackJson.optionalText(node, "column", path)));
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
                node.has("repeating") && PackJson.bool(node, "repeating", path), PackJson.integer(node, "page", path),
                PackJson.optionalText(node, "fact", path).map(CertificateFact::new)));
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
