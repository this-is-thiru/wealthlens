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
 * @param identities the sums the form prints (W-127b)
 */
public record CertificateSpec(CertificateCode code, String title, List<CertificatePart> parts, List<CertificateField> fields,
        List<CertificateIdentity> identities, Provenance provenance) {

    public CertificateSpec {
        if (code == null || title == null || title.isBlank() || provenance == null) {
            throw new IllegalArgumentException("A certificate needs its code, title and provenance");
        }
        parts = List.copyOf(parts);
        fields = List.copyOf(fields);
        identities = List.copyOf(identities);
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
        for (CertificateIdentity identity : identities) {
            requireCellsPrinted(code, identity, fields);
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
