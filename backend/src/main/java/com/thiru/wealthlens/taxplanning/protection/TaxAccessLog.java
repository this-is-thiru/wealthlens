package com.thiru.wealthlens.taxplanning.protection;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records each read of special-category data ({@code DR 14} O1.1, O4, D-447 C3a), inside the read's own transaction. */
@Service
@Transactional
@RequiredArgsConstructor
public class TaxAccessLog {

    private final AccessRecordRepository repository;
    private final Clock clock;

    public void record(String principal, ReadSubject subject, String subjectId) {
        AccessRecord read = new AccessRecord();
        read.setId(UUID.randomUUID().toString());
        read.setPrincipal(principal);
        read.setSubject(subject);
        read.setSubjectId(subjectId);
        read.setReadAt(Instant.now(clock));
        repository.insert(read);
    }
}
