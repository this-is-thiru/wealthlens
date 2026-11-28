package com.thiru.wealthlens.portfolio.repository;

import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface TradeOutcomeRepository extends JpaRepository<TradeOutcomeEntity, String> {

    List<TradeOutcomeEntity> findByEmail(String email);

    /**
     * One user's rows sold strictly between the two dates — both ends excluded, so a caller wanting a closed range
     * passes the days either side. Served by {@code trade_outcome_user_idx}.
     *
     * <p>Spring Data JPA's derived {@code Between} keyword maps to SQL's inclusive {@code BETWEEN}, while this
     * method excludes both ends — so this stays an explicit
     * JPQL query rather than a derived one (W-337e, D-949).
     */
    @Query("SELECT t FROM TradeOutcomeEntity t WHERE t.email = :email AND t.sellDate > :after AND t.sellDate < :before")
    List<TradeOutcomeEntity> findByEmailAndSellDateBetween(@Param("email") String email, @Param("after") LocalDate after,
            @Param("before") LocalDate before);

    /**
     * Every realised row that consumed one of these buy lots, in one round trip.
     *
     * <p>The sell-side charges a lot has attracted are already allocated across lots here, so a
     * per-holding charge view reads them rather than re-deriving a split. Served by
     * {@code trade_outcome_buy_lot_idx}.
     */
    List<TradeOutcomeEntity> findByEmailAndSourceBuyLotIdIn(String email, Collection<String> sourceBuyLotIds);

    @Transactional
    void deleteByEmail(String email);

    long countByEmail(String email);
}
