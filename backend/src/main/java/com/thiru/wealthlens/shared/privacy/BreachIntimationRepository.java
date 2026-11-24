package com.thiru.wealthlens.shared.privacy;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BreachIntimationRepository extends JpaRepository<BreachIntimation, String> {

    List<BreachIntimation> findByNoticeIdOrderByEmailAsc(String noticeId);

    List<BreachIntimation> findByEmailOrderByPostedAtDesc(String email);

    void deleteByEmail(String email);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM BreachIntimation b WHERE b.postedAt <= :cutoff"
            + " AND (b.readAt IS NULL OR b.readAt <= :cutoff)"
            + " AND (b.emailedAt IS NULL OR b.emailedAt <= :cutoff)")
    int deleteProcessedBy(@Param("cutoff") LocalDateTime cutoff);
}
