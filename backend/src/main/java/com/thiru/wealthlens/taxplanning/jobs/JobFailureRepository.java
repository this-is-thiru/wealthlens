package com.thiru.wealthlens.taxplanning.jobs;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface JobFailureRepository extends MongoRepository<JobFailure, String> {
}
