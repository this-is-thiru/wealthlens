package com.thiru.wealthlens.portfolio.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.shared.protection.FieldCipher;
import com.thiru.wealthlens.shared.protection.PersonalDataKeys;
import java.util.List;
import org.junit.jupiter.api.Test;

class CorporateActionListConverterTest {

    private static final String KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final String AUTHOR = "admin@example.com";

    private final FieldCipher cipher = new FieldCipher(new PersonalDataKeys(KEY, KEY));
    private final CorporateActionListConverter converter = new CorporateActionListConverter(cipher);

    private static CorporateActionEntity copyBy(String author) {
        CorporateActionEntity copy = new CorporateActionEntity();
        copy.setId("ca-1");
        copy.setStockCode("RELIANCE");
        copy.getAuditMetadata().setCreatedBy(author);
        return copy;
    }

    @Test
    void convertToDatabaseColumn_whenACopyNamesItsAuthor_storesTheSearchableFormOnly() {
        // When
        String stored = converter.convertToDatabaseColumn(List.of(copyBy(AUTHOR)));

        // Then — the form m1.6.1's SearchableIdentifier stored, so the copier carries MongoDB's value unchanged (D-956)
        assertThat(stored).doesNotContain(AUTHOR).contains("\"createdBy\":\"" + cipher.encryptSearchable(AUTHOR) + "\"")
                .contains("\"updatedBy\":null");
    }

    @Test
    void convertToDatabaseColumn_whenWritten_leavesTheCopyInMemoryAsItWas() {
        // Given
        CorporateActionEntity copy = copyBy(AUTHOR);

        // When
        converter.convertToDatabaseColumn(List.of(copy));

        // Then
        assertThat(copy.getAuditMetadata().getCreatedBy()).isEqualTo(AUTHOR);
    }

    @Test
    void convertToEntityAttribute_whenStored_readsTheAuthorBack() {
        // Given
        String stored = converter.convertToDatabaseColumn(List.of(copyBy(AUTHOR)));

        // When
        List<CorporateActionEntity> copies = converter.convertToEntityAttribute(stored);

        // Then
        assertThat(copies).singleElement().satisfies(copy -> {
            assertThat(copy.getStockCode()).isEqualTo("RELIANCE");
            assertThat(copy.getAuditMetadata().getCreatedBy()).isEqualTo(AUTHOR);
            assertThat(copy.getAuditMetadata().getUpdatedBy()).isNull();
        });
    }

    @Test
    void convertToEntityAttribute_whenNullOrBlank_isAnEmptyList() {
        // When / Then
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
        assertThat(converter.convertToEntityAttribute(" ")).isEmpty();
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }
}
