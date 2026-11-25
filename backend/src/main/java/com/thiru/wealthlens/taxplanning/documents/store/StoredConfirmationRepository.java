package com.thiru.wealthlens.taxplanning.documents.store;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredConfirmationRepository extends JpaRepository<StoredConfirmation, String> {
}
