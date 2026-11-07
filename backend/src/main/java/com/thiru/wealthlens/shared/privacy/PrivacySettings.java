package com.thiru.wealthlens.shared.privacy;

import java.time.Period;
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
 * @param breach when the Board's detailed report on a breach is due (r.7(2)(b); W-237c)
 * @param erasureHold how long data held for erasure is kept from the request, the last processing (r.8(3); W-235, W-237d)
 * @param logRetention how long a log of processing, and a privacy record, is kept from its last processing
 *     (r.6(1)(e), r.8(3); W-237e)
 */
@ConfigurationProperties(prefix = "app.privacy")
public record PrivacySettings(String contact, String identifier, List<PrivacyRight> rights, GrievancePeriod grievance,
        BreachReporting breach, Period erasureHold, Period logRetention) {

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
        if (breach == null) {
            throw new IllegalStateException("app.privacy.breach must state when the Board's report on a breach is due"
                    + " (DPDP Rules r.7(2)(b))");
        }
        requirePositive(erasureHold, "app.privacy.erasure-hold must state how long data held for erasure is kept"
                + " (DPDP Rules r.8(3))");
        requirePositive(logRetention, "app.privacy.log-retention must state how long logs of processing are kept"
                + " (DPDP Rules r.6(1)(e), r.8(3))");
        rights = List.copyOf(rights);
    }

    private static void requirePositive(Period period, String refusal) {
        if (period == null || period.isNegative() || period.isZero()) {
            throw new IllegalStateException(refusal);
        }
    }

    /** The notice published at {@code GET /privacy/rights}. */
    public PrivacyNotice notice() {
        return new PrivacyNotice(rights, identifier, contact, grievance.responseDays());
    }
}
