package com.thiru.wealthlens.brokercharges.engine;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import java.util.Optional;

/**
 * Finds the rate card that priced a trade.
 *
 * <p>A contract only, in this chunk. The specificity ranking, the validity window and the cache are
 * Chunk 5; the engine is written against the interface so the two can be built and tested apart.
 */
public interface ChargeScheduleResolver {

    /**
     * The card in force for this context, or empty when none is on file for the date — which the
     * engine records rather than treating as a zero charge.
     */
    Optional<ChargeScheduleEntity> resolve(ChargeContext context);
}
