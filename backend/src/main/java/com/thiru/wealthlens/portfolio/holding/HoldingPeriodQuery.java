package com.thiru.wealthlens.portfolio.holding;

import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import java.time.LocalDate;

/**
 * One disposal to classify.
 *
 * @param assetType     what was sold
 * @param subClass      the second dimension, where the asset type alone does not decide the rule —
 *                      {@code EQUITY_ORIENTED} / {@code SPECIFIED} / {@code OTHER} for a mutual
 *                      fund, {@code LISTED} / {@code UNLISTED} for a bond. Null when unknown, which
 *                      is a real state rather than a default: it is what the instrument profile
 *                      would have told us and usually has not
 * @param acquisitionDate when the lot being disposed of was acquired
 * @param transferDate  when it was sold
 */
public record HoldingPeriodQuery(AssetType assetType, String subClass,
                                 LocalDate acquisitionDate, LocalDate transferDate) {

    LocalDate dateFor(HoldingPeriodDate keyedOn) {
        return keyedOn == HoldingPeriodDate.ACQUISITION ? acquisitionDate : transferDate;
    }
}
