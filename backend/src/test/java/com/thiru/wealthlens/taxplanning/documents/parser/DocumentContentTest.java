package com.thiru.wealthlens.taxplanning.documents.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DocumentContentTest {

    @Test
    void new_whenEmpty_refuses() {
        // When / Then
        assertThatThrownBy(() -> new DocumentContent(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A document's content cannot be empty");
    }

    @Test
    void bytes_whenEitherSideChangesItsArray_keepsItsOwnCopy() {
        // Given
        byte[] uploaded = {1, 2, 3};
        DocumentContent content = new DocumentContent(uploaded);

        // When
        uploaded[0] = 9;
        content.bytes()[1] = 9;

        // Then
        assertThat(content.bytes()).containsExactly(1, 2, 3);
    }

    @Test
    void sha256_whenTheBytesAreAbc_isTheFipsTestVector() {
        // When / Then — FIPS 180-4, SHA-256("abc")
        assertThat(new DocumentContent(new byte[] {'a', 'b', 'c'}).sha256())
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
