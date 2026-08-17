package com.thiru.wealthlens.corporate.repository;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.LastlyPerformedCorporateAction;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface LastlyPerformedCorporateActionRepo extends MongoRepository<LastlyPerformedCorporateAction, String> {
    @Query("{'email': ?0, 'stock_code': ?1 , 'asset_type':  ?2, 'action_type': ?3, 'broker_name': ?4 }")
    Optional<LastlyPerformedCorporateAction> findLastPerformedCA(String email, String stockCode, AssetType assetType, CorporateActionType actionType, BrokerName brokerName);

    void deleteByEmail(String email);
}
