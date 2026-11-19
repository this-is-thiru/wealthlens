package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The HR-support table is refused at start unless it is well-formed (W-238f), and gives no default for a name it does not cover (C3a, W-238g C4a). */
class HrSupportTableTest {

    private static InputStream json(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void likelihoodOf_whenTheNameHasAValue_isThatValue() {
        // Given
        HrSupportTable table = HrSupportTable.read(json("{\"_note\": \"n\", \"likelihoods\": {\"HRA\": \"HIGH\"}}"));

        // When / Then
        assertThat(table.likelihoodOf("HRA")).isEqualTo(HrSupportLikelihood.HIGH);
    }

    @Test
    void likelihoodOf_whenTheNameHasNoValue_isRefused() {
        // Given — only pay and employer funds move (W-238g), and the coverage test holds the table to both, so a
        // name with no value is a defect, never a gap to show
        HrSupportTable table = HrSupportTable.read(json("{\"likelihoods\": {\"HRA\": \"HIGH\"}}"));

        // When / Then
        assertThatThrownBy(() -> table.likelihoodOf("EMPLOYEE_NPS")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no value for EMPLOYEE_NPS");
    }

    @Test
    void read_whenAValueIsUnknown_isRefused() {
        // Given / When / Then
        assertThatThrownBy(() -> HrSupportTable.read(json("{\"likelihoods\": {\"HRA\": \"CERTAIN\"}}")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not well-formed");
    }

    @Test
    void read_whenANameIsGivenTwice_isRefused() {
        // Given / When / Then
        assertThatThrownBy(() -> HrSupportTable.read(json("{\"likelihoods\": {\"HRA\": \"HIGH\", \"HRA\": \"LOW\"}}")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("HRA");
    }

    @Test
    void read_whenAFieldIsUnknown_isRefused() {
        // Given / When / Then
        assertThatThrownBy(() -> HrSupportTable.read(json("{\"likelyhoods\": {\"HRA\": \"HIGH\"}}")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("likelyhoods");
    }

    @Test
    void read_whenAValueIsNull_isRefusedNamingIt() {
        // Given / When / Then
        assertThatThrownBy(() -> HrSupportTable.read(json("{\"likelihoods\": {\"HRA\": null}}")))
                .isInstanceOf(IllegalStateException.class).hasMessage("The HR-support table gives no value for HRA");
    }

    @Test
    void read_whenNoValuesAreGiven_isRefused() {
        // Given / When / Then
        assertThatThrownBy(() -> HrSupportTable.read(json("{\"_note\": \"n\"}")))
                .isInstanceOf(IllegalStateException.class).hasMessage("The HR-support table gives no values");
    }

    @Test
    void fromClasspath_whenTheShippedTableIsRead_givesTheOwnersValues() {
        // Given — four of the 23 values adopted by the owner (W-238f C4, D-495; W-242 C2a, D-514)
        HrSupportTable table = HrSupportTable.fromClasspath();

        // When / Then
        assertThat(table.likelihoods()).hasSize(23);
        assertThat(table.likelihoodOf("SPECIAL_ALLOWANCE")).isEqualTo(HrSupportLikelihood.HIGH);
        assertThat(table.likelihoodOf("BASIC")).isEqualTo(HrSupportLikelihood.LOW);
        assertThat(table.likelihoodOf("UNCOMMUTED_PENSION")).isEqualTo(HrSupportLikelihood.NOT_APPLICABLE);
        assertThat(table.likelihoodOf("NOTIFIED_PENSION_SCHEME")).isEqualTo(HrSupportLikelihood.EMPLOYER_DISCRETION);
    }
}
