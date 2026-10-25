package com.thiru.wealthlens.portfolio.holding;

import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The one place a disposal's holding-period classification is decided.
 *
 * <p>It replaced {@code holdingPeriodDays > 365} in four separate methods, which is three too many
 * for a rule that decides a tax figure — and the four did not even agree: two compared
 * {@code plusYears(1)} and two counted 365 days, which differ across a leap year.
 */
@Service
@RequiredArgsConstructor
public class HoldingPeriodService {

    private final HoldingPeriodResolver resolver;

    /** Full classification, including {@code UNCLASSIFIED} and the policy that decided it. */
    public HoldingPeriodResolution classify(AssetType assetType, String subClass,
                                            LocalDate acquisitionDate, LocalDate transferDate) {
        return resolver.classify(new HoldingPeriodQuery(assetType, subClass, acquisitionDate, transferDate));
    }
}
