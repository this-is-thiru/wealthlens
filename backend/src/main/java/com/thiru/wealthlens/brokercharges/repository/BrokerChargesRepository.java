package com.thiru.wealthlens.brokercharges.repository;

import com.thiru.wealthlens.brokercharges.entity.BrokerCharges;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

/**
 * <b>Superseded.</b> Storage for the superseded rate-card template.
 *
 * <p>Replaced by {@code ChargeScheduleRepository}.
 *
 * <p><b>Do not delete yet.</b> Still reached by live code, and removal waits on human testing of
 * the charges engine — see {@code docs/charges-engine/implementation-checklist.md}, Chunk 11.
 *
 * <p>Plain {@code @Deprecated} rather than {@code forRemoval = true} on purpose. A removal warning
 * is <em>not</em> suppressed at a deprecated use site, so seventeen interlinked classes would warn
 * about each other and bury the only signal worth having. An ordinary deprecation warning is
 * suppressed inside deprecated code, which leaves the build reporting exactly the <b>live</b>
 * callers still to be migrated — and that list reaching zero is the precondition for deleting any
 * of this.
 */
@Deprecated
public interface BrokerChargesRepository extends MongoRepository<BrokerCharges, String> {

    @Query("{'broker_name': ?0, 'status': 'ACTIVE', 'start_date': { $lte: ?1 }, 'end_date': { $gte: ?1 } }")
    Optional<BrokerCharges> findActiveBrokerChargesOnDate(BrokerName brokerName, LocalDate targetDate);

}
