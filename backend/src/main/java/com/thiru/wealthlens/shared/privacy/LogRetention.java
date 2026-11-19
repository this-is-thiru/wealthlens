package com.thiru.wealthlens.shared.privacy;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

/**
 * Erases each log of processing and each privacy record once {@code app.privacy.log-retention} has passed since its last
 * processing (Digital Personal Data Protection Rules, 2025 r.6(1)(e), r.8(3); W-237e C2a, C4a). Which date is the last
 * processing is a fact of each record: a grievance's answer (one not yet answered is still being processed, so it is kept), the
 * latest event of an intimation, the completion of an account's erasure. A breach notice stays: it is the owner's record to the
 * Board. Each erasure is a delete by date, so a second run changes nothing.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class LogRetention {

    private final MongoTemplate mongoTemplate;
    private final PrivacySettings privacy;

    /** The newest moment whose processing is now past its year, at {@code nowUtc}. */
    public LocalDateTime cutoff(LocalDateTime nowUtc) {
        return nowUtc.minus(privacy.logRetention());
    }

    public void eraseExpired(LocalDateTime nowUtc) {
        LocalDateTime cutoff = cutoff(nowUtc);
        long records = mongoTemplate.remove(query(where("recorded_at").lte(cutoff)), ProcessingRecord.class).getDeletedCount();
        long grievances = mongoTemplate.remove(query(where("answered_at").lte(cutoff)), Grievance.class).getDeletedCount();
        long intimations = mongoTemplate.remove(query(new Criteria().andOperator(where("posted_at").lte(cutoff),
                notAfter("read_at", cutoff), notAfter("emailed_at", cutoff))), BreachIntimation.class).getDeletedCount();
        long requests = mongoTemplate.remove(query(where("erased_at").lte(cutoff)), AccountErasureRequest.class)
                .getDeletedCount();
        log.info("Erased {} processing records, {} grievances, {} intimations, {} erasure requests processed by {}", records,
                grievances, intimations, requests, cutoff);
    }

    private static Criteria notAfter(String field, LocalDateTime cutoff) {
        return new Criteria().orOperator(where(field).is(null), where(field).lte(cutoff));
    }
}
