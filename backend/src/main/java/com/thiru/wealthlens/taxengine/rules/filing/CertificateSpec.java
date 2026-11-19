package com.thiru.wealthlens.taxengine.rules.filing;

import com.thiru.wealthlens.taxengine.rules.id.CertificateCode;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A certificate's printed layout for one year: its parts by heading and its lines by label, read from the Gazette (W-126a,
 * D-729 C1a). Law as data: a form amended later is a new version of its file, not new code.
 *
 * @param title the form's title as printed
 * @param tables the tables whose rows the issuer writes (W-126c2a)
 * @param checks what its figures must agree with (W-332)
 */
public record CertificateSpec(CertificateCode code, String title, List<CertificatePart> parts, List<CertificateField> fields,
        List<CertificateTable> tables, CertificateChecks checks, Provenance provenance) {

    public CertificateSpec {
        if (code == null || title == null || title.isBlank() || provenance == null) {
            throw new IllegalArgumentException("A certificate needs its code, title and provenance");
        }
        parts = List.copyOf(parts);
        fields = List.copyOf(fields);
        tables = List.copyOf(tables);
        Set<String> partPaths = new HashSet<>();
        for (CertificatePart part : parts) {
            if (!partPaths.add(part.path())) {
                throw new IllegalArgumentException(code + " declares part " + part.path() + " twice");
            }
        }
        Set<String> fieldPaths = new HashSet<>();
        for (CertificateField field : fields) {
            if (!fieldPaths.add(field.path())) {
                throw new IllegalArgumentException(code + " declares field " + field.path() + " twice");
            }
            if (partPaths.stream().noneMatch(part -> field.path().startsWith(part + "."))) {
                throw new IllegalArgumentException(code + "'s field " + field.path() + " is in no declared part");
            }
        }
        for (CertificateTable table : tables) {
            requireInAPart(code, table.path(), partPaths, fieldPaths, "table");
        }
        for (CertificateIdentity identity : checks.identities()) {
            requireCellsPrinted(code, identity, fields);
        }
        requireAgreementsPrinted(code, checks.agreements(), fields, tables);
        requireComparisonsPrinted(code, checks.comparisons(), fields, tables);
        requireFactsOncePerPart(code, fields, partPaths);
    }

    /** A fact names one line of its part, so a check finds it there without doubt (W-128c). */
    private static void requireFactsOncePerPart(CertificateCode code, List<CertificateField> fields, Set<String> partPaths) {
        Set<String> seen = new HashSet<>();
        for (CertificateField field : fields) {
            if (field.fact().isPresent()) {
                String part = partPaths.stream().filter(path -> field.path().startsWith(path + "."))
                        .max(java.util.Comparator.comparingInt(String::length)).orElseThrow();
                if (!seen.add(part + " " + field.fact().get())) {
                    throw new IllegalArgumentException(code + " names fact " + field.fact().get() + " twice in part " + part);
                }
            }
        }
    }

    private static void requireAgreementsPrinted(CertificateCode code, List<CertificateAgreement> agreements, List<CertificateField> fields,
            List<CertificateTable> tables) {
        for (CertificateAgreement agreement : agreements) {
            requireAmount(code, "agreement", agreement.printed(), fields, tables);
            requireAmount(code, "agreement", agreement.near(), fields, tables);
        }
    }

    private static void requireComparisonsPrinted(CertificateCode code, List<CertificateComparison> comparisons, List<CertificateField> fields,
            List<CertificateTable> tables) {
        for (CertificateComparison comparison : comparisons) {
            if (comparison.column().isEmpty()) {
                requireAmount(code, "comparison", comparison.printed(), fields, tables);
            } else if (fields.stream().noneMatch(field -> field.path().equals(comparison.printed())
                    && field.kind() == CertificateValueKind.AMOUNT && field.columns().contains(comparison.column().get()))) {
                throw new IllegalArgumentException(code + "'s comparison names " + comparison.printed() + " [" + comparison.column().get()
                        + "], a cell it does not print");
            }
        }
    }

    /** An agreement's or comparison's amount is a declared AMOUNT line of one cell, or a table's Total cell over an AMOUNT column. */
    private static void requireAmount(CertificateCode code, String check, String path, List<CertificateField> fields,
            List<CertificateTable> tables) {
        boolean line = fields.stream().anyMatch(field -> field.path().equals(path) && field.kind() == CertificateValueKind.AMOUNT
                && field.columns().isEmpty());
        boolean total = tables.stream().anyMatch(table -> table.total().isPresent() && table.total().get().cells().stream()
                .map(cell -> cell.get(0)).anyMatch(key -> path.equals(table.path() + ".total." + key) && table.columns().stream()
                        .anyMatch(column -> column.key().equals(key) && column.kind() == CertificateValueKind.AMOUNT)));
        if (!line && !total) {
            throw new IllegalArgumentException(code + "'s " + check + " names " + path + ", which is not an amount it prints");
        }
    }

    /** A table sits in a declared part, and no line or other table shares its path. */
    private static void requireInAPart(CertificateCode code, String path, Set<String> partPaths, Set<String> taken, String what) {
        if (!taken.add(path)) {
            throw new IllegalArgumentException(code + " declares " + what + " " + path + " twice");
        }
        if (partPaths.stream().noneMatch(part -> path.startsWith(part + "."))) {
            throw new IllegalArgumentException(code + "'s " + what + " " + path + " is in no declared part");
        }
    }

    /** Every cell of a sum is an amount the form prints: a declared AMOUNT line, and its column where it has several. */
    private static void requireCellsPrinted(CertificateCode code, CertificateIdentity identity, List<CertificateField> fields) {
        Stream.concat(Stream.of(identity.target()),
                identity.terms().stream().map(CertificateTerm::cell)).forEach(cell -> {
                    CertificateField line = fields.stream().filter(field -> field.path().equals(cell.line())).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException(code + "'s sum names " + cell + ", which it does not declare"));
                    if (line.kind() != CertificateValueKind.AMOUNT) {
                        throw new IllegalArgumentException(code + "'s sum names " + cell + ", which is not an amount");
                    }
                    boolean columnFits = cell.column().map(line.columns()::contains).orElse(line.columns().isEmpty());
                    if (!columnFits) {
                        throw new IllegalArgumentException(code + "'s sum names " + cell + ", a cell the line does not print");
                    }
                });
    }
}
