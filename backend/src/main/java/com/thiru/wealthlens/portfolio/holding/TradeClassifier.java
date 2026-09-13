package com.thiru.wealthlens.portfolio.holding;

import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * The one place a disposal becomes a tax classification.
 *
 * <p>Exists because the answer composes two independent inputs — the trade's segment and its
 * holding period — and duplicating that composition is how four disagreeing copies of a hardcoded
 * 365-day rule came to exist in the first place.
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class TradeClassifier {

    private final HoldingPeriodService holdingPeriodService;

    public TradeClassification classify(TradeClassificationQuery query) {
        TradeClassification bySegment = classifyBySegment(query);
        if (bySegment != null) {
            return bySegment;
        }

        String subClass = subClassFor(query.stockCode(), query.assetType());
        HoldingPeriodResolution resolution = holdingPeriodService.classify(
                query.assetType(), subClass, query.acquisitionDate(), query.transferDate());
        return new TradeClassification(resolution.capitalGainsType(), subClass, resolution.reason());
    }

    /**
     * Segment decides before holding period does, and overrides it.
     *
     * <p>Intraday equity is speculative business income taxed at slab rate (D1) — how long it was
     * held is irrelevant, because it was not held. Exchange-traded derivatives are business income
     * too but expressly <em>not</em> speculative, which is a third head this model has no bucket
     * for; they are recorded {@code UNCLASSIFIED} with a reason that says which kind of unknown it
     * is, rather than being counted as capital gains.
     *
     * @return the classification when segment settles it, or null to fall through to holding period
     */
    private TradeClassification classifyBySegment(TradeClassificationQuery query) {
        return switch (query.segment()) {
            case INTRADAY -> query.assetType() == AssetType.EQUITY
                    ? new TradeClassification(CapitalGainsType.SPECULATIVE, null,
                            "Intraday equity is speculative business income, taxed at slab rate")
                    : null;
            case FUTURES, OPTIONS -> new TradeClassification(CapitalGainsType.UNCLASSIFIED, null,
                    "Exchange-traded derivatives are non-speculative business income, which has no"
                            + " bucket in this model — recorded rather than counted as capital gains");
            case DELIVERY, NA -> null;
        };
    }

    /**
     * The instrument sub-class, which decides a mutual fund's rule and a bond's.
     *
     * <p><b>Decision D7: this is the single seam, and it is deliberately empty today.</b> The source
     * is the instrument registry in the priced-portfolio epic, which owns instrument identity under
     * ADR-29. {@code ChargeInstrumentEntity} is not the source and must not become one: it is keyed
     * on a code real holdings do not match, its absence already means "no exit load" rather than
     * "category unknown", and its {@code FundCategory} cannot express the ">65% debt and
     * money-market" test that separates a specified fund from an ordinary one.
     */
    private String subClassFor(String stockCode, AssetType assetType) {
        return null;
    }
}
