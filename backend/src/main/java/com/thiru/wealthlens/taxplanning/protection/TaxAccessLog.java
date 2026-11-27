package com.thiru.wealthlens.taxplanning.protection;

import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Records each read of special-category data ({@code DR 14} O1.1, O4, D-447 C3a), inside the read's own transaction. */
@Service
@Transactional("postgresTransactionManager")
@RequiredArgsConstructor
public class TaxAccessLog {

    private final AccessRecordRepository repository;
    private final Clock clock;

    /**
     * @throws IllegalStateException inside a read-only transaction, where JPA would never flush the record and the read
     *     would go unrecorded without a word
     */
    public void record(String principal, ReadSubject subject, String subjectId) {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("A read of " + subject + " was recorded inside a read-only transaction");
        }
        AccessRecord read = new AccessRecord();
        read.setPrincipal(principal);
        read.setSubject(subject);
        read.setSubjectId(subjectId);
        read.setReadAt(Instant.now(clock));
        repository.save(read);
    }
}
