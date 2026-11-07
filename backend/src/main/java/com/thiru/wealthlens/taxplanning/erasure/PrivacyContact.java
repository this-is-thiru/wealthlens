package com.thiru.wealthlens.taxplanning.erasure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Who answers a user's questions about the processing of their data. The Digital Personal Data Protection Rules, 2025 r.9 require it
 * in every response to a request exercising a right, so a start without it is refused (W-235 C5a).
 */
@ConfigurationProperties(prefix = "app.tax")
public record PrivacyContact(String privacyContact) {

    public PrivacyContact {
        if (privacyContact == null || privacyContact.isBlank()) {
            throw new IllegalStateException("app.tax.privacy-contact (TAX_PRIVACY_CONTACT) must name who answers questions about"
                    + " the processing of personal data");
        }
    }
}
