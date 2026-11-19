package com.thiru.wealthlens.taxplanning.documents.store;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface StoredConfirmationRepository extends MongoRepository<StoredConfirmation, String> {
}
