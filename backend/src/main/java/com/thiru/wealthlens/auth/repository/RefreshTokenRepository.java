package com.thiru.wealthlens.auth.repository;

import com.thiru.wealthlens.auth.entity.RefreshToken;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RefreshTokenRepository extends MongoRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByEmail(String email);
}
