package com.thiru.wealthlens.shared.privacy;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The app's privacy notice and the person who answers questions about processing, for every module (W-237a). The
 * Digital Personal Data Protection Rules, 2025 r.9 require that person named in every response to a request exercising a
 * right, and r.14(1) require the means and the identifier published, so a start without them is refused.
 *
 * @param contact who answers questions about the processing of personal data ({@code PRIVACY_CONTACT})
 * @param identifier what identifies a user's request
 * @param rights each right and how to exercise it
 * @param grievance the period within which a grievance is answered (r.14(3); W-237b)
 */
@ConfigurationProperties(prefix = "app.privacy")
public record PrivacySettings(String contact, String identifier, List<PrivacyRight> rights, GrievancePeriod grievance) {

    public PrivacySettings {
        if (contact == null || contact.isBlank()) {
            throw new IllegalStateException("app.privacy.contact (PRIVACY_CONTACT) must name who answers questions about"
                    + " the processing of personal data");
        }
        if (identifier == null || identifier.isBlank() || rights == null || rights.isEmpty()) {
            throw new IllegalStateException("app.privacy.identifier and app.privacy.rights must say what identifies a"
                    + " request and how each right is exercised (DPDP Rules r.14(1))");
        }
        if (grievance == null) {
            throw new IllegalStateException("app.privacy.grievance must state the period within which a grievance is answered"
                    + " (DPDP Rules r.14(3))");
        }
        rights = List.copyOf(rights);
    }

    /** The notice published at {@code GET /privacy/rights}. */
    public PrivacyNotice notice() {
        return new PrivacyNotice(rights, identifier, contact, grievance.responseDays());
    }
}
