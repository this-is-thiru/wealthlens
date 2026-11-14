package com.thiru.wealthlens.shared.privacy;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PrivacyConsentRepository extends MongoRepository<PrivacyConsent, String> {

    Optional<PrivacyConsent> findByEmail(String email);
}
