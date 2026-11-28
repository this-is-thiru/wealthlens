package com.thiru.wealthlens.taxplanning.compute;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface DraftComputationRepository extends JpaRepository<DraftComputation, String> {

    Optional<DraftComputation> findByReceiptAndEmail(String receipt, String email);

    void deleteByDraftId(String draftId);

    /** Deletes this person's computations, including any left without its draft, with their account (W-237d). */
    @Transactional
    void deleteByEmail(String email);

    List<DraftComputation> findByDraftId(String draftId);

    /** The computations of any of {@code draftIds}: a profile's, listed before erasure (W-123b). */
    Page<DraftComputation> findByDraftIdIn(Collection<String> draftIds, Pageable pageable);
}
