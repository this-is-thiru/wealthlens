package com.thiru.wealthlens.shared.privacy;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BreachNoticeRepository extends JpaRepository<BreachNotice, String> {

    @Query("SELECT n FROM BreachNotice n WHERE :email MEMBER OF n.affected")
    List<BreachNotice> findAffecting(@Param("email") String email);
}
