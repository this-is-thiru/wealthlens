package com.thiru.wealthlens.shared.privacy;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erases each log of processing and each privacy record once {@code app.privacy.log-retention} has passed since its last
 * processing (Digital Personal Data Protection Rules, 2025 r.6(1)(e), r.8(3); W-237e C2a, C4a; ported to PostgreSQL in
 * W-337c2, D-943). Which date is the last processing is a fact of each record: a grievance's answer (one not yet answered
 * is still being processed, so it is kept), the latest event of an intimation, the completion of an account's erasure. A
 * breach notice stays: it is the owner's record to the Board. Each erasure is a delete by date, so a second run changes
 * nothing.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class LogRetention {

    private final ProcessingRecordRepository processingRecords;
    private final GrievanceRepository grievances;
    private final BreachIntimationRepository intimations;
    private final AccountErasureRequestRepository erasureRequests;
    private final PrivacySettings privacy;

    /** The newest moment whose processing is now past its year, at {@code nowUtc}. */
    public LocalDateTime cutoff(LocalDateTime nowUtc) {
        return nowUtc.minus(privacy.logRetention());
    }

    @Transactional("postgresTransactionManager")
    public void eraseExpired(LocalDateTime nowUtc) {
        LocalDateTime cutoff = cutoff(nowUtc);
        int records = processingRecords.deleteRecordedBy(cutoff);
        int erasedGrievances = grievances.deleteAnsweredBy(cutoff);
        int erasedIntimations = intimations.deleteProcessedBy(cutoff);
        int requests = erasureRequests.deleteErasedBy(cutoff);
        log.info("Erased {} processing records, {} grievances, {} intimations, {} erasure requests processed by {}", records,
                erasedGrievances, erasedIntimations, requests, cutoff);
    }
}
