package com.thiru.wealthlens.portfolio.holding;

import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * The one place a disposal's holding-period classification is decided.
 *
 * <p>It replaced {@code holdingPeriodDays > 365} in four separate methods, which is three too many
 * for a rule that decides a tax figure — and the four did not even agree: two compared
 * {@code plusYears(1)} and two counted 365 days, which differ across a leap year.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class HoldingPeriodService {

    private final HoldingPeriodResolver resolver;

    /** Full classification, including {@code UNCLASSIFIED} and the policy that decided it. */
    public HoldingPeriodResolution classify(AssetType assetType, String subClass,
                                            LocalDate acquisitionDate, LocalDate transferDate) {
        return resolver.classify(new HoldingPeriodQuery(assetType, subClass, acquisitionDate, transferDate));
    }

    /**
     * The two-way answer the profit-and-loss aggregate needs, because it has exactly two buckets.
     *
     * <p><b>Not a stopgap any more — the deliberate adapter for the legacy pair.</b> Since D8 the
     * aggregate also carries {@code gainsByClassification}, which holds the real answer; this
     * two-way reduction exists solely to keep {@code shortTermCapitalGains} and
     * {@code longTermCapitalGains} behaving exactly as they always have, so no existing reader
     * changes before those fields retire with V1. A caller that wants the truth asks
     * {@link #classify}, or {@code TradeClassifier} when segment matters too.
     *
     * <p>{@code UNCLASSIFIED} has nowhere to go in a two-bucket model, so it is reported as
     * short-term and <b>logged</b>. Short-term is the conservative direction — the higher-taxed of
     * the two — so an unknown lands on the side that does not understate a liability.
     *
     * <p>{@code NOT_CAPITAL_GAINS} is reported the same way for the same reason, and is equally
     * wrong — a fixed deposit's interest does not belong in a capital-gains report at all. It is
     * recorded correctly in the classification map, which is the point of having one.
     */
    public boolean isShortTerm(AssetType assetType, String subClass,
                               LocalDate acquisitionDate, LocalDate transferDate) {
        HoldingPeriodResolution resolution = classify(assetType, subClass, acquisitionDate, transferDate);

        if (resolution.capitalGainsType() == CapitalGainsType.UNCLASSIFIED
                || resolution.capitalGainsType() == CapitalGainsType.NOT_CAPITAL_GAINS) {
            log.warn("Holding period for a {} disposal acquired {} and sold {} resolved as {} — the profit and"
                            + " loss aggregate has only short and long term buckets, so it is counted as"
                            + " short-term. Reason: {}",
                    assetType, acquisitionDate, transferDate, resolution.capitalGainsType(), resolution.reason());
            return true;
        }
        return resolution.capitalGainsType() == CapitalGainsType.SHORT_TERM;
    }
}
