package com.thiru.wealthlens.portfolio.repository;

import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TradeOutcomeRepository extends MongoRepository<TradeOutcomeEntity, String> {

    List<TradeOutcomeEntity> findByEmail(String email);

    /**
     * Every realised row that consumed one of these buy lots, in one round trip.
     *
     * <p>The sell-side charges a lot has attracted are already allocated across lots here, so a
     * per-holding charge view reads them rather than re-deriving a split. Served by
     * {@code trade_outcome_buy_lot_idx}.
     */
    List<TradeOutcomeEntity> findByEmailAndSourceBuyLotIdIn(String email, Collection<String> sourceBuyLotIds);

    void deleteByEmail(String email);
}
