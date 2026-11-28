package com.thiru.wealthlens.taxplanning.profile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ProfileErasureRepository extends JpaRepository<ProfileErasure, String> {

    Optional<ProfileErasure> findByProfileIdAndErasedAtIsNull(String profileId);

    List<ProfileErasure> findByErasedAtGreaterThanEqual(LocalDateTime erasedFrom);

    List<ProfileErasure> findByErasedAtIsNullAndRequestedAtGreaterThanEqual(LocalDateTime requestedFrom);

    /** Deletes the records of the profile erasures this person asked for, with their account (W-237d). */
    @Transactional
    void deleteByEmail(String email);

    /** Deletes the records of profile erasures done on or before {@code cutoff}, a year after them (W-237f); how many. */
    @Transactional
    long deleteByErasedAtLessThanEqual(LocalDateTime cutoff);
}
