package com.thiru.wealthlens.shared.privacy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountErasureRequestRepository extends JpaRepository<AccountErasureRequest, String> {

    boolean existsByEmailAndErasedAtIsNull(String email);

    Optional<AccountErasureRequest> findFirstByEmailOrderByRequestedAtDesc(String email);

    Optional<AccountErasureRequest> findByEmailAndErasedAtIsNull(String email);

    List<AccountErasureRequest> findByErasedAtIsNullAndRequestedAtLessThanEqual(LocalDateTime requestedBy);

    List<AccountErasureRequest> findByErasedAtIsNullAndCause(ErasureCause cause);

    List<AccountErasureRequest> findByErasedAtGreaterThanEqual(LocalDateTime erasedFrom);

    List<AccountErasureRequest> findByErasedAtIsNullAndRequestedAtGreaterThanEqual(LocalDateTime requestedFrom);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM AccountErasureRequest r WHERE r.erasedAt <= :cutoff")
    int deleteErasedBy(@Param("cutoff") LocalDateTime cutoff);
}
