package com.thiru.wealthlens.shared.privacy;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface GrievanceRepository extends MongoRepository<Grievance, String> {

    List<Grievance> findByEmailOrderByReceivedAtDesc(String email);

    List<Grievance> findByAnsweredAtIsNullOrderByRespondByAsc();
}
