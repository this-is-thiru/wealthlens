package com.thiru.wealthlens.portfolio.entity;

import com.thiru.wealthlens.portfolio.entity.model.RealisedProfits;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;

/**
 * Serializes {@link RealisedProfits} to and from a {@code TEXT} JSON column (W-337e, D-949 C3a) for both
 * {@code ProfitAndLossEntity.realisedProfits} and {@code .outSourcedRealisedProfits}.
 *
 * <p>Every mutation of the tree loads the whole document, walks deep inside it in memory, and saves the whole
 * document back ({@code ProfitAndLossService}); no query anywhere filters inside {@code gainsByAssetAndSegment} or
 * {@code yearlyChargeSummary}.
 */
@Converter
public class RealisedProfitsConverter extends JsonColumnConverter<RealisedProfits> {

    private static final TypeReference<RealisedProfits> TYPE = new TypeReference<>() { };

    @Override
    protected TypeReference<RealisedProfits> type() {
        return TYPE;
    }

    /**
     * {@code null}, not {@link RealisedProfits#empty()}: a column with no stored JSON means this track was never
     * folded into (e.g. a user with no outsourced profits), which {@code ProfitAndLossService} and
     * {@code ProfitAndLossRebuild} already tell apart from a zero {@code RealisedProfits} via {@code TOptional.mapO}.
     */
    @Override
    protected RealisedProfits empty() {
        return null;
    }
}
