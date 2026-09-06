package com.thiru.wealthlens.brokercharges.engine;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import java.math.BigDecimal;

/**
 * Computes the raw amount for one basis of charge arithmetic.
 *
 * <p>This is the second axis of extensibility. A new <em>charge</em> is a rule document and needs
 * no Java; a new <em>kind of arithmetic</em> is one implementation of this interface, registered by
 * its basis, that no existing class knows about.
 *
 * <p>Implementations return the raw amount only. Floors, caps, aggregation between a rate and a
 * flat fee, and rounding are applied uniformly by the engine afterwards — an implementation that
 * rounded would compound the error against a real contract note.
 */
public interface ChargeCalculator {

    /** The basis this implementation serves. Exactly one implementation per constant. */
    ChargeBasis basis();

    /**
     * @param accumulator the lines applied so far, which a derived or formula-based charge reads
     * @return the raw amount, unrounded and before any modifier
     */
    BigDecimal compute(ChargeRule rule, ChargeContext context, ChargeAccumulator accumulator);
}
