package com.thiru.wealthlens.shared.privacy;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface BreachNoticeRepository extends MongoRepository<BreachNotice, String> {
}
