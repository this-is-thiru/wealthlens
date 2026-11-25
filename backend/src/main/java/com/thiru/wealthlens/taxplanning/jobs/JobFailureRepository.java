package com.thiru.wealthlens.taxplanning.jobs;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobFailureRepository extends JpaRepository<JobFailure, String> {
}
