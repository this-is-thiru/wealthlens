package com.thiru.wealthlens.shared.privacy;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BreachIntimationRepository extends MongoRepository<BreachIntimation, String> {

    List<BreachIntimation> findByNoticeIdOrderByEmailAsc(String noticeId);

    List<BreachIntimation> findByEmailOrderByPostedAtDesc(String email);
}
