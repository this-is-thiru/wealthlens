package com.thiru.wealthlens.portfolio.dto;

import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
import com.thiru.wealthlens.shared.util.money.TMoney;
import java.util.Optional;

/**
 * One year's realised gain for one classification of one kind of asset, as {@code portfolio} publishes it
 * ({@code 13-edge-cases.md} R-10; D-237).
 *
 * <p>Gross, and never a tax: {@code portfolio} owns which disposals happened and what they made, the tax side owns
 * the rate. So the bucket carries what the rate depends on — the asset and its sub-class, since listed equity and
 * equity-oriented funds are charged apart from everything else — and no rate basis of its own. Every classification
 * is published, {@code UNCLASSIFIED} and {@code SPECULATIVE} included, so the tax side can block on the one and route
 * the other ({@code 07-architecture.md} X1.3) rather than never seeing them.
 *
 * @param classification how the disposals were classified
 * @param assetType what was disposed of
 * @param instrumentSubClass the scheme's or bond's sub-class, where the instrument registry has given one
 * @param gainRupees the gross gain, a loss negative, canonical to paise
 */
public record CapitalGainsBucket(
        CapitalGainsType classification, AssetType assetType, Optional<String> instrumentSubClass, double gainRupees) {

    public CapitalGainsBucket {
        if (classification == null || assetType == null || instrumentSubClass == null) {
            throw new IllegalArgumentException("A capital-gains bucket needs its classification and asset type; pass"
                    + " Optional.empty() for an instrument without a sub-class");
        }
        gainRupees = TMoney.scale(gainRupees);
    }
}
