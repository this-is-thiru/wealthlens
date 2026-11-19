package com.thiru.wealthlens.shared.privacy;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Records the notice before consent and the consent given against it (Digital Personal Data Protection Act, 2023 s.5,
 * s.6(10); W-250), for {@code auth}, which registers and signs people in but holds no privacy record of its own. Not
 * transactional: each write is one document, and a duplicate key inside a MongoDB transaction would abort it rather than be
 * recognised as a record already made.
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class PrivacyConsents {

    private final PrivacyConsentRepository consents;
    private final PrivacySettings privacy;

    /**
     * Refuses a registration whose caller did not send back the version of the notice now published, so a consent is never
     * recorded against wording the person was not shown (s.5(1)). Writes nothing.
     */
    public void requireCurrent(String noticeVersion) {
        if (!privacy.notice().version().equals(noticeVersion)) {
            throw new IllegalArgumentException("Read the privacy notice at GET " + PrivacyNotice.PAGE + " (or GET /privacy/rights)"
                    + " and register with its version");
        }
    }

    /** Records consent given at registration against {@code noticeVersion}, already checked by {@link #requireCurrent}. */
    public void recordConsent(String email, String noticeVersion, LocalDateTime givenAtUtc) {
        PrivacyConsent consent = consents.findByEmail(email).orElseGet(PrivacyConsent::new);
        consent.setEmail(email);
        consent.setNoticeVersion(noticeVersion);
        consent.setGivenAt(givenAtUtc);
        consents.save(consent);
    }

    /**
     * Records that an account with no record yet, one registered before W-250, was given the notice at this sign-in
     * (s.5(2)(a)); processing goes on, as s.5(2)(b) allows. Every later sign-in changes nothing.
     *
     * @return the version of the notice published now, which the sign-in response links to
     */
    public String recordNotice(String email, LocalDateTime noticedAtUtc) {
        String version = privacy.notice().version();
        if (consents.findByEmail(email).isEmpty()) {
            PrivacyConsent notice = new PrivacyConsent();
            notice.setEmail(email);
            notice.setNoticeVersion(version);
            notice.setNoticedAt(noticedAtUtc);
            saveOnce(notice);
        }
        return version;
    }

    /* Two first sign-ins at once both find no record; the unique email index lets one in, and the other's is the same fact. */
    private void saveOnce(PrivacyConsent notice) {
        try {
            consents.save(notice);
        } catch (DuplicateKeyException alreadyRecorded) {
            log.info("The notice to an earlier account was recorded by a concurrent sign-in");
        }
    }
}
