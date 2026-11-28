package com.thiru.wealthlens.corporate.repository;

import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.LastlyPerformedCorporateAction;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface LastlyPerformedCorporateActionRepo extends JpaRepository<LastlyPerformedCorporateAction, String> {

    @Query("SELECT l FROM LastlyPerformedCorporateAction l WHERE l.email = :email AND l.stockCode = :stockCode"
            + " AND l.assetType = :assetType AND l.actionType = :actionType AND l.brokerName = :brokerName")
    Optional<LastlyPerformedCorporateAction> findLastPerformedCA(
            @Param("email") String email, @Param("stockCode") String stockCode,
            @Param("assetType") AssetType assetType, @Param("actionType") CorporateActionType actionType,
            @Param("brokerName") BrokerName brokerName);

    @Transactional
    void deleteByEmail(String email);
}
