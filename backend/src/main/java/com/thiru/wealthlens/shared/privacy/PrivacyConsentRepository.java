package com.thiru.wealthlens.shared.privacy;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivacyConsentRepository extends JpaRepository<PrivacyConsent, String> {

    Optional<PrivacyConsent> findByEmail(String email);

    void deleteByEmail(String email);
}
