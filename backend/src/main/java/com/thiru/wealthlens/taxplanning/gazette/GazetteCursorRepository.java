package com.thiru.wealthlens.taxplanning.gazette;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface GazetteCursorRepository extends MongoRepository<GazetteCursor, String> {
}
