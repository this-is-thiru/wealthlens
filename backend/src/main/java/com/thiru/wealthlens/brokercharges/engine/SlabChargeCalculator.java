package com.thiru.wealthlens.brokercharges.engine;

import static com.thiru.wealthlens.brokercharges.engine.ChargeAmounts.amount;
import static com.thiru.wealthlens.brokercharges.engine.ChargeAmounts.requireNonNegative;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import com.thiru.wealthlens.brokercharges.entity.ChargeSlab;
import java.math.BigDecimal;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * A tiered charge: one quantity selects the band, and the band's rate applies to another.
 *
 * <p>Those are two different questions and conflating them is the trap. An exit load banded by
 * holding period charges a percentage <em>of the redemption</em>, not of the number of days — so the
 * band is chosen by {@code slabBandBasis} and the rate is applied to {@code amountBasis}.
 *
 * <p>Bands are half-open: the lower bound is inclusive, the upper exclusive. A card whose bands do
 * not cover a trade charges nothing rather than falling back to the nearest one, because the nearest
 * band is a guess and the gap belongs to the validator.
 */
@Log4j2
@Component
public class SlabChargeCalculator implements ChargeCalculator {

    private static final int PERCENT = 2;

    @Override
    public ChargeBasis basis() {
        return ChargeBasis.SLAB;
    }

    @Override
    public BigDecimal compute(ChargeRule rule, ChargeContext context, ChargeAccumulator accumulator) {
        List<ChargeSlab> slabs = rule.getSlabs();
        if (slabs == null || slabs.isEmpty()) {
            log.warn("Slab charge rule {} declares no bands; charging nothing", rule.getCode());
            return BigDecimal.ZERO;
        }

        double bandValue = bandValue(rule, context);
        ChargeSlab band = slabs.stream()
                .filter(slab -> covers(slab, bandValue))
                .findFirst()
                .orElse(null);

        if (band == null) {
            log.warn("Slab charge rule {} has no band covering {} on {}; charging nothing",
                    rule.getCode(), bandValue, rule.effectiveSlabBandBasis());
            return BigDecimal.ZERO;
        }

        if (band.getFlatAmount() != null) {
            return requireNonNegative(rule, "band flat amount", amount(band.getFlatAmount()));
        }

        BigDecimal rate = requireNonNegative(rule, "band rate", amount(band.getRate()));
        BigDecimal base = BigDecimal.valueOf(context.amount(rule.effectiveAmountBasis()));
        return rate.multiply(base).movePointLeft(PERCENT);
    }

    /** Lower bound inclusive, upper exclusive; an absent bound is unbounded on that side. */
    private static boolean covers(ChargeSlab slab, double value) {
        boolean aboveFloor = slab.getFromValue() == null || value >= slab.getFromValue();
        boolean belowCeiling = slab.getToValue() == null || value < slab.getToValue();
        return aboveFloor && belowCeiling;
    }

    private static double bandValue(ChargeRule rule, ChargeContext context) {
        return switch (rule.effectiveSlabBandBasis()) {
            case TURNOVER -> context.amount(com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis.TURNOVER);
            case QUANTITY -> context.quantity();
            case HOLDING_DAYS -> holdingDays(context);
        };
    }

    /** Published per lot by the engine, so a tapering charge is banded on the lot it applies to. */
    private static double holdingDays(ChargeContext context) {
        Object value = context.attributes() == null ? null : context.attributes().get("holdingDays");
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }
}
