package com.thiru.wealthlens.shared.privacy;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erases a person's grievances and breach intimations, and takes their email out of each breach notice (W-237d C7a;
 * ported to PostgreSQL in W-337c2, D-943). The notice itself stays: it is the owner's record to the Board about the
 * breach, not about the person. The person's own requests in the processing log go too; requests others made about
 * them stay until their year has passed (r.8(3); W-237e C3a).
 */
@Component
@RequiredArgsConstructor
public class PrivacyRecordErasure implements PersonalDataHolder {

    private final GrievanceRepository grievances;
    private final BreachIntimationRepository intimations;
    private final ProcessingRecordRepository processingRecords;
    private final PrivacyConsentRepository consents;
    private final BreachNoticeRepository notices;

    @Override
    public String noticePart() {
        return "privacy-records";
    }

    @Override
    @Transactional
    public void erase(String email) {
        grievances.deleteByEmail(email);
        intimations.deleteByEmail(email);
        processingRecords.deleteByPrincipal(email);
        consents.deleteByEmail(email);
        List<BreachNotice> affecting = notices.findAffecting(email);
        affecting.forEach(notice -> notice.getAffected().removeIf(email::equals));
        notices.saveAll(affecting);
    }
}
