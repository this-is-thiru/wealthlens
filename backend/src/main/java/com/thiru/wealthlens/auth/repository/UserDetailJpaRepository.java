package com.thiru.wealthlens.auth.repository;

import com.thiru.wealthlens.auth.entity.UserDetailJpa;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** {@link UserDetailsRepository}'s PostgreSQL counterpart, proving the mapping (W-337a, D-937). */
public interface UserDetailJpaRepository extends JpaRepository<UserDetailJpa, String> {

    Optional<UserDetailJpa> findByEmail(String email);

    void deleteByEmail(String email);
}
