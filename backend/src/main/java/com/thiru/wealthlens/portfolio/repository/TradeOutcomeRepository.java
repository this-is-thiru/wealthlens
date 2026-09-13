package com.thiru.wealthlens.portfolio.repository;

import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TradeOutcomeRepository extends MongoRepository<TradeOutcomeEntity, String> {

    List<TradeOutcomeEntity> findByEmail(String email);

    void deleteByEmail(String email);
}
