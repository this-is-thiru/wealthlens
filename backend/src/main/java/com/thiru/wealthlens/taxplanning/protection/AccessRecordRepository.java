package com.thiru.wealthlens.taxplanning.protection;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AccessRecordRepository extends MongoRepository<AccessRecord, String> {
}
