package com.thiru.wealthlens.taxengine.rules.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.filing.CertificateSpec;
import com.thiru.wealthlens.taxengine.rules.filing.CertificateValueKind;
import com.thiru.wealthlens.taxengine.rules.id.CertificateCode;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** A certificate's layout is read line by line, and a malformed one is refused where it lies (W-126a). */
class CertificateJsonTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String PATH = "forms/itr-forms.json";
    private static final String PROVENANCE = "{\"sourceUrl\": \"https://example.invalid/w-126a\","
            + " \"sourceDescription\": \"Synthetic\", \"accessedOn\": \"2026-11-16\","
            + " \"verificationLevel\": \"UNVERIFIED\", \"whatToCheck\": \"nothing\"}";
    private static final String NAME = "{\"path\": \"partA.name\", \"row\": \"1\", \"label\": \"Name\", \"kind\": \"TEXT\","
            + " \"page\": 1}";

    private static CertificateSpec read(String... fields) {
        return CertificateJson.certificate(MAPPER.readTree(certificate("SYN_1", fields)), PATH);
    }

    private static String certificate(String id, String... fields) {
        return "{\"id\": \"" + id + "\", \"title\": \"Synthetic\", \"parts\": [{\"path\": \"partA\", \"heading\": \"PART A\"}],"
                + " \"fields\": [" + String.join(",", fields) + "], \"provenance\": " + PROVENANCE + "}";
    }

    @Test
    void certificate_whenWellFormed_readsEachLine() {
        // When
        CertificateSpec spec = read(NAME, "{\"path\": \"partA.total\", \"label\": \"Total\", \"kind\": \"AMOUNT\","
                + " \"columns\": [\"Gross Amount\", \"Deductible Amount\"], \"repeating\": true, \"page\": 2}");

        // Then
        assertThat(spec.code()).isEqualTo(new CertificateCode("SYN_1"));
        assertThat(spec.fields().get(0).row()).contains("1");
        assertThat(spec.fields().get(1).row()).isEmpty();
        assertThat(spec.fields().get(1).kind()).isEqualTo(CertificateValueKind.AMOUNT);
        assertThat(spec.fields().get(1).columns()).containsExactly("Gross Amount", "Deductible Amount");
        assertThat(spec.fields().get(1).repeating()).isTrue();
    }

    @Test
    void certificates_whenTheYearHasNone_isEmpty() {
        // When / Then
        assertThat(CertificateJson.certificates(MAPPER.readTree("{\"forms\": []}"), PATH)).isEmpty();
    }

    @Test
    void certificates_whenOneCodeIsDefinedTwice_refuses() {
        // Given
        String twice = "{\"certificates\": [" + certificate("SYN_1", NAME) + "," + certificate("SYN_1", NAME) + "]}";

        // When / Then
        assertThatThrownBy(() -> CertificateJson.certificates(MAPPER.readTree(twice), PATH))
                .isInstanceOf(PackValidationException.class).hasMessageContaining("defines certificate SYN_1 twice");
    }

    @Test
    void certificate_whenAPropertyIsMisspelt_refusesNamingIt() {
        // When / Then
        assertThatThrownBy(() -> read("{\"path\": \"partA.name\", \"lable\": \"Name\", \"label\": \"Name\","
                + " \"kind\": \"TEXT\", \"page\": 1}"))
                .isInstanceOf(PackValidationException.class).hasMessageContaining("unknown property lable");
    }

    @Test
    void certificate_whenANoteIsPresent_readsPastIt() {
        // When / Then
        assertThat(read("{\"_note\": \"why\", \"path\": \"partA.name\", \"label\": \"Name\", \"kind\": \"TEXT\","
                + " \"page\": 1}").fields()).hasSize(1);
    }

    @Test
    void certificate_whenAPathIsNotDotSeparatedWords_refuses() {
        // When / Then
        assertThatThrownBy(() -> read("{\"path\": \"PartA.name\", \"label\": \"Name\", \"kind\": \"TEXT\", \"page\": 1}"))
                .isInstanceOf(PackValidationException.class).hasMessageContaining("path is dot-separated words");
    }

    @Test
    void certificate_whenALineIsInNoDeclaredPart_refuses() {
        // When / Then
        assertThatThrownBy(() -> read("{\"path\": \"partB.name\", \"label\": \"Name\", \"kind\": \"TEXT\", \"page\": 1}"))
                .isInstanceOf(PackValidationException.class).hasMessageContaining("partB.name is in no declared part");
    }

    @Test
    void certificate_whenALineIsDeclaredTwice_refuses() {
        // When / Then
        assertThatThrownBy(() -> read(NAME, NAME))
                .isInstanceOf(PackValidationException.class).hasMessageContaining("declares field partA.name twice");
    }

    @Test
    void certificate_whenAPartIsDeclaredTwice_refuses() {
        // Given
        String twoParts = certificate("SYN_1", NAME).replace("\"parts\": [{\"path\": \"partA\", \"heading\": \"PART A\"}]",
                "\"parts\": [{\"path\": \"partA\", \"heading\": \"PART A\"}, {\"path\": \"partA\", \"heading\": \"PART A\"}]");

        // When / Then
        assertThatThrownBy(() -> CertificateJson.certificate(MAPPER.readTree(twoParts), PATH))
                .isInstanceOf(PackValidationException.class).hasMessageContaining("declares part partA twice");
    }

    @Test
    void certificate_whenALabelIsBlank_refuses() {
        // When / Then
        assertThatThrownBy(() -> read("{\"path\": \"partA.name\", \"label\": \" \", \"kind\": \"TEXT\", \"page\": 1}"))
                .isInstanceOf(PackValidationException.class).hasMessageContaining("label must be a non-empty string");
    }

    @Test
    void certificate_whenAHeadingLineHasColumns_refuses() {
        // When / Then
        assertThatThrownBy(() -> read("{\"path\": \"partA.group\", \"label\": \"Group\", \"kind\": \"NONE\","
                + " \"columns\": [\"Gross Amount\"], \"page\": 1}"))
                .isInstanceOf(PackValidationException.class).hasMessageContaining("is a heading line");
    }

    @Test
    void certificate_whenTheHeadingIsBlank_refuses() {
        // Given
        String blank = certificate("SYN_1", NAME).replace("\"heading\": \"PART A\"", "\"heading\": \"\"");

        // When / Then
        assertThatThrownBy(() -> CertificateJson.certificate(MAPPER.readTree(blank), PATH))
                .isInstanceOf(PackValidationException.class).hasMessageContaining("heading must be a non-empty string");
    }

    @Test
    void certificate_whenThePageIsZero_refuses() {
        // When / Then
        assertThatThrownBy(() -> read("{\"path\": \"partA.name\", \"label\": \"Name\", \"kind\": \"TEXT\", \"page\": 0}"))
                .isInstanceOf(PackValidationException.class).hasMessageContaining("is printed on a page from 1");
    }

    @Test
    void certificate_whenTheCodeIsLowerCase_refuses() {
        // When / Then
        assertThatThrownBy(() -> CertificateJson.certificate(MAPPER.readTree(certificate("form_130", NAME)), PATH))
                .isInstanceOf(PackValidationException.class).hasMessageContaining("CertificateCode");
    }
}
