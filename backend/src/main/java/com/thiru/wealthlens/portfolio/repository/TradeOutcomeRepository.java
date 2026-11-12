package com.thiru.wealthlens.portfolio.repository;

import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TradeOutcomeRepository extends MongoRepository<TradeOutcomeEntity, String> {

    List<TradeOutcomeEntity> findByEmail(String email);

    /**
     * One user's rows sold strictly between the two dates — both ends excluded, so a caller wanting a closed range
     * passes the days either side. Served by {@code trade_outcome_user_idx}.
     */
    List<TradeOutcomeEntity> findByEmailAndSellDateBetween(String email, LocalDate after, LocalDate before);

    /**
     * Every realised row that consumed one of these buy lots, in one round trip.
     *
     * <p>The sell-side charges a lot has attracted are already allocated across lots here, so a
     * per-holding charge view reads them rather than re-deriving a split. Served by
     * {@code trade_outcome_buy_lot_idx}.
     */
    List<TradeOutcomeEntity> findByEmailAndSourceBuyLotIdIn(String email, Collection<String> sourceBuyLotIds);

    void deleteByEmail(String email);

    long countByEmail(String email);

    /**
     * A user's rows that carry {@code sell_charge_breakup}: the V2 recorder writes it, V1 and the rebuild do not
     * ({@code ProfitAndLossV2Backfill}). The entity defaults the map to empty, so only the stored field tells (W-283a2).
     */
    List<TradeOutcomeEntity> findByEmailAndSellChargeBreakupExists(String email, boolean exists);
}
