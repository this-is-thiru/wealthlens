package com.thiru.wealthlens.shared.privacy;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GrievanceRepository extends JpaRepository<Grievance, String> {

    List<Grievance> findByEmailOrderByReceivedAtDesc(String email);

    List<Grievance> findByAnsweredAtIsNullOrderByRespondByAsc();

    void deleteByEmail(String email);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Grievance g WHERE g.answeredAt <= :cutoff")
    int deleteAnsweredBy(@Param("cutoff") LocalDateTime cutoff);
}
