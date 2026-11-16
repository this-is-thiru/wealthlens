package com.thiru.wealthlens.taxengine.rules.filing;

import com.thiru.wealthlens.taxengine.rules.id.CertificateCode;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A certificate's printed layout for one year: its parts by heading and its lines by label, read from the Gazette (W-126a,
 * D-729 C1a). Law as data: a form amended later is a new version of its file, not new code.
 *
 * @param title the form's title as printed
 */
public record CertificateSpec(CertificateCode code, String title, List<CertificatePart> parts, List<CertificateField> fields,
        Provenance provenance) {

    public CertificateSpec {
        if (code == null || title == null || title.isBlank() || provenance == null) {
            throw new IllegalArgumentException("A certificate needs its code, title and provenance");
        }
        parts = List.copyOf(parts);
        fields = List.copyOf(fields);
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
    }
}
