package com.thiru.wealthlens.portfolio.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

class CorporateActionListConverterTest {

    private static final String AUTHOR = "admin@example.com";

    private final CorporateActionListConverter converter = new CorporateActionListConverter();

    private static CorporateActionEntity copyBy(String author) {
        CorporateActionEntity copy = new CorporateActionEntity();
        copy.setId("ca-1");
        copy.setStockCode("RELIANCE");
        copy.getAuditMetadata().setCreatedBy(author);
        return copy;
    }

    @Test
    void convertToDatabaseColumn_whenACopyNamesItsAuthor_storesItPlain() {
        // When
        String stored = converter.convertToDatabaseColumn(List.of(copyBy(AUTHOR)));

        // Then — plain since W-428a (D-1036)
        assertThat(stored).contains("\"createdBy\":\"" + AUTHOR + "\"").contains("\"updatedBy\":null").doesNotContain("k1:");
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
