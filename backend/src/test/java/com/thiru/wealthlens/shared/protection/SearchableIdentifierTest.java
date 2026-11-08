package com.thiru.wealthlens.shared.protection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchableIdentifierTest {

    private static final String KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

    private final SearchableIdentifier converter = new SearchableIdentifier(new FieldCipher(new PersonalDataKeys(KEY, KEY)));

    @Test
    void write_whenGivenText_storesTheSearchableFormThatReadsBack() {
        // When
        Object stored = converter.write("holder@example.com", null);

        // Then
        assertThat(stored).asString().startsWith("k1:").isEqualTo(converter.write("holder@example.com", null));
        assertThat(converter.read(stored, null)).isEqualTo("holder@example.com");
    }

    @Test
    void write_whenAQueryOperatorCarriesNoText_refuses() {
        // When / Then — `$exists: true` on an identifier would otherwise compare a boolean's ciphertext and match nothing
        assertThatThrownBy(() -> converter.write(Boolean.TRUE, null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not as a Boolean");
    }

    @Test
    void write_whenGivenAListOfEmails_storesEachSearchableAndReadsBackAMutableList() {
        // When
        Object stored = converter.write(List.of("anu@example.com", "bala@example.com"), null);

        // Then
        assertThat(stored).isEqualTo(List.of(converter.write("anu@example.com", null), converter.write("bala@example.com", null)));
        Object read = converter.read(stored, null);
        assertThat(read).isEqualTo(List.of("anu@example.com", "bala@example.com")).isInstanceOf(ArrayList.class);
    }
}
