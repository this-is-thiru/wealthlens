package com.thiru.wealthlens.taxplanning.compute;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DraftComputationRepository extends MongoRepository<DraftComputation, String> {

    Optional<DraftComputation> findByReceiptAndEmail(String receipt, String email);

    void deleteByDraftId(String draftId);

    List<DraftComputation> findByDraftId(String draftId);
}
