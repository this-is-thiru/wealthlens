package com.thiru.wealthlens.portfolio.repository;

import com.thiru.wealthlens.portfolio.entity.ProfitAndLossEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProfitAndLossRepository extends MongoRepository<ProfitAndLossEntity, String> {
    Optional<ProfitAndLossEntity> findByEmailAndFinancialYear(String email, String financialYear);

    List<ProfitAndLossEntity> findAllByEmail(String email);

    void deleteByEmail(String email);
}
