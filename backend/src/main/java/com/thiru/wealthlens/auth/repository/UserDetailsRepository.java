package com.thiru.wealthlens.auth.repository;

import com.thiru.wealthlens.auth.entity.UserDetail;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserDetailsRepository extends JpaRepository<UserDetail, String> {

    Optional<UserDetail> findByEmail(String email);

    void deleteByEmail(String email);

    List<UserDetail> findByLastSignedInAtLessThanEqual(LocalDateTime cutoffUtc);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserDetail u SET u.lastSignedInAt = :atUtc WHERE u.email = :email")
    int recordSignIn(@Param("email") String email, @Param("atUtc") LocalDateTime atUtc);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserDetail u SET u.password = :hash WHERE u.email = :email")
    int storePassword(@Param("email") String email, @Param("hash") String hash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserDetail u SET u.lastSignedInAt = :nowUtc WHERE u.lastSignedInAt IS NULL")
    int startCounting(@Param("nowUtc") LocalDateTime nowUtc);
}
