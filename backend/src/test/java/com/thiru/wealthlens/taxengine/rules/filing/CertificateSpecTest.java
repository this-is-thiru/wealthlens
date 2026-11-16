package com.thiru.wealthlens.taxengine.rules.filing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.CertificateCode;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A certificate's layout refuses an impossible state when it is made, whoever makes it (W-126a). */
class CertificateSpecTest {

    private static final Provenance SOURCE = new Provenance(URI.create("https://example.invalid/w-126a"), "Synthetic",
            LocalDate.of(2026, 11, 16), VerificationLevel.UNVERIFIED, "nothing");
    private static final CertificatePart PART_A = new CertificatePart("partA", "PART A");
    private static final CertificateField NAME = new CertificateField("partA.name", Optional.of("1"), "Name",
            CertificateValueKind.TEXT, List.of(), false, 1);

    @Test
    void newSpec_whenTheTitleIsBlank_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificateSpec(new CertificateCode("SYN_1"), " ", List.of(PART_A), List.of(NAME), SOURCE))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A certificate needs its code, title and provenance");
    }

    @Test
    void newSpec_whenTheProvenanceIsMissing_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificateSpec(new CertificateCode("SYN_1"), "T", List.of(PART_A), List.of(NAME), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A certificate needs its code, title and provenance");
    }

    @Test
    void newPart_whenTheHeadingIsBlank_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificatePart("partA", ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A certificate part needs its printed heading: partA");
    }

    @Test
    void newPart_whenThePathIsMissing_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificatePart(null, "PART A"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A certificate part's path is dot-separated words: null");
    }

    @Test
    void newField_whenTheLabelIsBlank_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificateField("partA.name", Optional.empty(), " ", CertificateValueKind.TEXT,
                List.of(), false, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("partA.name needs its row (or none), label, kind and columns");
    }

    @Test
    void newField_whenTheRowIsNullRatherThanEmpty_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificateField("partA.name", null, "Name", CertificateValueKind.TEXT, List.of(),
                false, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("partA.name needs its row (or none), label, kind and columns");
    }

    @Test
    void newField_whenTheKindIsMissing_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificateField("partA.name", Optional.empty(), "Name", null, List.of(), false, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("partA.name needs its row (or none), label, kind and columns");
    }

    @Test
    void newField_whenTheColumnsAreNull_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificateField("partA.name", Optional.empty(), "Name", CertificateValueKind.TEXT, null,
                false, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("partA.name needs its row (or none), label, kind and columns");
    }

    @Test
    void newField_whenThePathIsMissing_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificateField(null, Optional.empty(), "Name", CertificateValueKind.TEXT, List.of(),
                false, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A certificate field's path is dot-separated words: null");
    }

    @Test
    void newPart_whenTheHeadingIsNull_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificatePart("partA", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A certificate part needs its printed heading: partA");
    }

    @Test
    void newField_whenAHeadingLineRepeats_refuses() {
        // When / Then
        assertThatThrownBy(() -> new CertificateField("partA.group", Optional.empty(), "Group", CertificateValueKind.NONE,
                List.of(), true, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("partA.group is a heading line; it has no columns and does not repeat");
    }
}
