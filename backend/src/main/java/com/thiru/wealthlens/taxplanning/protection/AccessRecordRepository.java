package com.thiru.wealthlens.taxplanning.protection;

import java.time.Instant;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface AccessRecordRepository extends JpaRepository<AccessRecord, String> {

    /** Deletes the records of reads of these subjects: done only when the subjects themselves are erased (W-235 C4a). */
    void deleteBySubjectIdIn(Collection<String> subjectIds);

    /** Deletes the records of the reads this person made, with their account (W-237d). */
    @Transactional
    void deleteByPrincipal(String principal);

    /** Deletes the records of reads made on or before {@code cutoff}, a year after them (W-237f); how many. */
    @Transactional
    long deleteByReadAtLessThanEqual(Instant cutoff);
}
