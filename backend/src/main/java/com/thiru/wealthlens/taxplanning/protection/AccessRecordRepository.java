package com.thiru.wealthlens.taxplanning.protection;

import java.util.Collection;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AccessRecordRepository extends MongoRepository<AccessRecord, String> {

    /** Deletes the records of reads of these subjects: done only when the subjects themselves are erased (W-235 C4a). */
    void deleteBySubjectIdIn(Collection<String> subjectIds);
}
