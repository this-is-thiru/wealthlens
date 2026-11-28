package com.thiru.wealthlens.portfolio.repository;

import com.thiru.wealthlens.portfolio.entity.ProfitAndLossEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ProfitAndLossRepository extends JpaRepository<ProfitAndLossEntity, String> {
    Optional<ProfitAndLossEntity> findByEmailAndFinancialYear(String email, String financialYear);

    List<ProfitAndLossEntity> findAllByEmail(String email);

    @Transactional
    void deleteByEmail(String email);
}
