package com.thiru.wealthlens.taxplanning.erasure;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;

class PrivacyContactTest {

    @Test
    void create_whenTheContactIsBlank_refusesTheStart() {
        // Given / When / Then — r.9 needs a contact in every response to a rights request
        assertThatIllegalStateException().isThrownBy(() -> new PrivacyContact(" "))
                .withMessageContaining("TAX_PRIVACY_CONTACT");
    }

    @Test
    void create_whenTheContactIsAbsent_refusesTheStart() {
        // Given / When / Then
        assertThatIllegalStateException().isThrownBy(() -> new PrivacyContact(null));
    }
}
