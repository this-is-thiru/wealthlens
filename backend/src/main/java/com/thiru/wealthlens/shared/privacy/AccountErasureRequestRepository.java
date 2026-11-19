package com.thiru.wealthlens.shared.privacy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AccountErasureRequestRepository extends MongoRepository<AccountErasureRequest, String> {

    boolean existsByEmailAndErasedAtIsNull(String email);

    Optional<AccountErasureRequest> findFirstByEmailOrderByRequestedAtDesc(String email);

    Optional<AccountErasureRequest> findByEmailAndErasedAtIsNull(String email);

    List<AccountErasureRequest> findByErasedAtIsNullAndRequestedAtLessThanEqual(LocalDateTime requestedBy);

    List<AccountErasureRequest> findByErasedAtIsNullAndCause(ErasureCause cause);

    List<AccountErasureRequest> findByErasedAtGreaterThanEqual(LocalDateTime erasedFrom);

    List<AccountErasureRequest> findByErasedAtIsNullAndRequestedAtGreaterThanEqual(LocalDateTime requestedFrom);
}
