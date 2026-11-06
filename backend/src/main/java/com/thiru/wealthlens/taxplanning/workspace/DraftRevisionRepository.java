package com.thiru.wealthlens.taxplanning.workspace;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DraftRevisionRepository extends MongoRepository<DraftRevision, String> {

    List<DraftRevision> findByDraftIdOrderByRevisionNumber(String draftId);

    void deleteByDraftId(String draftId);
}
