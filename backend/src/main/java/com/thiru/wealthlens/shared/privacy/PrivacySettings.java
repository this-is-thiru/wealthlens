package com.thiru.wealthlens.shared.privacy;

import com.thiru.wealthlens.shared.util.collection.TJsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.Period;
import java.util.HexFormat;
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
 * @param purposeLapse how long after an account's last sign-in its purpose is no longer served, so it is erased (Act
 *     s.8(7)(a)); the owner's ruling, not a figure from the law (W-249, D-480)
 * @param consent what the notice before consent adds (Act s.5(1); Rules r.3; W-250)
 */
@ConfigurationProperties(prefix = "app.privacy")
public record PrivacySettings(String contact, String identifier, List<PrivacyRight> rights, GrievancePeriod grievance,
        BreachReporting breach, Period erasureHold, Period logRetention, Period purposeLapse, ConsentNotice consent) {

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
        requireLongerThanHold(purposeLapse, erasureHold);
        if (consent == null) {
            throw new IllegalStateException("app.privacy.consent must state what the notice before consent adds (DPDP Act"
                    + " s.5(1), Rules r.3)");
        }
        rights = List.copyOf(rights);
    }

    /*
     * A lapsed account is erased at once, with no hold, so r.8(3)'s year from its last processing must already have passed.
     * Periods are compared from one fixed day because a Period of months and one of years are not comparable as values.
     */
    private static void requireLongerThanHold(Period purposeLapse, Period erasureHold) {
        if (purposeLapse == null || !LocalDate.EPOCH.plus(purposeLapse).isAfter(LocalDate.EPOCH.plus(erasureHold))) {
            throw new IllegalStateException("app.privacy.purpose-lapse must state how long after the last sign-in an account"
                    + " is erased, and be longer than app.privacy.erasure-hold (DPDP Rules r.8(3))");
        }
    }

    private static void requirePositive(Period period, String refusal) {
        if (period == null || period.isNegative() || period.isZero()) {
            throw new IllegalStateException(refusal);
        }
    }

    /** The notice published at {@code GET /privacy/rights} and {@code GET /privacy}. */
    public PrivacyNotice notice() {
        String wording = TJsonMapper.writeValueAsString(List.of(rights, identifier, contact, grievance.responseDays(),
                purposeLapse.toString(), consent));
        return new PrivacyNotice(rights, identifier, contact, grievance.responseDays(), purposeLapse, consent, sha256(wording));
    }

    /*
     * The version is derived from the wording rather than kept by hand, so no change of wording can keep an old version and no
     * version can be bumped without one.
     */
    private static String sha256(String wording) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(wording.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable, so the privacy notice cannot be versioned", e);
        }
    }
}
