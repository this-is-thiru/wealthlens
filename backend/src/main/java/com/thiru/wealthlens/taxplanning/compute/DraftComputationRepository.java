package com.thiru.wealthlens.taxplanning.compute;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DraftComputationRepository extends MongoRepository<DraftComputation, String> {

    Optional<DraftComputation> findByReceiptAndEmail(String receipt, String email);

    void deleteByDraftId(String draftId);

    List<DraftComputation> findByDraftId(String draftId);

    /** The computations of any of {@code draftIds}: a profile's, listed before erasure (W-123b). */
    Page<DraftComputation> findByDraftIdIn(Collection<String> draftIds, Pageable pageable);
}
