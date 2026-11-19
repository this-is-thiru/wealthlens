package com.thiru.wealthlens.taxplanning.profile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProfileErasureRepository extends MongoRepository<ProfileErasure, String> {

    Optional<ProfileErasure> findByProfileIdAndErasedAtIsNull(String profileId);

    List<ProfileErasure> findByErasedAtGreaterThanEqual(LocalDateTime erasedFrom);

    List<ProfileErasure> findByErasedAtIsNullAndRequestedAtGreaterThanEqual(LocalDateTime requestedFrom);
}
