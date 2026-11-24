package com.thiru.wealthlens.shared.privacy;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessingRecordRepository extends JpaRepository<ProcessingRecord, String> {

    List<ProcessingRecord> findByPrincipal(String principal);

    void deleteByPrincipal(String principal);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ProcessingRecord r WHERE r.recordedAt <= :cutoff")
    int deleteRecordedBy(@Param("cutoff") LocalDateTime cutoff);
}
