package com.thiru.wealthlens.shared.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The privacy settings refuse a start that could not answer r.9 or r.14(1) (W-237a; W-235 C5a's rule, now app-wide). */
class PrivacySettingsTest {

    private static final List<PrivacyRight> RIGHTS = List.of(
            new PrivacyRight("Erasure of your personal data", "s.12(1), (3)", "Write to the contact", null));

    @Test
    void new_whenTheContactIsBlank_isRefusedNamingTheVariable() {
        assertThatIllegalStateException().isThrownBy(() -> new PrivacySettings(" ", "your email", RIGHTS))
                .withMessageContaining("PRIVACY_CONTACT");
    }

    @Test
    void new_whenNoRightIsPublished_isRefused() {
        assertThatIllegalStateException().isThrownBy(() -> new PrivacySettings("dpo@example.com", "your email", List.of()))
                .withMessageContaining("r.14(1)");
    }

    @Test
    void notice_whenSettingsAreComplete_publishesEachRightTheIdentifierAndTheContact() {
        // Given
        PrivacySettings settings = new PrivacySettings("dpo@example.com", "your email", RIGHTS);

        // When
        PrivacyNotice notice = settings.notice();

        // Then
        assertThat(notice.contact()).isEqualTo("dpo@example.com");
        assertThat(notice.identifier()).isEqualTo("your email");
        assertThat(notice.rights()).singleElement().satisfies(right -> assertThat(right.endpoints()).isEmpty());
    }
}
